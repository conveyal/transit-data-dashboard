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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.FeedInfo;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.serialization.GtfsReader;

import models.GtfsFeed;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.algorithm.ConvexHull;

import play.Logger;
import utils.GeometryUtils;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Calculate feed stats for a zipped GTFS.
 * @author mattwigway
 *
 */
public class FeedStatsCalculator {
	private File rawGtfs;
	private GtfsDaoImpl store;
	private int stops;
	
	// These dates are stored in agency local time in this timezone
	private TimeZone timezone;
	private Date startDate;
	private Date endDate;
	private MultiPolygon the_geom;
	
	public Date getStartDate() {
		return startDate;
	}

	public Date getEndDate() {
		return endDate;
	}
	
	public Geometry getGeometry () {
	    return the_geom;
	}
	
	public FeedStatsCalculator(File gtfs) throws Exception {
		this.rawGtfs = gtfs;
	    GtfsReader reader = new GtfsReader();
	    reader.setInputLocation(rawGtfs);
	        
	    this.store = new GtfsDaoImpl();
	    reader.setEntityStore(this.store);
	    reader.run();
	    reader.close();
	    
		this.startDate = null;
		this.endDate = null;
		
		findTimeZone();
		calculateStartAndEnd();
		calculateGeometry();
		calculateNumStops();
	}
	
	private void findTimeZone () {
        for (Agency agency : store.getAllAgencies()) {
            // TODO: Multiple agencies with different time zones
            this.timezone = TimeZone.getTimeZone(agency.getTimezone());
            break;
        }
	}
	
	private void calculateNumStops() {
	    stops = store.getAllStops().size();
    }

    /**
	 * Apply the calculated feed stats to the appropriate fields of the given GtfsFeed
	 */
	public void apply (GtfsFeed feed) {
		feed.startDate = this.startDate;
		feed.expirationDate = this.endDate;
		feed.the_geom = this.the_geom;
		feed.stops = this.stops;
		feed.timezone = this.timezone;
	}
	
	/**
	 * Apply not just the feed stats, but also the 
	 */
	
	private void calculateStartAndEnd () throws Exception {
	    // First, read feed_info.txt
	    // TODO is 1 ever not the correct value?
	    FeedInfo feedInfo = store.getFeedInfoForId(1);
	    if (feedInfo != null) {
	        ServiceDate d;
	        d = feedInfo.getStartDate();
	        if (d != null) {
	            Calendar c = d.getAsCalendar(timezone);
	            // move to GTFS noon, which will always be during the day. This accounts for both
	            // multitimezone feeds and for daylight savings time 
	            c.add(Calendar.HOUR_OF_DAY, 12);
	            startDate = c.getTime();
	        }
	        
	        d = feedInfo.getEndDate();
	        if (d != null) {
	            Calendar c = d.getAsCalendar(timezone);
	            c.add(Calendar.HOUR_OF_DAY, 12);
	            endDate = c.getTime();
	        }
	    }
	    
		
		// we have an authoritative answer
		if (startDate != null && endDate != null) return;
	
		// let OBA deal with the complexities of interactions between calendar.txt and 
		// calendar_dates.txt
		// This code is lifted and slightly modified from
		// https://github.com/demory/otp_gtfs/blob/master/java/gtfsmetrics/src/main/java/org/openplans/gtfsmetrics/CalendarStatus.java
		Map<AgencyAndId, Set<ServiceDate>> addExceptions = new HashMap<AgencyAndId, Set<ServiceDate>>();
        Map<AgencyAndId, Set<String>> removeExceptions = new HashMap<AgencyAndId, Set<String>>();
        for(ServiceCalendarDate date : store.getAllCalendarDates()) {
            if(date.getExceptionType() == ServiceCalendarDate.EXCEPTION_TYPE_ADD) {
                Set<ServiceDate> dateSet = addExceptions.get(date.getServiceId());
                if(dateSet == null) {
                    dateSet = new HashSet<ServiceDate>();
                    addExceptions.put(date.getServiceId(), dateSet);
                }
                dateSet.add(date.getDate());
            }
            else if(date.getExceptionType() == ServiceCalendarDate.EXCEPTION_TYPE_REMOVE) {
                Set<String> dateSet = removeExceptions.get(date.getServiceId());
                if(dateSet == null) {
                    dateSet = new HashSet<String>();
                    removeExceptions.put(date.getServiceId(), dateSet);
                }
                dateSet.add(constructMDYString(date.getDate()));
            }
        }
        
        DateTime latestEnd = new DateTime(0);
        DateTime earliestStart = null;
        
        for (ServiceCalendar svcCal : store.getAllCalendars()) {
        
            Calendar c;
            c = svcCal.getStartDate().getAsCalendar(timezone);
            c.add(Calendar.HOUR_OF_DAY, 12);
            DateTime start = new DateTime(c.getTime());
            
            c = svcCal.getEndDate().getAsCalendar(timezone);
            c.add(Calendar.HOUR_OF_DAY, 12);
            DateTime end = new DateTime(c.getTime());
            
            int totalDays = Days.daysBetween(start, end).getDays();
            for(int d=0; d < totalDays; d++) {
                int gd = getDay(svcCal, end.dayOfWeek().get());// dateCal.get(Calendar.DAY_OF_WEEK));
                boolean removeException = false;
                Set<String> dateSet = removeExceptions.get(svcCal.getServiceId());
                if(dateSet != null) {
                 removeException = dateSet.contains(constructMDYString(end));
                }
                if(gd == 1 && !removeException) break;
                end = end.minusDays(1);
            }
            if (end.isAfter(latestEnd))
            	latestEnd = end;
            
            totalDays = Days.daysBetween(start, end).getDays();
            for(int d=0; d < totalDays; d++) {
                int gd = getDay(svcCal, start.dayOfWeek().get());// dateCal.get(Calendar.DAY_OF_WEEK));
                boolean removeException = false;
                Set<String> dateSet = removeExceptions.get(svcCal.getServiceId());
                if(dateSet != null) {
                 removeException = dateSet.contains(constructMDYString(start));
                }
                if(gd == 1 && !removeException) break;
                start = start.plusDays(1);
            }
            if (earliestStart == null || start.isBefore(earliestStart))
            	earliestStart = start;
            
        }
        
        // now, expand based on calendar_dates.txt
        for(Set<ServiceDate> dateSet: addExceptions.values()) {
            for(ServiceDate sd : dateSet) {
                DateTime dt = new DateTime(sd.getAsDate(timezone).getTime());
                if (dt.isAfter(latestEnd))
                	latestEnd = dt;
                if (dt.isBefore(earliestStart))
                	earliestStart = dt;
            }
        }
        
        this.startDate = earliestStart.toDate();
        this.endDate = latestEnd.toDate();        
    }
    
    private static int getDay(ServiceCalendar cal, int dow) {
        switch(dow) {
            case 1: return cal.getMonday();
            case 2: return cal.getTuesday();
            case 3: return cal.getWednesday();
            case 4: return cal.getThursday();
            case 5: return cal.getFriday();
            case 6: return cal.getSaturday();
            case 7: return cal.getSunday();
        }
        return 0;
    }
    
    private static String constructMDYString(ServiceDate date) {
        return date.getMonth()+"-"+date.getDay()+"-"+date.getYear();
    }
    
    private static String constructMDYString(DateTime dt) {
        return dt.getMonthOfYear()+"-"+dt.getDayOfMonth()+"-"+dt.getYear();
    }
	
    private void calculateGeometry () {
        List<Coordinate> stopsGeom = new ArrayList<Coordinate>();
        // Make a coordinate for each stop, and add it to the MultiPoint
        double lon, lat;
        for (Stop stop : store.getAllStops()) {
            lon = stop.getLon();
            lat = stop.getLat();

            // ignore stops near 0,0 island
            if (Math.abs(lon) < 1 && Math.abs(lat) <1)
                continue;

            stopsGeom.add(
                    new Coordinate(lon, lat)
                    );
        }

        GeometryFactory gf = GeometryUtils.getGeometryFactoryForSrid(4326);
        Coordinate[] coords = new Coordinate[stopsGeom.size()];
        stopsGeom.toArray(coords);
        Geometry geom = new ConvexHull(coords, gf).getConvexHull();

        if (geom instanceof Polygon) {
            Polygon[] poly = new Polygon[] {(Polygon) geom};
            geom = gf.createMultiPolygon(poly);
        }

        this.the_geom = (MultiPolygon) geom;		
    }

    /**
     * Apply not just the stats, but other GTFS-derived info.
     * @param feed
     */
    public void applyExtended(GtfsFeed feed) {
        this.apply(feed);
        for (Agency agency : store.getAllAgencies()) {
            feed.agencyName = agency.getName();
            feed.agencyUrl = agency.getUrl();
            break;
        }
    }
}

