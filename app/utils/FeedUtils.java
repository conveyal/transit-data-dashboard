package utils;

import java.util.Date;

import models.GtfsFeed;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class FeedUtils {
	/**
	 * Copy the data from a JSON representation of a feed into a feed object.
	 */
	public static void copyFromJson (JsonObject json, GtfsFeed feed) {
		feed.dateUpdated = getDateFromJson(json.get("date_last_updated"));
		feed.feedBaseUrl = json.get("feed_baseurl").getAsString();
		feed.agencyName = json.get("name").getAsString();
		feed.areaDescription = json.get("area").getAsString();
		feed.agencyUrl = json.get("url").getAsString();
		feed.country = json.get("country").getAsString();
		feed.state = json.get("state").getAsString();
		feed.licenseUrl = json.get("license_url").getAsString();
		feed.dataExchangeUrl = json.get("dataexchange_url").getAsString();
		feed.dateAdded = getDateFromJson(json.get("date_added"));
		feed.official = json.get("is_official").getAsBoolean();
		feed.dataExchangeId = json.get("dataexchange_id").getAsString();
	}
	
	/**
	 * Get a Java date from a JsonElement with Unix time.
	 * @param e
	 * @return
	 */
	private static Date getDateFromJson(JsonElement e) {
		long date = ((long) e.getAsDouble()) * 1000L;
		return new Date(date);
	}
}
