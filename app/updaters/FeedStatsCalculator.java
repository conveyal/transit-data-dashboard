package updaters;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

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
		
		ZipEntry calendar_txt = this.gtfs.getEntry("calendar.txt");
		if (calendar_txt != null) {
			CSVReader calendar = new CSVReader(
					new BufferedReader(
							new InputStreamReader(
									this.gtfs.getInputStream(calendar_txt)
							)
					)
				);
			
			Date currentStart = null;
			Date currentEnd = null;
			
			String[] firstRow = calendar.readNext();
			String[] currentRow;
			
			// Don't re-set if we have an authoritative answer from feed_info
			boolean startAlreadySet = this.startDate != null;
			boolean endAlreadySet = this.endDate != null;
			
			// find column offsets
			int startDateCol = -1, endDateCol = -1;
			for (int i = 0; i < firstRow.length; i++) {
				if (firstRow[i].toLowerCase().equals("start_date"))
					startDateCol = i;
				else if (firstRow[i].toLowerCase().equals("end_date"))
					endDateCol = i;
			}
			
			if (startDateCol == -1 || endDateCol == -1) {
				Logger.error("calendar.txt is missing start_date or end_date");
				calendar.close();
				throw new ParseException("calendar.txt is missing start_date or end_date", 0);
			}
			
			while ((currentRow = calendar.readNext()) != null) {
				currentStart = gtfsDateFormat.parse(currentRow[startDateCol]);
				currentEnd = gtfsDateFormat.parse(currentRow[endDateCol]);
			
				// TODO Move them to the nearest day with service, i.e. if a weekday
				// service pattern starts on a Saturday move it to the following Monday
				
				// expand the range on either end
				if (startDate == null || 
						(!startAlreadySet && currentStart.compareTo(startDate) < 0))
					startDate = currentStart;
				
				if (endDate == null ||
						(!endAlreadySet && currentEnd.compareTo(endDate) > 0))
					endDate = currentEnd;
			}
			calendar.close();
		}
		
		ZipEntry calendar_dates_txt = this.gtfs.getEntry("calendar_dates.txt");
		if (calendar_dates_txt != null) {
			CSVReader dates = getReaderForZipEntry(calendar_dates_txt);
			
			String[] cols = dates.readNext();
			String[] row;
			Date date;
			
			int dateCol = -1, excTypeCol = -1;
			
			for (int i = 0; i < cols.length; i++) {
				if (cols[i].toLowerCase().equals("date"))
					dateCol = i;
				else if (cols[i].toLowerCase().equals("exception_type"))
					excTypeCol = i;
			}
			
			if (dateCol == -1 || excTypeCol == -1) {
				dates.close();
				Logger.error("bad calendar_dates.txt");
				throw new ParseException("bad calendar_dates.txt", 0);
			}
			
			while ((row = dates.readNext()) != null) {
				if (row[excTypeCol].equals("1")) {
					// service will run
					date = gtfsDateFormat.parse(row[dateCol]);
					// this is before the start date
					if (startDate == null || date.compareTo(startDate) < 0)
						startDate = date;
					else if (endDate == null || date.compareTo(endDate) > 0)
						endDate = date;
				}
			}
		}
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
