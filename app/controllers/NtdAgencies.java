package controllers;

import play.*;
import play.mvc.*;
import models.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import proxies.NtdAgencyProxy;

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

        for (NtdAgency agency : NtdAgency.<NtdAgency>findAll()) {
            agencies.add(new NtdAgencyProxy(agency));
        }

        renderJSON(agencies);
    }                      
                        
}