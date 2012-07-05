package controllers;

import play.*;
import play.mvc.*;
import models.MetroArea;
import utils.GeometryUtils;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.io.ParseException;

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
}