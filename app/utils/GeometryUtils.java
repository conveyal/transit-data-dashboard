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



// modeled on https://github.com/openplans/OpenTripPlanner/blob/master/opentripplanner-utils/src/main/java/org/opentripplanner/common/geometry/GeometryUtils.java

package utils;

import java.util.HashMap;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.ParseException;

public class GeometryUtils {
    private static HashMap<Integer, GeometryFactory> geomFactories = 
        new HashMap<Integer, GeometryFactory>();
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

    /**
     * Parse a bounding box in the format left,bottom,right,top to a PostGIS BOX(...) construct.
     */
    public static String parseBbox (String bbox) {
        StringBuilder sb = new StringBuilder(32);
        sb.append("BOX(");
        sb.append(bbox);
        sb.append(')');
        return sb.toString();
    }
    
    /**
     * Convert a polygon to a multipolygon
     */
    public static MultiPolygon forceToMultiPolygon (Geometry in) {
        if (in instanceof Polygon) {
            GeometryFactory factory = getGeometryFactoryForSrid(in.getSRID());
            Polygon[] polygons = new Polygon[1];
            polygons[0] = (Polygon) in;
            return factory.createMultiPolygon(polygons);
        }
        else if (in instanceof MultiPolygon)
            return (MultiPolygon) in;
        else
            return null;
                    
    }
}
            