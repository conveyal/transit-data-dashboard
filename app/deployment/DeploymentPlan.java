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
import java.net.URL;

import com.google.gson.Gson;

import play.Play;
import play.libs.WS;
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
	private SimpleDateFormat isoDate;
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
		this.isoDate = new SimpleDateFormat("yyyy-MM-dd");
		this.startDate = date;
		calendar.setTime(date);
		calendar.add(Calendar.DAY_OF_YEAR, window);
		this.endDate = calendar.getTime();
		
		Set<FeedDescriptor> toInclude = new HashSet<FeedDescriptor>();
		
		// Clear all the scheduled rebuilds of this area; if still applicable, they will be
		// recreated automatically.
		DeploymentPlanScheduler.clearRebuilds(this.area);
		
		for (NtdAgency agency : area.agencies) {
			// all the unsuperseded feeds for this agency
			
			for (GtfsFeed feed : agency.feeds) {
				if (feed.supersededBy != null) {
					continue;
				}
				
				if (feed.status == FeedParseStatus.FAILED) {
				    continue;
				}
				
				addFeeds(agency.name + "_" + agency.id, feed, toInclude);
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
	 */
	public void sendTo(String url) {
	    WS.url(url).setParameter("data", this.toJson()).get();
	}
	
	private void addFeeds(String agency, GtfsFeed feed, Set<FeedDescriptor> toInclude, int iteration) {
		FeedDescriptor fd;
		GtfsFeed olderFeed;
		
		if (this.startDate.compareTo(feed.startDate) < 0) {
			// Feed starts after today
			
			if (this.endDate.compareTo(feed.startDate) > 0) {
				// Feed starts before the end of the window, include it
				fd = new FeedDescriptor();
				fd.feedId = feed.storedId;
				fd.defaultBikesAllowed = feed.defaultBikesAllowed;
				
				// force expire before the next one starts, if it's not already
				if (feed.supersededBy != null && 
						feed.expirationDate.compareTo(feed.supersededBy.startDate) >= 0) {
					calendar.setTime(feed.supersededBy.startDate);
					// Go back 12 hours, this should be yesterday since we have date at 00:00:00
					calendar.add(Calendar.HOUR, -12);
					fd.expireOn = isoDate.format(calendar.getTime());
				}
				// otherwise, let it expire normally
				else {
					fd.expireOn = isoDate.format(feed.expirationDate);
				}
				
				fd.realtimeUrl = feed.realtimeUrl;
				fd.defaultAgencyId = agency;
				
				// if this feed is already present, there is no reason to continue the search
				if (!toInclude.add(fd))
				    return;
			}
			else {
			    // the feed starts after the end of the window, so it shouldn't be included, but
			    // the graph needs to be rebuilt on the day it comes into the window.
			    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("gmt"));
			    cal.setTime(feed.startDate);
			    // - 1 so it will be sure to rebuild
			    cal.add(Calendar.DAY_OF_YEAR, -this.window - 1);
			    DeploymentPlanScheduler.scheduleRebuild(feed, cal.getTime());
			}
			
			olderFeed = GtfsFeed.find(
			        "supersededBy = ? ORDER BY startDate DESC WHERE status <> 'FAILED'",
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
			fd = new FeedDescriptor();
			fd.feedId = feed.storedId;
			fd.expireOn = isoDate.format(feed.expirationDate);
			fd.realtimeUrl = feed.realtimeUrl;
			fd.defaultAgencyId = agency;
			fd.defaultBikesAllowed = feed.defaultBikesAllowed;
			
			// force expire if necessary
			if (feed.supersededBy != null &&
					feed.expirationDate.compareTo(feed.supersededBy.startDate) >= 0) {
				calendar.setTime(feed.supersededBy.startDate);
				// Go back 12 hours, this should be yesterday since we have date at 00:00:00
				calendar.add(Calendar.HOUR, -12);
				fd.expireOn = isoDate.format(calendar.getTime());
			}
			
			toInclude.add(fd);
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
	private class DeploymentPlanProxy {
		private long metroId;
		private String metro;
		private FeedDescriptor[] feeds;
		private List<BikeRentalSystemProxy> bikeRentalSystems;
	}
	
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
            result = prime
                    * result
                    + ((defaultBikesAllowed == null) ? 0 : defaultBikesAllowed
                            .hashCode());
            result = prime * result
                    + ((expireOn == null) ? 0 : expireOn.hashCode());
            result = prime * result
                    + ((feedId == null) ? 0 : feedId.hashCode());
            result = prime * result
                    + ((realtimeUrl == null) ? 0 : realtimeUrl.hashCode());
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
            if (defaultBikesAllowed != other.defaultBikesAllowed)
                return false;
            if (expireOn == null) {
                if (other.expireOn != null)
                    return false;
            } else if (!expireOn.equals(other.expireOn))
                return false;
            if (feedId == null) {
                if (other.feedId != null)
                    return false;
            } else if (!feedId.equals(other.feedId))
                return false;
            if (realtimeUrl == null) {
                if (other.realtimeUrl != null)
                    return false;
            } else if (!realtimeUrl.equals(other.realtimeUrl))
                return false;
            return true;
        }

        public String getFeedId() {
	        return feedId;
	    }

	    /**
	     * Add a feed to the set if it is advisable to do so, removing other feeds that are in
	     * the way.
	     * @param toInclude
	     * @param fd
	     */
	    public static void addAndRemoveIfAdvisable(
	            Set<FeedDescriptor> toInclude, FeedDescriptor fd) {
	        if (toInclude.contains(fd))
	            return;

	        List<FeedDescriptor> toRemove = new ArrayList<FeedDescriptor>();
	        boolean foundSameFeedWithLaterExpiration = false;
	        String id = fd.getFeedId();

	        for (FeedDescriptor o : toInclude) {
	            if (id.equals(o.feedId)) {
	                int comparison = fd.getExpireOn().compareTo(o.getExpireOn());

	                // it expires later, it should be added and the other removed
	                if (comparison > 0) {
	                    toRemove.add(o);
	                    break;
	                }
	                else if (comparison == 0) {
	                    // do nothing; it will be added
	                }
	                else if (comparison < 0) {
	                    foundSameFeedWithLaterExpiration = true;
	                }   
	            }
	        }

	        for (FeedDescriptor o : toRemove) {
	            toInclude.remove(o);
	        }

	        if (!foundSameFeedWithLaterExpiration)
	            toInclude.add(fd);
	    }


	    public String getExpireOn() {
	        return expireOn;
	    }
	    public String getDefaultAgencyId() {
	        return defaultAgencyId;
	    }
	    private String feedId;
	    private String expireOn;
	    private String defaultAgencyId;
	    private String realtimeUrl;
	    private DefaultBikesAllowedType defaultBikesAllowed;

	}

	public FeedDescriptor[] getFeeds() {
	    return feeds;
	}
}

