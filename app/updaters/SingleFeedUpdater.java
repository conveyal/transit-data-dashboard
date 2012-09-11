package updaters;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import play.Logger;
import play.db.jpa.JPAPlugin;
import play.libs.WS;
import play.libs.WS.HttpResponse;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import models.DefaultBikesAllowedType;
import models.FeedParseStatus;
import models.GtfsFeed;
import models.MetroArea;
import models.NtdAgency;
import models.ReviewType;

/**
 * Update a single feed from a URL, not from data exchange - useful when a feed is, for instance
 * authwalled.
 * @author mattwigway
 */
public class SingleFeedUpdater implements Updater {
    /**
     * @return the downloadUrl
     */
    public String getDownloadUrl() {
        return downloadUrl;
    }

    /**
     * @param downloadUrl the downloadUrl to set
     */
    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    private String downloadUrl;
    
    public Set<MetroArea> update (FeedStorer storer) {
        // find the old feed, if it exists
        Set<MetroArea> changed = new HashSet<MetroArea>();
        
        JPAPlugin.startTx(false);
        try {
            GtfsFeed original = GtfsFeed.find("byDownloadUrl", this.downloadUrl).first();
            GtfsFeed feed;

            boolean downloadFeed;

            if (original != null) {
                feed = original.clone();
                downloadFeed = false;
            }
            else {
                downloadFeed = true;
                feed = new GtfsFeed();
            }

            // determine if it needs to be downloaded
            HttpResponse modifiedRes = WS.url(downloadUrl).head();
            if (!modifiedRes.success()) {
                Logger.error("Error fetching %s", downloadUrl);
                return changed;
            }    

            String modifiedRaw = modifiedRes.getHeader("Last-Modified");
            Date modified;
            if (modifiedRaw == null) {
                Logger.warn(
                        "Server at %s sends no Last-Modified header; feed will always be redownloaded",
                        downloadUrl);
                downloadFeed = true;
                modified = new Date();
            }
            else {
                SimpleDateFormat httpDate = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss z");

                try {
                    modified = httpDate.parse(modifiedRaw);
                } catch (ParseException e) {
                    Logger.error("Malformed Last-Modified header for server at %s, was %s",
                            downloadUrl, modifiedRaw);
                    e.printStackTrace();
                    return changed;
                }

                if (original != null && modified.compareTo(original.dateUpdated) > 0) {
                    downloadFeed = true;
                }
            }

            if (downloadFeed) {
                String feedId = storer.storeFeed(downloadUrl);
                File feedData = storer.getFeed(feedId);
                feed.storedId = feedId;
                FeedStatsCalculator stats;
                
                feed.downloadUrl = this.downloadUrl;
                
                try {
                    stats = new FeedStatsCalculator(feedData);
                    stats.applyExtended(feed);
                } catch (Exception e) {
                    feed.status = FeedParseStatus.FAILED;
                    Logger.warn("Exception calculating feed stats for %s", downloadUrl);
                    e.printStackTrace();
                }

                storer.releaseFeed(feedId);
                feed.dateUpdated = modified;

                if (original == null)
                    feed.dateAdded = modified;

                feed.save();
                
                if (original != null) {
                    original.supersededBy = feed;
                    original.save();
                }

                List<NtdAgency> agencies = feed.getAgencies();

                if (agencies.size() == 0)
                    feed.review = ReviewType.NO_AGENCY;

                feed.save();
                JPAPlugin.closeTx(false);

                for (NtdAgency agency : agencies) {
                    for (MetroArea area : agency.getMetroAreas()) {
                        changed.add(area);
                    }
                }
            }
        } finally {
            // roll it back if not committed
            JPAPlugin.closeTx(true);
        }
        
        return changed;
    }
}
