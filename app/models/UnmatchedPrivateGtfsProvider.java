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

package models;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Query;

import play.Logger;
import play.db.jpa.JPA;
import play.db.jpa.Model;
import proxies.NtdAgencyProxy;

/**
 * This represents an agency, loaded from Google, that was not automatically matched.
 * @author mattwigway
 *
 */
@Entity
public class UnmatchedPrivateGtfsProvider extends Model {
    /** The metro area that was matched */
    @ManyToOne
    public MetroArea metroArea;
    
    /** Alternately the description of the unmatched metro */
    @ManyToOne
    public UnmatchedMetroArea unmatchedArea;
    
    /** The latitude of this area on Google Transit */
    public double lat;
    
    /** The longitude */
    public double lon;
    
    /** The name of the agency */
    public String name;

    /**
     * Attempt to find the agency this goes with.
     */
    public void search () {
        if (this.metroArea == null)
            return;
        
        // find matching agencies
        String qs = "SELECT a.id FROM ntdagency a " +
                "INNER JOIN metroarea_ntdagency mn ON (a.id = mn.agencies_id) " +
                "INNER JOIN metroarea m ON (m.id = mn.metroarea_id)" +
                "WHERE (to_tsvector(CONCAT(a.name, ' ', " + 
                "regexp_replace(a.url, '\\.|https?://|/|_|\\-', ' ', 'g'))) " +
                "@@ plainto_tsquery(?) " +
                "OR a.url ILIKE CONCAT('%', ?, '%')) " +
                "AND m.id = ?";

        Query q = JPA.em().createNativeQuery(qs);
        q.setParameter(1, this.name);
        q.setParameter(2, this.name);
        q.setParameter(3, this.metroArea.id);
        
        NtdAgency agency;
        boolean hadAtLeastOneAgency = false;
        for (Object result : q.getResultList()) {
            hadAtLeastOneAgency = true;
            agency = NtdAgency.findById(((BigInteger) result).longValue());
            // logged with WARN level so it shows up in production logs
            Logger.warn("Matching agency %s to %s", this.name, agency.name);
            agency.googleGtfs = true;
            agency.save();
        }
        
        if (hadAtLeastOneAgency) {
            this.delete();
        } 
    }
}