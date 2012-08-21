package updaters;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import com.google.gson.Gson;

import play.Play;

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
	private static TimeZone gmt = TimeZone.getTimeZone("GMT");
	private Calendar calendar = Calendar.getInstance(gmt);
	private FeedDescriptor[] feeds;
	
	/**
	 * Create a plan for the given metro at the current time and for the default window.
	 * @param area
	 */
	public DeploymentPlan(MetroArea area) {
		this(area, new Date());
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
		this.startDate = date;
		calendar.setTime(date);
		calendar.add(Calendar.DAY_OF_YEAR, window);
		this.endDate = calendar.getTime();
		
		List<GtfsFeed> feeds;
		Set<FeedDescriptor> toInclude = new HashSet<FeedDescriptor>();
		
		for (NtdAgency agency : area.getAgencies()) {
			// all the unsuperseded feeds for this agency
			
			for (GtfsFeed feed : agency.feeds) {
				if (feed.supersededBy != null) {
					continue;
				}
				
				addFeeds(feed, toInclude);
			}
			
			this.feeds = new FeedDescriptor[toInclude.size()];
			this.feeds = toInclude.toArray(this.feeds);
		}
	}
	
	/**
	 * Follows the chain of supersession back until it adds enough feeds to cover the window. 
	 * @param feed
	 * @param toInclude
	 */
	private void addFeeds(GtfsFeed feed, Set<FeedDescriptor> toInclude) {
		addFeeds(feed, toInclude, 0);
	}
	
	private void addFeeds(GtfsFeed feed, Set<FeedDescriptor> toInclude, int i) {
		FeedDescriptor fd;
		GtfsFeed olderFeed;
		
		if (this.startDate.compareTo(feed.startDate) < 0) {
			// Feed starts after today
			
			if (this.endDate.compareTo(feed.startDate) > 0) {
				// Feed starts before the end of the window, include it
				fd = new FeedDescriptor();
				fd.feedId = feed.storedId;
				
				// force expire before the next one starts, if it's not already
				if (feed.supersededBy != null && 
						feed.expirationDate.compareTo(feed.supersededBy.startDate) >= 0) {
					calendar.setTime(feed.supersededBy.startDate);
					// Go back one day to get the end date for this feed
					calendar.add(Calendar.DAY_OF_YEAR, -1);
					fd.expireOn = calendar.getTime();
				}
				// otherwise, let it expire normally
				else {
					fd.expireOn = feed.expirationDate;
				}
				
				fd.defaultAgencyId = feed.dataExchangeId + "_" + i;
			}
			
			olderFeed = GtfsFeed.find("bySupersededBy", feed).first();
			if (olderFeed == null) {
				// Data doesn't go back far enough
				return;
			}
			
			addFeeds(olderFeed, toInclude, i + 1);
			return;
		}
		else {
			// Feed does not start after today. Presumably we'll be following feeds
			// through supersession in roughly chronological order, so we don't need
			// to continue searching for older feeds.
			fd = new FeedDescriptor();
			fd.feedId = feed.storedId;
			fd.expireOn = feed.expirationDate;
			fd.defaultAgencyId = feed.dataExchangeId + "_" + i;
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
		
		Gson gson = new Gson();
		return gson.toJson(plan);
	}
	
	// This is serialized to JSON and sent to deployer.
	private class DeploymentPlanProxy {
		private FeedDescriptor[] feeds;
	}
	
	private class FeedDescriptor {
		private String feedId;
		private Date expireOn;
		private String defaultAgencyId;
	}
}
