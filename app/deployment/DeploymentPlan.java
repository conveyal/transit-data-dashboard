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

package deployment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.jar.JarException;
import java.net.URL;

import com.google.gson.Gson;

import play.Play;
import play.libs.WS;
import play.libs.WS.HttpResponse;
import play.libs.WS.WSRequest;
import play.modules.spring.Spring;

import models.BikeRentalSystem;
import models.BikeRentalSystemType;
import models.DefaultBikesAllowedType;
import models.FeedParseStatus;
import models.GtfsFeed;
import models.MetroArea;
import models.NtdAgency;

/**
 * A deployment plan contains all the information needed to build a graph for a given metro area at a
 * given time.
 * 
 * @author mattwigway
 */

public class DeploymentPlan {
	private Date startDate;
	private Date endDate;
	private MetroArea area;
	private static TimeZone gmt = TimeZone.getTimeZone("GMT");
	private Calendar calendar;
	private FeedDescriptor[] feeds;
	private int window;
	
	/**
	 * Create a plan for the given metro at the current time and for the default window.
	 * @param area
	 */
	public DeploymentPlan(MetroArea area) {
		this(area, Calendar.getInstance(TimeZone.getTimeZone("gmt")).getTime());
	}
	
	/**
	 * Create a plan for the given metro at the given date and for the default window.
	 * @param area
	 * @param date
	 */
	public DeploymentPlan(MetroArea area, Date date) {
		this(area, date,
				Integer.parseInt(Play.configuration.getProperty("dashboard.planwindow", "14")));
	}
	
	/**
	 * Create a new deployment plan.
	 * @param area The Metro Area to create a plan for.
	 * @param date The date this plan should go into effect
	 * @param window The number of days this plan should attempt to find valid trip plans.
	 */
	public DeploymentPlan(MetroArea area, Date date, int window) {
		this.area = area;
		this.window = window;
		this.calendar = Calendar.getInstance(gmt);
		this.startDate = date;
		calendar.setTime(date);
		calendar.add(Calendar.DAY_OF_YEAR, window);
		this.endDate = calendar.getTime();
		
		Set<FeedDescriptor> toInclude = new HashSet<FeedDescriptor>();
		
		// Clear all the scheduled rebuilds of this area; if still applicable, they will be
		// recreated automatically.
		DeploymentPlanScheduler.clearRebuilds(this.area);
		
		for (NtdAgency agency : area.agencies) {
		    if (agency.disabled)
		        continue;
		    
			// all the unsuperseded feeds for this agency
			
			for (GtfsFeed feed : agency.feeds) {
				if (feed.supersededBy != null) {
					continue;
				}
				
				// This is now handled by the same code that handles disabled and failed feeds when
				// they occcur in a supersession sequence
				// handling it here is bad because, if the _last_ feed in a chain of supersession was disabled or
				// failed, the whole chain would get thrown out (needless to say undesirable)
				//if (feed.status == FeedParseStatus.FAILED || feed.disabled) {
				//    continue;
				//}
				
				// We use the feed ID of the latest feed in this chain of supersession to prevent
				// "parallel" feeds from being merged (e.g. to prevent Metro-North from being
				// merged with MTA NYCT or SEPTA Bus from being merged with SEPTA Rail)
				addFeeds(agency.name + "-" + feed.id, feed, toInclude);
			}
		}
		
        this.feeds = new FeedDescriptor[toInclude.size()];
        this.feeds = toInclude.toArray(this.feeds);
	}
	
	/**
	 * Follows the chain of supersession back until it adds enough feeds to cover the window. 
	 * @param feed
	 * @param toInclude
	 */
	private void addFeeds(String agency, GtfsFeed feed, Set<FeedDescriptor> toInclude) {
		addFeeds(agency, feed, toInclude, 0);
	}
	
	/**
	 * Send this to the given URL.
	 * 
	 * @param url
	 * @throws IllegalStateException if the Deployer server returned a non-success status.
	 */
	public void sendTo(String url) throws IllegalStateException {
	    WSRequest req = WS.url(url);
	    req.setParameter("data", this.toJson());
	    HttpResponse res = req.post();
	    
	    if (!res.success()) {
	        throw new IllegalStateException("Deployer returned a status of " + res.getStatus() +
	                " for request to [re]build metro " + this.area.toString() + "(id " + this.area.id + ")");
	    }
	}
	
	private void addFeeds(String agency, GtfsFeed feed, Set<FeedDescriptor> toInclude, int iteration) {
	    /*
	     * I figure there needs to be an explanation of how feeds are supposed to be superseded in
	     * the database, and this seems as good a place for that as any.
	     *  
	     * Feeds that are disabled are just like any other feed in that they take part in normal
	     * supersession of feeds; if the deployment plan generator encounters any such feeds, it
	     * to simply skip them and continue using other feed. Feeds that failed parsing are treated
	     * like disabled feeds; however, they have to be skipped at the top of this function
	     * because they don't have expirationDate &c.
	     * 
	     * It used to be that superseded feeds were left hanging, but this created problems
	     * because they were always considered "current" ad infinitum. Also, if changes to OBA
	     * make a feed parseable, it did not have a place in the chain of supersession under this
	     * strategy. There are still some feeds in the database like this.
	     */
	    
		FeedDescriptor fd;
		GtfsFeed olderFeed;
		GtfsFeed nextSupersession;
		Calendar local = Calendar.getInstance(feed.timezone);
		SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd");
		// expiration dates are in feed-local time, because they are used to shorten feeds, and
		// deployer shouldn't need to know about time zone transformations.
		isoDate.setTimeZone(feed.timezone);
		
		if (feed.status == FeedParseStatus.FAILED) {
		    olderFeed = GtfsFeed.find("supersededBy = ? ORDER BY expirationDate DESC", feed)
		            .first();
		    if (olderFeed != null)
		        addFeeds(agency, olderFeed, toInclude, iteration + 1);
		        return;
		}
		
		if (this.startDate.compareTo(feed.startDate) < 0) {
			// Feed starts after today
			
			if (this.endDate.compareTo(feed.startDate) > 0) {
				// Feed starts before the end of the window, include it
				fd = new FeedDescriptor();
				fd.feedId = feed.storedId;
				fd.setDefaultBikesAllowed(feed.defaultBikesAllowed);
				
				nextSupersession = findNextSupersession(feed);
				
				// force expire before the next one starts, if it's not already
				if (nextSupersession != null && 
						feed.expirationDate.compareTo(nextSupersession.startDate) >= 0) {
					local.setTime(nextSupersession.startDate);
					// Go back 12 hours, this should be yesterday since we have date at 00:00:00,
					// or potentially it is off by one hour due to Daylight Savings Time
					local.add(Calendar.HOUR, -12);
					fd.expireOn = isoDate.format(local.getTime());
				}
				// otherwise, let it expire normally
				else {
					fd.expireOn = isoDate.format(feed.expirationDate);
				}
				
	            if (feed.realtimeUrls != null && feed.realtimeUrls.size() > 0) {
	                fd.realtimeUrls = new String[feed.realtimeUrls.size()];
	                fd.realtimeUrls = feed.realtimeUrls.toArray(fd.realtimeUrls);
	            }   
				
				fd.defaultAgencyId = agency;
				
				// if this feed is already present, there is no reason to continue the search
				if (!feed.disabled)
				    if (!toInclude.add(fd))
				        return;
			}
			else {
			    // the feed starts after the end of the window, so it shouldn't be included, but
			    // the graph needs to be rebuilt on the day it comes into the window.
			    local.setTime(feed.startDate);
			    // - 1 so it will be sure to rebuild
			    local.add(Calendar.DAY_OF_YEAR, -this.window - 1);
			    DeploymentPlanScheduler.scheduleRebuild(feed, local.getTime());
			}
			
			olderFeed = GtfsFeed.find(
			        "supersededBy = ? ORDER BY startDate DESC",
			        feed).first();
			if (olderFeed == null) {
				// Data doesn't go back far enough
				return;
			}
			
			addFeeds(agency, olderFeed, toInclude, iteration + 1);
			return;
		}
		else {
			// Feed does not start after today. Presumably we'll be following feeds
			// through supersession in roughly chronological order, so we don't need
			// to continue searching for older feeds.
		    
		    // if it expires before today also, don't even bother to include it. Don't continue the
		    // search either. TODO: if an older feed accidentally supersedes a newer one, the newer
		    // one will not be included.
		    
		    if (this.startDate.compareTo(feed.expirationDate) > 0)
		        return;
		    
			fd = new FeedDescriptor();
			fd.feedId = feed.storedId;
			fd.expireOn = isoDate.format(feed.expirationDate);
			
			if (feed.realtimeUrls != null && feed.realtimeUrls.size() > 0) {
			    fd.realtimeUrls = new String[feed.realtimeUrls.size()];
			    fd.realtimeUrls = feed.realtimeUrls.toArray(fd.realtimeUrls);
			}			    
			
			fd.defaultAgencyId = agency;
			fd.setDefaultBikesAllowed(feed.defaultBikesAllowed);

			nextSupersession = findNextSupersession(feed);
			
			// force expire if necessary
			if (nextSupersession != null &&
					nextSupersession.startDate.compareTo(nextSupersession.startDate) >= 0) {
				local.setTime(nextSupersession.startDate);
				// Go back 12 hours, this should be yesterday since we have date at 00:00:00
				local.add(Calendar.HOUR, -12);
				fd.expireOn = isoDate.format(local.getTime());
			}
			
			if (!feed.disabled)
			    toInclude.add(fd);
			else {
			    // if the feed is disabled, try the feed it superseded.
			    // This is in case an agency releases new, bad GTFS that supersedes older but
			    // working GTFS
			    olderFeed = GtfsFeed.find(
	                    "supersededBy = ? ORDER BY startDate DESC",
	                    feed).first();
			    
			    if (olderFeed != null) 
			        addFeeds(agency, olderFeed, toInclude, iteration + 1);
			}
			
			return;
		}
	}
	
	/**
	 * Convert this plan to JSON for Deployer.
	 * @return JSON for Deployer.
	 */
	public String toJson () {
		DeploymentPlanProxy plan = new DeploymentPlanProxy();
		plan.feeds = this.feeds;
		plan.metroId = this.area.id;
		plan.metro = this.area.name;
		plan.bikeRentalSystems = new ArrayList<BikeRentalSystemProxy>();
		for (BikeRentalSystem system : this.area.getBikeRentalSystems()) {
		    plan.bikeRentalSystems.add(new BikeRentalSystemProxy(system));
		}
		
		Gson gson = new Gson();
		return gson.toJson(plan);
	}
	
	// This is serialized to JSON and sent to deployer.
	@SuppressWarnings("unused")
	private class DeploymentPlanProxy {
		private long metroId;
		private String metro;
		private FeedDescriptor[] feeds;
		private List<BikeRentalSystemProxy> bikeRentalSystems;
	}
	
	@SuppressWarnings("unused")
	private class BikeRentalSystemProxy {
	    private String name;
	    private BikeRentalSystemType type;
	    private String url;
	    private String currency;
	    private List<String> fareClasses;
	    
	    public BikeRentalSystemProxy(BikeRentalSystem s) {
	        this.name = s.name;
	        this.type = s.type;
	        this.url = s.url;
	        this.currency = s.currency;
	        this.fareClasses = s.fareClasses;
	    }
	}
	
	public static class FeedDescriptor {
	    // Astute readers will notice that defaultAgencyId is not part of the hash/equals code
	    // This is to prevent multiagency feeds being added twice. 
        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((feedId == null) ? 0 : feedId.hashCode());
            return result;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            FeedDescriptor other = (FeedDescriptor) obj;
            if (feedId == null) {
                if (other.feedId != null)
                    return false;
            } else if (!feedId.equals(other.feedId))
                return false;
            return true;
        }

        public String getFeedId() {
	        return feedId;
	    }

	    public String getExpireOn() {
	        return expireOn;
	    }
	    public String getDefaultAgencyId() {
	        return defaultAgencyId;
	    }
	    
	    public FeedDescriptor () {
	        realtimeUrls = null;
	    }
	    
	    public DefaultBikesAllowedType getDefaultBikesAllowed() {
            return defaultBikesAllowed;
        }

        public void setDefaultBikesAllowed(DefaultBikesAllowedType defaultBikesAllowed) {
            this.defaultBikesAllowed = defaultBikesAllowed;
        }

        private String feedId;
	    private String expireOn;
	    private String defaultAgencyId;
	    private String[] realtimeUrls;
	    private DefaultBikesAllowedType defaultBikesAllowed;
	}

	public FeedDescriptor[] getFeeds() {
	    return feeds;
	}
	
	/**
	 * Find the feed that supersedes this one. This is complicated because if the next feed is
	 * disabled, we need the one after that, &c., &c.
	 * @return
	 */
	private static GtfsFeed findNextSupersession (GtfsFeed feed) {
	    GtfsFeed ret = feed.supersededBy;
	    
	    // if we get to an unsuperseded disabled feed, ret will be null, this loop will end,
	    // function will return null. If this is an unsuperseded feed, ret will be null from
	    // the start and this loop will not execute at all.
	    while (ret != null && (ret.disabled || ret.status == FeedParseStatus.FAILED))
	        ret = ret.supersededBy;
	    
	    return ret;
	}
}

