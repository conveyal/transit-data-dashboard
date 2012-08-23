package updaters;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
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
	private ZipFile gtfs;
	
	private Date startDate;
	private Date endDate;
	private MultiPolygon the_geom;
	
	public Date getStartDate() {
		return startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	private static SimpleDateFormat gtfsDateFormat = new SimpleDateFormat("yyyyMMdd");
	
	public FeedStatsCalculator(File gtfs) throws Exception {
		this.rawGtfs = gtfs;
		this.gtfs = new ZipFile(gtfs);
		this.startDate = null;
		this.endDate = null;
		calculateStartAndEnd();
		calculateGeometry();
	}
	
	/**
	 * Apply the calculated feed stats to the appropriate fields of the given GtfsFeed
	 */
	public void apply (GtfsFeed feed) {
		feed.startDate = this.startDate;
		feed.expirationDate = this.endDate;
		feed.the_geom = this.the_geom;
	}
	
	private void calculateStartAndEnd () throws Exception {
		// First, read feed_info.txt
		// no need to parse the whole GTFS if all we need is feed info
		ZipEntry feed_info_txt = this.gtfs.getEntry("feed_info.txt");
		if (feed_info_txt != null) {
			CSVReader feedInfo = getReaderForZipEntry(feed_info_txt);
			String[] cols = feedInfo.readNext();
			String[] info = feedInfo.readNext();
			
			// find the columns
			for (int i = 0; i < cols.length; i++) {
				if (cols[i].toLowerCase().equals("feed_start_date"))
					startDate = gtfsDateFormat.parse(info[i]);
				else if (cols[i].toLowerCase().equals("feed_end_date"))
					endDate = gtfsDateFormat.parse(info[i]);
			}
			feedInfo.close();
		}
		
		// we have an authoritative answer
		if (startDate != null && endDate != null) return;
	
		// let OBA deal with the complexities of interactions between calendar.txt and 
		// calendar_dates.txt
		// This code is lifted and slightly modified from
		// https://github.com/demory/otp_gtfs/blob/master/java/gtfsmetrics/src/main/java/org/openplans/gtfsmetrics/CalendarStatus.java
		
		GtfsReader reader = new GtfsReader();
		reader.setInputLocation(rawGtfs);
		
		GtfsDaoImpl store = new GtfsDaoImpl();
        reader.setEntityStore(store);

        reader.run();
        
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
        
            DateTime start = new DateTime(svcCal.getStartDate().getAsDate().getTime());
            DateTime end = new DateTime(svcCal.getEndDate().getAsDate().getTime());
            
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
                DateTime dt = new DateTime(sd.getAsDate().getTime());
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
	
	private void calculateGeometry () throws Exception {
		ZipEntry stops_txt = this.gtfs.getEntry("stops.txt");
		if (stops_txt != null) {
			CSVReader stops = getReaderForZipEntry(stops_txt);
			// Get coordinates for each stop, then convex-hull them.
			List<Coordinate> stopsGeom = new ArrayList<Coordinate>();
			
			String[] cols = stops.readNext();
			String[] row;
			
			int lonCol = -1, latCol = -1;
			
			// find the columns
			for (int i = 0; i < cols.length; i++) {
				if (cols[i].toLowerCase().equals("stop_lon"))
					lonCol = i;
				else if (cols[i].toLowerCase().equals("stop_lat"))
					latCol = i;
			}
			
			if (lonCol == -1 || latCol == -1) {
				Logger.error("Missing stop_lat or stop_lon!");
				stops.close();
				throw new ParseException("Missing stop_lat or stop_lon!", 0);
			}
			
			// Make a coordinate for each stop, and add it to the MultiPoint
			double lon, lat;
			while ((row = stops.readNext()) != null )	{
				lon = Double.parseDouble(row[lonCol]);
				lat = Double.parseDouble(row[latCol]);
				
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
	}
	
	private CSVReader getReaderForZipEntry(ZipEntry entry) throws IOException {
		return new CSVReader(
				new BufferedReader(
						new InputStreamReader(
								this.gtfs.getInputStream(entry)
						)
				)
			);
	}
}
