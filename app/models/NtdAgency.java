package models;

import javax.persistence.*;
import java.util.*;

import play.db.jpa.*;
import play.data.validation.*;

@Entity
public class NtdAgency extends Model {
    
    /** Human-readable agency name */
    @Required
    public String name;

    /** This agency's primary location on the WWW */
    @Required
    @URL
    public String website;

    /** This agency's ID in the National Transit Database */
    public String ntdId;

    /** Service area population */
    public int population;

    /** Annual unlinked passenger trips */
    public int ridership;
    
    /** Annual passenger miles */
    public int passengerMiles;

    // TODO: geometry

    @ManyToOne
    public MetroArea metroArea;

    @ManyToMany(mappedBy="agencies")
    public Set<GtfsFeed> feeds;

    /** 
     * Convert to a human-readable string. This is exposed in the admin interface, so it should be
     * correct.
     */
    public String toString () {
        return name;
    }
}
