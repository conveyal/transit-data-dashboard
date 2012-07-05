package controllers;

import play.*;
import play.mvc.*;
import play.db.jpa.JPA;
import models.MetroArea;
import utils.GeometryUtils;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.io.ParseException;
import javax.persistence.Query;

public class MetroAreas extends Controller {
    /**
     * Create a new Metro Area
     * @param name The name of this metro area
     * @param geometry The EWKT geometry of this metro area
     */
    public static void create (String name, String geometry) {
        //String name = params.get("name");
        //String geometry = params.get("geometry");
        MultiPolygon the_geom;

        try {
            the_geom = (MultiPolygon) GeometryUtils.parseEwkt(geometry);
        } catch (ParseException e) {
            return;
        }

        new MetroArea(name, the_geom).save();
    }

    /**
     * Return a MetroArea as GeoJSON
     */
    public static void get (int id) {
        // max decimal places 6 (per demory), include GeoJSON short CRS (2)
        // TODO: is FROM MetroArea the right thing to do here?
        String query = "SELECT m.name, ST_AsGeoJSON(m.the_geom, 6, 2) AS geom " +
            "FROM MetroArea m WHERE m.id = ?";
        Query q = JPA.em().createNativeQuery(query);
        q.setParameter(1, id);
        Object[] result = (Object[]) q.getResultList().get(0);

        // TODO: writing JSON by hand is ugly
        String output = "{\"properties\": {\"name\": \"" + (String) result[0] + "\"}," +
            "\"geometry\":" + (String) result[1] + "," + 
            "\"type\": \"Feature\"}";
        
        renderJSON(output);
    }
}