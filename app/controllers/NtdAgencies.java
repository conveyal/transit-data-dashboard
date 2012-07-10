package controllers;

import play.*;
import play.mvc.*;
import models.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

    public static void agencies () {
        /*        List<Map<String, Object>> agencies = new ArrayList<Map<String, Object>>();
        Map<String, Object> current;

        for (NtdAgency agency : NtdAgency.<NtdAgency>findAll()) {
            current = new HashMap<String, Object>();
            current.put("name", agency.name);
            current.put("url", agency.url);
            current.put("population", agency.population);
            current.put("ridership", agency.ridership);
            current.put("passengerMiles", agency.passengerMiles);

            if (agency.feeds.size() > 0)
                current.put("publicGtfs", true);
            else
                current.put("publicGtfs", false);
                
        }

        Gson gson = new Gson();
*/

        renderJSON(NtdAgency.<NtdAgency>findAll());
    }                      
                        
}