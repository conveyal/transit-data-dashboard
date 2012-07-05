// modeled on https://github.com/openplans/OpenTripPlanner/blob/master/opentripplanner-utils/src/main/java/org/opentripplanner/common/geometry/GeometryUtils.java

package utils;

import java.util.HashMap;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.ParseException;

public class GeometryUtils {
    private static HashMap<Integer, GeometryFactory> geomFactories = new HashMap<Integer, GeometryFactory>();
    private static HashMap<Integer, WKTReader> wktReaders = new HashMap<Integer, WKTReader>();
    private static PrecisionModel defaultPrecisionModel = new PrecisionModel();

    /**
     * get a geometry factory for a given SRID.
     * @param srid the srid to get a factory for
     */
    public static GeometryFactory getGeometryFactoryForSrid (int srid) {
        Integer wrappedSrid = new Integer(srid);

        if (geomFactories.containsKey(wrappedSrid)) {
            return geomFactories.get(wrappedSrid);
        }
        else {
            GeometryFactory gf = new GeometryFactory(defaultPrecisionModel, srid);
            geomFactories.put(wrappedSrid, gf);
            return gf;
        }
    }

    /**
     * get a WKTReader for a given SRID.
     * @param srid the srid to get a factory for
     */
    public static WKTReader getWktReaderForSrid (int srid) {
        Integer wrappedSrid = new Integer(srid);

        if (wktReaders.containsKey(wrappedSrid)) {
            return wktReaders.get(wrappedSrid);
        }
        else {
            GeometryFactory gf = getGeometryFactoryForSrid(srid);
            WKTReader wktr = new WKTReader(gf);
            wktReaders.put(wrappedSrid, wktr);
            return wktr;
        }
    }

    /**
     * Parse an EWKT geometry and return a JTS Geometry
     * @param ewkt The geometry to parse
     */
    // TODO: check format, audit for security (specifically in WKTReader class)
    public static Geometry parseEwkt (String ewkt) throws ParseException {
        // parse out geometry
        String[] geometrySplit = ewkt.split(";");
        String[] srsSplit = geometrySplit[0].split("=");
        // now srsSplit[1] should be the SRS ID
        int srsId = Integer.parseInt(srsSplit[1]);
        
        WKTReader wktr = getWktReaderForSrid(srsId);

        return wktr.read(geometrySplit[1]);  
    }
}
            