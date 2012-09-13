/* 
  This program is free software: you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public License
  as published by the Free Software Foundation, either version 3 of
  the License, or (props, at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program. If not, see <http://www.gnu.org/licenses/>. 
*/

package updaters;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.File;

import play.Logger;
import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.db.jpa.NoTransaction;
import play.exceptions.JPAException;
import play.libs.WS;
import play.libs.WS.HttpResponse;
import play.libs.XPath;
import utils.FeedUtils;
import models.FeedParseStatus;
import models.GtfsFeed;
import models.MetroArea;
import models.NtdAgency;
import models.ReviewType;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This class reads the RSS from GTFS Data Exchange and updates the database with that information.
 * It also handles hooks, calling them with the feeds that have changed.
 * @author mattwigway
 */
public class GtfsDataExchangeUpdater implements Updater {	
	public Set<MetroArea> update (FeedStorer storer) {	    
		Set<MetroArea> updated = new HashSet<MetroArea>();
		
		// First, fetch the RSS feed
		HttpResponse res = WS.url("http://www.gtfs-data-exchange.com/api/agencies").get();
		int status = res.getStatus(); 
		if (status != 200) {
			Logger.error("Error fetching GTFS changes from Data Exchange: HTTP status %s", status);
			return null;
		}
		
		JsonObject agencies = res.getJson().getAsJsonObject();
		JsonArray data = agencies.get("data").getAsJsonArray();
		JsonObject feed;
		
		for (JsonElement rawFeed : data) {
		    // this doesn't seem to cause a problem if there is no active transaction
		    JPAPlugin.closeTx(true);
            JPAPlugin.startTx(false);

            feed = rawFeed.getAsJsonObject();

            String dataExchangeId = feed.get("dataexchange_id").getAsString();

            if (dataExchangeId == null || dataExchangeId.equals(""))
                continue;

            // ORDER BY ...: so failed feeds aren't continuously refetched.
            GtfsFeed originalFeed = GtfsFeed.find("dataExchangeId = ? AND supersededBy IS NULL ORDER BY dateUpdated DESC",
                    dataExchangeId).first();

            // convert to ms
            long lastUpdated = ((long) feed.get("date_last_updated").getAsDouble()) * 1000L;

            // do we need to fetch?
            if (originalFeed != null) {
                // difference of less than 2000ms: ignore
                if ((lastUpdated - originalFeed.dateUpdated.getTime()) < 2000) {
                    continue;
                }
            }

            // get the data file URL
            res = WS.url("http://www.gtfs-data-exchange.com/agency/" + 
                    dataExchangeId + "/json").get();
            status = res.getStatus();
            if (status != 200) {
                Logger.error("Error fetching agency %s, status %s", dataExchangeId, status);
                continue;
            }

            JsonObject agency = res.getJson().getAsJsonObject();
            JsonArray files = agency.get("data").getAsJsonObject()
                    .get("datafiles").getAsJsonArray();
            JsonObject firstFile = files.get(0).getAsJsonObject();
            String url = firstFile.get("file_url").getAsString();

            // Download the feed
            String feedId = storer.storeFeed(url);
            if (feedId == null) {
                Logger.error("Could not retrieve feed %s", url);
                // feed will be redownloaded on next attempt
                continue;
            }

            GtfsFeed newFeed;
            boolean isNew;
            // copy over all the data.
            if (originalFeed != null) {
                isNew = false;
                newFeed = originalFeed.clone();
            }
            else {
                newFeed = new GtfsFeed();
                newFeed.note = "new feed";
                isNew = true;
            }

            // update all fields
            FeedUtils.copyFromJson(feed, newFeed);
            newFeed.downloadUrl = url;
            newFeed.storedId = feedId;

            // Calculate feed stats
            File feedFile = storer.getFeed(feedId);

            FeedStatsCalculator stats;
            try {
                stats = new FeedStatsCalculator(feedFile);
            } catch (Exception e) {
                // TODO be more descriptive
                Logger.error("Error calculating feed stats for feed %s", url);
                e.printStackTrace();

                // still save it in the DB
                newFeed.status = FeedParseStatus.FAILED;

                // don't supersede anything
                newFeed.save();
                JPAPlugin.closeTx(false);

                continue;
            }

            storer.releaseFeed(feedId);

            // save the stats
            stats.apply(newFeed);
            newFeed.status = FeedParseStatus.SUCCESSFUL;
            newFeed.save();

            // if it's a new feed, find an agency.
            if (isNew) {
                if (!newFeed.findAgency())
                    newFeed.review = ReviewType.NO_AGENCY;

                newFeed.save();
            }

            for (NtdAgency ntd : newFeed.getAgencies()) {
                for (MetroArea metro : ntd.getMetroAreas()) {
                    updated.add(metro);
                }
            }

            if (originalFeed != null) {
                originalFeed.supersededBy = newFeed;
                originalFeed.save();
            }

            // https://groups.google.com/forum/?fromgroups=#!topic/play-framework/qaIaMOIjpMM
            JPAPlugin.closeTx(false);
		}
		
		return updated;
	}
}
