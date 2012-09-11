package jobs;

import java.io.File;

import models.FeedParseStatus;
import models.GtfsFeed;
import play.db.jpa.NoTransaction;
import play.jobs.Job;
import play.modules.spring.Spring;
import updaters.FeedStatsCalculator;
import updaters.FeedStorer;

public class RecalculateFeedStats extends Job {
    public void doJob () {
        FeedStatsCalculator stats;
        File feedFile;
        FeedStorer storer = Spring.getBeanOfType(FeedStorer.class);
        
        for (GtfsFeed feed : GtfsFeed.<GtfsFeed>findAll()) {
            if (feed.status == FeedParseStatus.FAILED)
                // no reason to reparse a failed feed
                continue;
            
            feedFile = storer.getFeed(feed.storedId); 
            try {
                stats = new FeedStatsCalculator(feedFile);
                stats.apply(feed);
            } catch (Exception e) {}
            
            storer.releaseFeed(feed.storedId);
            
            feed.save();
       }
    }
}
