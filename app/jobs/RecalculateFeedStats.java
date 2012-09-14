package jobs;

import java.io.File;

import models.FeedParseStatus;
import models.GtfsFeed;
import play.Logger;
import play.db.jpa.NoTransaction;
import play.jobs.Job;
import play.modules.spring.Spring;
import updaters.FeedStatsCalculator;
import updaters.FeedStorer;
import updaters.UpdaterFactory;

public class RecalculateFeedStats extends Job {
    public void doJob () {
        FeedStatsCalculator stats;
        File feedFile;
        UpdaterFactory updaterFactory = Spring.getBeanOfType(UpdaterFactory.class);
        FeedStorer storer = updaterFactory.getStorer();
        
        for (GtfsFeed feed : GtfsFeed.<GtfsFeed>findAll()) {
            if (feed.status == FeedParseStatus.FAILED)
                // no reason to reparse a failed feed
                continue;
            
            try {
                feedFile = storer.getFeed(feed.storedId);
                stats = new FeedStatsCalculator(feedFile);
                stats.apply(feed);
                storer.releaseFeed(feed.storedId);
            } catch (Exception e) {
                Logger.error("Error calculating feed stats for agency %s, this must be an OBA " +
                		"change or file system error", feed.agencyName);
                e.printStackTrace();
                continue;
            }
            
            feed.save();
        }
    }
}
