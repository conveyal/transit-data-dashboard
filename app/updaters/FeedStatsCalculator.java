package updaters;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import play.Logger;

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
				if (!startAlreadySet && startDate != null && currentStart.compareTo(startDate) < 0)
					startDate = currentStart;
				
				if (!endAlreadySet && endDate != null && currentEnd.compareTo(endDate) > 0)
					endDate = currentEnd;
			}
			calendar.close();
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
