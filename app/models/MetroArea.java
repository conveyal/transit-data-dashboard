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

    // TODO: cascade?
    @OneToMany(mappedBy="metroArea")
    public List<NtdAgency> ntdAgencies;

    /**
     * Return the string used in the admin interface
     */
    public String toString () {
        if (name != null && !name.equals("")) {
            return name;
        }
        else if (ntdAgencies.size() != 0) {
            return "Metro including " + ntdAgencies.get(0).name;
        }
        else {
            return "Empty metro area";
        }
    }
}
    