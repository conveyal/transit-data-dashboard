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

package utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Query;

import models.FeedParseStatus;
import models.GtfsFeed;
import models.MetroArea;
import models.MetroAreaSource;
import models.NtdAgency;
import models.ReviewType;
import play.db.jpa.JPA;

public class DbUtils {
    public static Set<MetroArea> mapFeedsWithNoAgencies () {
        Set<MetroArea> changedMetros = new HashSet<MetroArea>();
        
        NtdAgency agency;
        MetroArea metro;
        for (GtfsFeed feed : GtfsFeed.<GtfsFeed>findAll()) {
            if (feed.getAgencies().size() != 0)
                continue;
            
            if (feed.status != FeedParseStatus.SUCCESSFUL)
                continue;
            
            if (feed.supersededBy != null)
                continue;
            
            agency = new NtdAgency(feed);
            agency.feeds.add(feed);

            changedMetros.addAll(agency.findAndAssignMetroArea());
        }
        
        return changedMetros;
    }
}
