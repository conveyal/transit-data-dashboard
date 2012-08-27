package controllers;

import models.MetroArea;
import models.NtdAgency;
import play.mvc.Controller;

/**
 * Database migrations
 * 
 * @author mattwigway
 *
 */
public class Migrations extends Controller {
    /**
     * Migrate a database structure from a single metro area per agency to multiple metros per agency
     */
    public static void metroAreaSingleToMany () {
        MetroArea metro;
        
        for (NtdAgency agency : NtdAgency.<NtdAgency>findAll()) {
            metro = agency.metroArea;
            
            if (metro == null)
                continue;
            
            agency.metroArea = null;
            
            metro.agencies.add(agency);
            agency.save();
            metro.save();
        }
        
        renderJSON("{\"status\":\"success\"}");
    }
}
