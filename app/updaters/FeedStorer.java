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
