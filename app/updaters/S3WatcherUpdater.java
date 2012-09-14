package updaters;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import play.Logger;
import play.db.jpa.JPAPlugin;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;

import models.FeedParseStatus;
import models.GtfsFeed;
import models.MetroArea;
import models.ReviewType;

/**
 * Will fetch the feeds named in watchedFiles to the given storer.
 * @author matthewc
 *
 */
public class S3WatcherUpdater implements Updater {
    private String bucket;
    private String accessKey;
    private String secretKey;
    private AmazonS3 s3Client;
    /** These are the S3 paths to watch */ 
    private List<String> watchedFiles;
    
    public void setWatchedFiles (List<String> watchedFiles) {
        this.watchedFiles = watchedFiles; 
    }
    
    public void setAccessKey (String accessKey) {
        this.accessKey = accessKey;
        buildCredentialsIfReady();
    }
    
    public void setSecretKey (String secretKey) {
        this.secretKey = secretKey;
        buildCredentialsIfReady();
    }
    
    public void setBucket (String bucket) {
        this.bucket = bucket;
    }
    
    private void buildCredentialsIfReady() {
        if (accessKey != null && secretKey != null) {
            AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
            s3Client = new AmazonS3Client(credentials);
        }
    }

    public S3WatcherUpdater () {
        this.accessKey = null;
        this.secretKey = null;
    }    
    
    @Override
    public Set<MetroArea> update(FeedStorer storerRaw) {
        Set<MetroArea> changed = new HashSet<MetroArea>();
        if (!(storerRaw instanceof S3FeedStorer) || 
                ((S3FeedStorer) storerRaw).getBucket() != this.bucket) {
            Logger.error("Cannot use S3WatcherUpdater unless also using S3FeedStorer with same bucket");
            return changed;
        }
        
        S3FeedStorer storer = (S3FeedStorer) storerRaw;
        
        for (String fn : this.watchedFiles) {
            JPAPlugin.startTx(false);
            try {
                // TODO what does this do if the file does not exist
                ObjectMetadata meta;
                try {
                    meta = s3Client.getObjectMetadata(this.bucket, fn);
                } catch (Exception e) {
                    Logger.warn("Exception fetching s3:%s/%s ; perhaps it does not exist.", 
                            this.bucket, fn);
                    JPAPlugin.closeTx(true);
                    continue;
                }

                // check if we have a new feed
                String s3Url = buildS3Url(fn);

                boolean newFeed;
                GtfsFeed oldFeed = GtfsFeed.find("downloadUrl = ? AND supersededBy IS NULL ORDER BY dateUpdated DESC",
                        s3Url).first();
                GtfsFeed feed;

                if (oldFeed == null) {
                    feed = new GtfsFeed();
                    newFeed = true;
                }
                else {
                    newFeed = false;
                    feed = oldFeed.clone();
                }
                
                if (!newFeed && oldFeed.dateUpdated.compareTo(meta.getLastModified()) >= 0)
                    continue;

                feed.downloadUrl = s3Url;

                String feedId = storer.copy(fn);
                feed.storedId = feedId;
                
                File gtfs = storer.getFeed(feedId);
                try {
                    FeedStatsCalculator stats = new FeedStatsCalculator(gtfs);
                    stats.applyExtended(feed);
                    feed.status = FeedParseStatus.SUCCESSFUL;
                } catch (Exception e) {
                    Logger.error("Exception calculating feed stats for " + fn);
                    e.printStackTrace();
                    feed.status = FeedParseStatus.FAILED;
                }
                
                
                
                storer.releaseFeed(feedId);
                
                feed.dateUpdated = meta.getLastModified();
                
                if (newFeed) {
                    if (!feed.findAgency())
                        feed.review = ReviewType.NO_AGENCY;
                    feed.dateAdded = meta.getLastModified();
                }
                
                feed.save();
                
                JPAPlugin.closeTx(false);
            } catch (Exception e) {
                JPAPlugin.closeTx(true);
                Logger.error("Exception reading %s", buildS3Url(fn));
                e.printStackTrace();
            }
        }
        
        return changed;
    }
    
    private String buildS3Url (String filename) {
        return "S3Watcher:" + this.bucket + ":" + filename;
    }
}
