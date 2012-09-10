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

package controllers;

import play.*;
import play.mvc.*;
import models.*;
import proxies.GtfsFeedProxy;
import utils.GeometryUtils;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.io.ParseException;
import java.util.Date;
import play.data.binding.As;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class GtfsFeeds extends Controller {
    /**
     * Create a new GtfsFeed
     * @param agency_name the human-readable agency name
     * @param agency_url the agency's location on the WWW
     * @param country The country where this agency is located
     * @param data_exchange_id The ID of this feed on GTFS Data Exchange
     * @param data_exchange_url The URL of this feed's page on GTFS Data Exchange
     * @param date_added The date this was added to this site or to GTFS Data Exchange, whichever
     *    is earlier. Defaults to today.
     * @param date_updated The date this feed was last updated on this site
     * @param feed_base_url The URL where this feed may be found on the WWW. May be a direct URL or
     *    a developer site.
     * @param license_url The URL where the license of this feed can be found
     * @param official Is this feed provided and supported by the respective agency
     * @param state The US state abbreviation of this feed
     * @param area_description The human readable description of the area where this feed is located
     * @param geometry The EWKT geometry of the bounds of this feed
     */
    public static void create (String agency_name, String agency_url, String country, 
                               String data_exchange_id, String data_exchange_url, 
                               @As("yyyy-MM-dd'T'hh:mm:ss'Z'") Date date_added,
                               @As("yyyy-MM-dd'T'hh:mm:ss'Z'") Date date_updated,
                               @As("yyyy-MM-dd'T'hh:mm:ss'Z'") Date expiration_date,
                               String feed_base_url, String license_url, 
                               boolean official, String state, String area_description, 
                               String geometry) {
        MultiPolygon the_geom;

        Date now = Calendar.getInstance(TimeZone.getTimeZone("gmt")).getTime();
        if (date_updated == null)
            date_updated = now;
        if (date_added == null)
            date_added = now;

        // parse EWKT
        try {
            the_geom = (MultiPolygon) GeometryUtils.parseEwkt(geometry);
        } catch (ParseException e) {
            renderJSON("{\"status\": \"failure\", \"message\": \"could not parse geometry\"}");
            return;
        }

        GtfsFeed feed = new GtfsFeed(agency_name, agency_url, country, data_exchange_id, 
                                     data_exchange_url, date_added, date_updated, expiration_date,
                                     feed_base_url, license_url, official, state, area_description, 
                                     the_geom);
        feed.save();
        renderJSON("{\"status\": \"success\"}");
    }

    public static void feedsNoAgencies () {
        List<GtfsFeed> noAgency = new ArrayList<GtfsFeed>();
        
        for (GtfsFeed feed : GtfsFeed.<GtfsFeed>findAll()) {
            if (feed.getAgencies().size() == 0) {
                noAgency.add(feed);
            }
        }
        
        render(noAgency);
    }
    
    public static void feeds () {
        List<GtfsFeedProxy> feeds = new ArrayList<GtfsFeedProxy>();

        for (GtfsFeed feed : GtfsFeed.<GtfsFeed>findAll()) {
            if (feed.supersededBy != null)
                continue;
            
            feeds.add(new GtfsFeedProxy(feed));
        }

        // this is fairly quick b/c there are no joins
        renderJSON(feeds);
    }
}