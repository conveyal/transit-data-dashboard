package controllers;

import play.*;
import play.mvc.*;
import play.db.jpa.JPA;
import models.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.math.BigInteger;
import com.google.gson.Gson;
import proxies.NtdAgencyProxy;
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

    public static void agencies () {
        List<NtdAgencyProxy> agencies = new ArrayList<NtdAgencyProxy>();
        List<Object[]> results;
        NtdAgencyProxy current;
        
        // TODO: google gtfs
        String qs =
            "SELECT a.name, a.url, m.name AS metroname, a.population, a.ridership, " +
            "a.passengerMiles, " +
            "CASE "+
            "  WHEN f.publicGtfs THEN true " +
            "  ELSE false " +
            "END AS publicGtfs, " +
            "FALSE AS googleGtfs, " +
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
                ((BigInteger) result[8]).longValue() // id
                                         );

            agencies.add(current);
        }

        renderJSON(agencies);
    }                      
                        
}