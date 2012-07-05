package models;

import javax.persistence.*;
import java.util.*;

import play.db.jpa.*;
import play.data.validation.*;

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
    
    // TODO: geometry

    /**
     * Get a list of agencies for this metro.
     */
    public List<NtdAgency> getAgencies () {
        return NtdAgency.find("byMetroArea", this).fetch();
    }

    /**
     * Make a new metro area
     */
    public MetroArea (String name) {
        this.name = name;
    }

    public MetroArea () {};

    /**
     * Return the string used in the admin interface
     */
    public String toString () {
        List<NtdAgency> agencies = getAgencies();
        if (name != null && !name.equals("")) {
            return name;
        }
        else if (agencies.size() != 0) {
            if (agencies.get(0).name != null && !agencies.get(0).name.equals("")) {
                return "Metro including " + agencies.get(0).name;
            }
            else {
                return "Metro including " + agencies.get(0).url;
            }
        }
        else {
            return "Empty metro area";
        }
    }
}
    