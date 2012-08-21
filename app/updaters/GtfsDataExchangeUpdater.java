package updaters;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import play.Logger;
import play.libs.WS;
import play.libs.WS.HttpResponse;
import play.libs.XPath;
import models.GtfsFeed;
import models.MetroArea;
import models.NtdAgency;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * This class reads the RSS from GTFS Data Exchange and updates the database with that information.
 * It also handles hooks, calling them with the feeds that have changed.
 * @author mattwigway
 */
public class GtfsDataExchangeUpdater implements Updater {
	public FeedStorer storer;
	
	public Set<MetroArea> update () {
		Set<MetroArea> updated = new HashSet<MetroArea>();
		
		// First, fetch the RSS feed
		HttpResponse res = WS.url("http://www.gtfs-data-exchange.com/feed").get();
		int status = res.getStatus(); 
		if (status != 200) {
			Logger.error("Error fetching GTFS changes from Data Exchange: HTTP status %s", status);
			return null;
		}
		
		DateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		
		Document rss = res.getXml();
		for (Node entry : XPath.selectNodes("//entry", rss)) {
			Node link = XPath.selectNode("link[@rel = 'enclosure']", entry);
			
			// now, parse out the data exchange ID
			// The data exchange ID matches /[a-z\-]/; an underscore indicates the start
			// of the date.
			String dataExchangeId = XPath.selectText("@title", link)
					.split("_")[0];
			
			// FIXME remove
			if (dataExchangeId.equals("mts")) continue;
			if (dataExchangeId.equals("amtrak-sunset-limited-unofficial-feed")) continue;
			if (dataExchangeId.equals("tac-transportation")) continue;
						
			// find the feed
			// make sure we get the latest one so that date comparisons work
			// TODO: This shouldn't depend on how JPA lays out relationships
			GtfsFeed originalFeed = GtfsFeed.find("dataExchangeId = ? and supersededBy_id IS NULL", 
						dataExchangeId)
					.first();
			
			String url = XPath.selectText("@href", link);
			
			String dateUpdatedStr = XPath.selectText("updated", entry);
			// Java does not like the Z on the end
			dateUpdatedStr = dateUpdatedStr.replace("Z", "-0000");
			Date dateUpdated;
			
			try {
				dateUpdated = isoDate.parse(dateUpdatedStr);
			} catch (ParseException e) {
				Logger.error("Unable to parse date %s", dateUpdatedStr);
				continue;
			}
			
			if (originalFeed != null && dateUpdated.equals(originalFeed.dateUpdated)) {
				// We've reached the end of what we need to do
				break;
			}
			
			if (originalFeed != null) {
				for (NtdAgency agency : originalFeed.getAgencies()) {
					updated.add(agency.metroArea);
				}
			}
			
			// Download the feed
			String feedId = storer.storeFeed(url);
			if (feedId == null) {
				Logger.error("Could not retrieve feed %s", url);
				continue;
			}
			
			GtfsFeed newFeed;
			// copy over all the data.
			if (originalFeed != null)
				newFeed = originalFeed.clone();
			else
				newFeed = new GtfsFeed();
			
			// Calculate feed stats
			FeedStatsCalculator stats;
			try {
				stats = new FeedStatsCalculator(storer.getFeed(feedId));
			} catch (Exception e) {
				// TODO be more descriptive
				Logger.error("Error calculating feed stats for feed %s", url);
				e.printStackTrace();
				continue;
			}
			
			newFeed.expirationDate = stats.getEndDate();
			newFeed.startDate = stats.getStartDate();
			newFeed.dateUpdated = dateUpdated;
			newFeed.downloadUrl = url;
			newFeed.storedId = feedId;
			newFeed.save();
			
			if (originalFeed != null) {
				originalFeed.supersededBy = newFeed;
				originalFeed.save();
			}
		}
		
		return updated;
	}
}
