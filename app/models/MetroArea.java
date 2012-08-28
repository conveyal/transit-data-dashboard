package models;

import javax.persistence.*;
import java.util.*;

import play.db.jpa.*;
import play.data.validation.*;
import com.vividsolutions.jts.geom.MultiPolygon;
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
            if (agency.uzaNames == null)
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
     * Merge UZA names
     * @param names The names to merge
     * @param maxLength The maximum length of the resulting string
     */
    public static String mergeAreaNames (int maxLength, String... names) {
        String[] thisSplit;
        Set<String> cities = new HashSet<String>();
        Set<String> states = new HashSet<String>();
        StringBuilder out;
    
        for (String name : names) {
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
    
        out = new StringBuilder(maxLength);
    
        if (cities.size() > 0) {
            for (String city : cities) {
                out.append(city);
                out.append('-');
            }
    
            // delete last -
            out.deleteCharAt(out.length() - 1);
            out.append(", ");
        }
    
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
    }
}
    