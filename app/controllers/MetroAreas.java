package controllers;

import play.*;
import play.mvc.*;
import play.db.jpa.JPA;
import models.MetroArea;
import utils.GeometryUtils;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.io.ParseException;
import javax.persistence.Query;
import java.util.List;
import java.util.Date;

public class MetroAreas extends Controller {
    // TODO: writing JSON by hand is ugly
    private static String jsonFeature = "{\"properties\": {\"name\": \"%s\"}," +
            "\"geometry\": %s," + 
            "\"type\": \"Feature\"}";

    /**
     * Create a new Metro Area
     * @param name The name of this metro area
     * @param geometry The EWKT geometry of this metro area
     */
    public static void create (String name, String geometry) {
        //String name = params.get("name");
        //String geometry = params.get("geometry");
        MultiPolygon the_geom = null;
	Geometry tempGeom;

        try {
	    tempGeom = GeometryUtils.parseEwkt(geometry);
        } catch (ParseException e) {
            return;
        }

	if (tempGeom instanceof MultiPolygon) {
	    the_geom = (MultiPolygon) tempGeom;
	}

	else if (tempGeom instanceof Polygon) {
	    Polygon[] geoms = new Polygon[] {(Polygon) tempGeom};
	    GeometryFactory gf = GeometryUtils.getGeometryFactoryForSrid(tempGeom.getSRID());
	    the_geom = new MultiPolygon(geoms, gf);
	}

        new MetroArea(name, the_geom).save();
    }

    private static String getMetroAsJson (long id) {
        // max decimal places 6 (per demory), include GeoJSON short CRS (2)
        // TODO: is FROM MetroArea the right thing to do here?
        String query = "SELECT m.name, ST_AsGeoJSON(m.the_geom, 6, 2) AS geom " +
            "FROM MetroArea m WHERE m.id = ?";
        Query q = JPA.em().createNativeQuery(query);
        q.setParameter(1, id);
        Object[] result = (Object[]) q.getResultList().get(0);

        return String.format(jsonFeature, (String) result[0], (String) result[1]);
    }

    /**
     * Return a MetroArea as GeoJSON
     */
    public static void get (long id) {
        renderJSON(getMetroAsJson(id));
    }

    /**
     * Return a list of all metro areas
     */
    public static void getAll () {
        // TODO: stringbuilder
        String output = "{\"type\": \"FeatureCollection\", \"features\": [";
        List<MetroArea> areas = MetroArea.findAll();
        boolean first = true;

        String query = "SELECT m.name, ST_AsGeoJSON(m.the_geom, 6, 2) AS geom " +
            "FROM MetroArea m";
        Query q = JPA.em().createNativeQuery(query);
        List<Object[]> results = q.getResultList();

        for (Object[] result : results) {
            if (!first) {
                // TODO: super slow
                output += "," +  String.format(jsonFeature, (String) result[0], (String) result[1]);
            }
            else {
                first = false;
                output +=  String.format(jsonFeature, (String) result[0], (String) result[1]);
            }   
        }
        output += "]}";
        renderJSON(output);
    }
}