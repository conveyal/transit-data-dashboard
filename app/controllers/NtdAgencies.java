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
        
        // TODO: google gtfs
        String qs =
            "SELECT a.name, a.url, m.name AS metroname, a.population, a.ridership, " +
            "a.passengerMiles, " + 
            "CASE "+
            "  WHEN f.publicGtfs THEN true " +
            "  ELSE false " +
            "END AS publicGtfs, " +
            "FALSE AS googleGtfs, " +
            "Y(ST_Transform(ST_Centroid(m.the_geom), 4326)) AS lat," +
            "X(ST_Transform(ST_Centroid(m.the_geom), 4326)) AS lon, " +
            "a.id " +
            "FROM (NtdAgency a " +
            "  LEFT JOIN MetroArea m ON (m.id = a.MetroArea_id)) " +
            "  LEFT JOIN (SELECT j.NtdAgency_id, count(*) > 0 AS publicGtfs " + 
            "               FROM NtdAgency_GtfsFeed j " +
            "             GROUP BY j.NtdAgency_id) f ON (a.id = f.NtdAgency_id)";

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
                (Double) result[8], // lat
                (Double) result[9], // lon
                ((BigInteger) result[10]).longValue() // id
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
}