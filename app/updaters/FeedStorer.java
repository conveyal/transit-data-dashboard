package updaters;

import java.io.File;

/**
 * Download and store a GTFS feed.
 * 
 * @author mattwigway
 */
public interface FeedStorer {
	/**
	 * Fetch the given URL and return an ID.
	 * @param url The URL to fetch
	 * @return a string identifier for use with getFeed
	 */
	public String storeFeed(String url);
	
	/**
	 * Return a file object for the given identifier.
	 * @param feedId the identifier, as returned by storeFeed
	 * @return A file object accessing the desired file.
	 */
	public File getFeed(String feedId); 
}
