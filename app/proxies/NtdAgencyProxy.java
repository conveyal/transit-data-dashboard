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

package proxies;

import models.MetroArea;
import models.NtdAgency;
import models.GtfsFeed;
import java.util.List;
import java.util.ArrayList;

/**
 * Proxy class for serialization of an NtdAgency
 */
public class NtdAgencyProxy implements Proxy {
    public String name;
    public String url;
    // a String since it has to be serialized
    public String metro;
    public int population;
    public int ridership;
    public int passengerMiles;
    public boolean publicGtfs;
    public boolean googleGtfs;
    /** WGS84 latitude, a Double so that it is nullable */
    public Double lat;
    /** WGS84 longitude */
    public Double lon;
    public long id;

    // these are not always initialized; they are only initialized when returning a single agency
    // otherwise it's too DB intensive
    public List<GtfsFeedProxy> feeds = null;
    public String ntdId = null;

    /**
     * Create a header row for a csv file
     */
    public String[] toHeader () {
        String[] retval =  {"Name", "URL", "Metro Area", "Service Area Population", 
                            "Annual Unlinked Passenger Trips", "Annual Passenger Miles",
                            "Public GTFS", "Google Maps", "Metro Area Latitude", 
                            "Metro Area Longitude"};
        return retval;
    }

    /**
     * Create a row for the CSV file from this proxy
     */
    public String[] toRow () {
        String[] retval = {name, url, metro, "" + population, "" + ridership, "" + passengerMiles,
                           (publicGtfs ? "Yes" : "No"), (googleGtfs ? "Yes" : "No"), "" + lat, 
                           "" + lon};
        return retval;
    }

    public NtdAgencyProxy (String name, String url, String metro, int population, int ridership,
                           int passengerMiles, boolean publicGtfs, boolean googleGtfs,
                           Double lat, Double lon, long id) {
        this.name = name;
        this.url = url;
        this.metro = metro;
        this.population = population;
        this.ridership = ridership;
        this.passengerMiles = passengerMiles;
        this.publicGtfs = publicGtfs;
        this.googleGtfs = googleGtfs;
        this.lat = lat;
        this.lon = lon;
        this.id = id;
    }

    /**
     * Make an NtdAgencyProxy from an NtdAgency
     */
    public NtdAgencyProxy (NtdAgency agency) {
        name = agency.name;
        url = agency.url;

        // choose a representative metro
        metro = null;
        for (MetroArea m : agency.getMetroAreas()) {
            metro = m.toString();
            break;
        }

        population = agency.population;
        ridership = agency.ridership;
        passengerMiles = agency.passengerMiles;
        
        if (agency.feeds.size() > 0)
            publicGtfs = true;
        else
            publicGtfs = false;

        // TODO: parse google gtfs
        googleGtfs = agency.googleGtfs;

        id = agency.id;

        ntdId = agency.ntdId;

        feeds = new ArrayList<GtfsFeedProxy>();

        for (GtfsFeed feed : agency.feeds) {
            if (feed.supersededBy != null)
                continue;
            
            feeds.add(new GtfsFeedProxy(feed));
        }
    }
}