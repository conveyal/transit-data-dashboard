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

package controllers;

import play.*;
import play.mvc.*;
import play.db.jpa.JPA;
import models.*;
import proxies.NtdAgencyProxy;
import proxies.Proxy;
import utils.DataDumpFormat;
import utils.DataUtils;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.math.BigInteger;
import javax.persistence.Query;
import com.google.gson.Gson;

public class NtdAgencies extends Controller {
    /**
     * Create a new NtdAgency
     * @param name the agency name
     * @param url the agency's permanent location on the WWW. Not a marketing URL, e.g. WMATA would
     *    be http://wmata.com not http://metroopensdoors.com
     * @param ntdId this agency's ID in the <a href="http://www.ntdprogram.gov">National Transit
     *    Database</a>.
     * @param population the population of this agency's service area
     * @param ridership the annual unlinked passenger trips of this agency
     * @param passenger_miles the annual passenger miles of this agency
     */
    public static void create (String name, String url, String ntdId, int population,
                               List<String> uzaNames, int ridership, int passenger_miles) {
        new NtdAgency(name, url, ntdId, population, uzaNames, ridership, passenger_miles).save();
        renderJSON("{\"status\": \"success\"}");
    }

    /**
     * Return a list of agencies
     * @param as JSON or CSV
     */
    public static void agencies (DataDumpFormat as) {
        List<Proxy> agencies = new ArrayList<Proxy>();
        List<Object[]> results;
        NtdAgencyProxy current;

        // send JSON by default
        if (as == null)
            as = DataDumpFormat.JSON;
        
        String qs =
            "SELECT a.name, a.url, m.name AS metroname, a.population, a.ridership, " +
            "a.passengerMiles, " + 
            "CASE "+
            "  WHEN f.publicGtfs THEN true " +
            "  ELSE false " +
            "END AS publicGtfs, " +
            "googleGtfs, m.source AS metrosource, " +
            "Y(ST_Transform(ST_Centroid(m.the_geom), 4326)) AS lat," +
            "X(ST_Transform(ST_Centroid(m.the_geom), 4326)) AS lon, " +
            "a.id " +
            "FROM (NtdAgency a " +
            "  LEFT JOIN (SELECT mn.agencies_id AS id, min(m.name) AS name, bool_and(m.disabled) AS disabled, min(m.source) AS source, ST_Union(m.the_geom) AS the_geom FROM MetroArea_NtdAgency mn LEFT JOIN MetroArea m ON (mn.metroarea_id = m.id) GROUP BY mn.agencies_id) m " +
            "    ON (m.id = a.id))" +
            "  LEFT JOIN (SELECT j.NtdAgency_id, count(*) > 0 AS publicGtfs " + 
            "               FROM NtdAgency_GtfsFeed j " +
            "             GROUP BY j.NtdAgency_id) f ON (a.id = f.NtdAgency_id) " +
            // if any are enabled, show this agency
            "WHERE m.disabled = false AND a.disabled = false;";

        Query q = JPA.em().createNativeQuery(qs);
        
        results = q.getResultList();

        for (Object[] result : results) {
         
            current = new NtdAgencyProxy(
                (String) result[0], // name
                (String) result[1], // url
                (String) result[2], // Metro name
                ((Integer) result[3]).intValue(), // population
                ((Integer) result[4]).intValue(), // ridership
                ((Integer) result[5]).intValue(), // passenger miles
                ((Boolean) result[6]).booleanValue(), // public GTFS
                ((Boolean) result[7]).booleanValue(), // google GTFS
                (Double) result[9], // lat
                (Double) result[10], // lon
                ((BigInteger) result[11]).longValue() // id
                                         );

            agencies.add(current);
        }

        if (as == DataDumpFormat.CSV) {
            // http://stackoverflow.com/questions/398237
            response.setContentTypeIfNotSet("text/csv");
            response.setHeader("Content-Disposition", 
                               "attachment;filename=TransitDataDashboard.csv");
            renderText(DataUtils.encodeCsv(agencies));
        } 
        else if (as == DataDumpFormat.JSON) {
            response.setHeader("Content-Disposition",
                               "attachment;filename=TransitDataDashboard.json");
            renderJSON(agencies);
        }
    }
    
    /**
     * Get the full agency definition for this agency. Hard on the DB.
     */
    public static void agency (long id) {
        NtdAgency agency = NtdAgency.findById(id);

        response.setHeader("Content-Disposition",
                           "attachment;filename=agency.json");
        renderJSON(new NtdAgencyProxy(agency));
    }
}