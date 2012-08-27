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

    public MetroArea () {};

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
     * Initialize this metro's agencies. Warning: will erase all agency mappings if any exist.
     */
    public void initializeAgencies() {
        this.agencies = new HashSet<NtdAgency>();
    }
}
    