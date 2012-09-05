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

import models.GtfsFeed;
import java.util.Date;

public class GtfsFeedProxy {
    public long id;
    public String agencyName;
    public String agencyUrl;
    public String feedBaseUrl;
    public boolean official;
    public Date expires; 
    public String status;

    public GtfsFeedProxy (GtfsFeed feed) {
        this.id = feed.id;
        this.agencyName = feed.agencyName;
        this.agencyUrl = feed.agencyUrl;
        this.feedBaseUrl = feed.feedBaseUrl;
        this.expires = feed.expirationDate;
        this.official = feed.official;
        this.status = feed.status.toString();
    }
}