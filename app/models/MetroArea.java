package models;

import javax.persistence.*;
import java.util.*;

import play.db.jpa.*;
import play.data.validation.*;
import utils.GeometryUtils;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.overlay.OverlayOp;

import org.hibernate.annotations.Type;

@Entity
public class MetroArea extends Model {

    /** 
     * The name of this Metro area. Should take the form
     * City 1-City 2-City 3, State 1-State 2-State 3
     */
    public String name;
    // note that this ID is not serial, because we don't want
    // to have trouble if we add more GTFS to an urban area; they
    // id should stay the same.
    /** The ID of this area */
    /*@Required
    @Unique
    @Id
    public int id;*/
    
    @Type(type = "org.hibernatespatial.GeometryUserType")
    public MultiPolygon the_geom;

    /**
     * The fare service (or first fare service, in the chained case) for this metro.
     */
    public FareConfiguration fareConfiguration;
    
    /**
     * The agencies in this metro
     */
    @ManyToMany
    public Set<NtdAgency> agencies;
    
    /**
     * Is this metro disabled?
     */
    public boolean disabled;
    
    /**
     * Human readable note.
     */
    public String note;
    
    /**
     * Machine readable problem type requiring human review - picked up in the admin interface.
     */
    @Enumerated(EnumType.STRING)
    public ReviewType review;
    
    /**
     * The source of this metro area
     */
    @Enumerated(EnumType.STRING)
    public MetroAreaSource source;
    
    /**
     * Needed from old DB structure; still used in some code.
     */
    @Deprecated
    public Set<NtdAgency> getAgencies() {
        return agencies;
    }
    
    /**
     * Get a list of bike rental systems in this metro.
     */
    public List<BikeRentalSystem> getBikeRentalSystems () {
        return BikeRentalSystem.find("byMetroArea", this).fetch();
    }

    /**
     * Make a new metro area
     */
    public MetroArea (String name, MultiPolygon the_geom) {
        this.name = name;
        this.the_geom = the_geom;
        this.initializeAgencies();
    }

    public MetroArea () {
        this(null, null);
    };

    /**
     * Autoname this metro area.
     */
    public void autoname () {
        NtdAgency largestAgency = null;
        
        for (NtdAgency agency : agencies) {
            if (agency.uzaNames == null || agency.uzaNames.size() == 0)
                continue;
                
            // if there's one agency, it's the largest
            if (largestAgency == null) {
                largestAgency = agency;
                continue;
            }

            if (agency.population > largestAgency.population) {
                largestAgency = agency;
            }
        }
        
        if (largestAgency != null) {
            String[] uzaNames = new String[largestAgency.uzaNames.size()];
            largestAgency.uzaNames.toArray(uzaNames);
            this.name = mergeAreaNames(255, uzaNames);
        }
        else {
            Set<String> cities = new HashSet<String>();
            Set<String> states = new HashSet<String>();
            
            // go through agencies looking for data exchange information
            for (NtdAgency agency : agencies) {
                for (GtfsFeed feed : agency.feeds) {
                    if (feed.areaDescription != null && !feed.areaDescription.equals("")) {
                        cities.add(feed.areaDescription);
                    }
                    if (feed.state != null && !feed.state.equals("")) {
                        states.add(feed.state);
                    }
                }
            }
            
            this.name = mergeAreaNames(255, cities, states);
        }
    }
    
    /**
     * Return the string used in the admin interface
     */
    public String toString () {
        if (name != null && !name.equals("")) {
            return name;
        }
        else if (agencies.size() != 0) {
            return "Metro including " + agencies.size() + " agencies.";
        }
        else {
            return "Empty metro area";
        }
    }

    /**
     * Initialize this metro's agencies. Warning: will erase all agency mappings.
     */
    public void initializeAgencies() {
        this.agencies = new HashSet<NtdAgency>();
    }

    /**
     * Merge two metro areas.
     */
    public void mergeAreas (MetroArea other) {
        // we make a multipolygon with one member for the geometry
        Polygon[] polygons;
        Geometry result;
        GeometryFactory factory;
    
        for (NtdAgency agency : other.agencies) {
            this.agencies.add(agency);;
        }
        
        // erase all other's agencies
        other.initializeAgencies();
    
        // now, combine geometries
        
        result = OverlayOp.overlayOp(this.the_geom, other.the_geom, OverlayOp.UNION);
        
        // sometimes it's a polygon, not sure why
        if (result instanceof Polygon) {
            // TODO: assumptions about SRID of other here
            factory = GeometryUtils.getGeometryFactoryForSrid(this.the_geom.getSRID());
            polygons = new Polygon[1];
            polygons[0] = (Polygon) result;
            result = factory.createMultiPolygon(polygons);
        }
        
        // somewhere this gets lost
        result.setSRID(this.the_geom.getSRID());
    
        this.the_geom = (MultiPolygon) result;
        
        this.name = mergeAreaNames(255, this.name, other.name);
    }

    /**
     * Merge UZA names
     * @param names The names to merge
     * @param maxLength The maximum length of the resulting string
     */
    public static String mergeAreaNames (int maxLength, String... names) {
        String[] thisSplit;
        Set<String> cities = new HashSet<String>();
        Set<String> states = new HashSet<String>();

        try {
            for (String name : names) {
                if (name == null)
                    continue;

                thisSplit = name.split(", ");

                for (String city : thisSplit[0].split("-")) {
                    cities.add(city);
                }

                if (thisSplit.length >= 2) {
                    for (String state : thisSplit[1].split("-")) {
                        states.add(state);
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        
        return mergeAreaNames(maxLength, cities, states);
    }
    
    public static String mergeAreaNames(int maxLength, Set<String> cities, Set<String> states) {
        try {
            StringBuilder out;
        
            out = new StringBuilder(maxLength);

            if (cities.size() > 0) {
                for (String city : cities) {
                    out.append(city);
                    out.append('-');
                }

                // delete last -
                out.deleteCharAt(out.length() - 1);
            }
            
            if (cities.size() > 0 && states.size() > 0)
                out.append(", ");

            if (states.size() > 0) {
                for (String state : states) {
                    out.append(state);
                    out.append('-');
                }

                out.deleteCharAt(out.length() - 1);
            }

            // truncate if needed
            if (out.length() >= maxLength)
                out.setLength(maxLength);

            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
    