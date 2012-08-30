package updaters;

import java.io.File;
import java.util.List;

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
	
	/**
	 * Release a feed, whatever that means. Probably clean up temp files.
	 */
	public void releaseFeed(String feedId);
	
	/**
	 * Get the stored IDs of all the feeds in here. Used to keep this in sync with the DB.
	 * In some cases things can get put in here without being put in the DB (for instance, 
	 * OutOfMemoryError or hardware failure during feed parsing).
	 */
	public List<String> getFeedIds ();

    public void deleteFeed(String id);
}
