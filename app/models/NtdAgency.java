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
    public String url;

    /** 
     * This agency's ID in the National Transit Database. Stored as string to preserve
     * leading zeros.
     */
    public String ntdId;

    /** This agency's UZA name(s) in the National Transit Database */
    @ElementCollection
    public List<String> uzaNames;

    /** Service area population */
    public int population;

    /** Annual unlinked passenger trips */
    public int ridership;
    
    /** Annual passenger miles */
    public int passengerMiles;

    /** Does this agency provide GTFS to Google? */
    public boolean googleGtfs;

    // TODO: geometry

    @ManyToOne
    public MetroArea metroArea;

    @ManyToMany(cascade=CascadeType.PERSIST)
    public Set<GtfsFeed> feeds;

    /** 
     * Convert to a human-readable string. This is exposed in the admin interface, so it should be
     * correct.
     */
    public String toString () {
        if (name != null && !name.equals(""))
            return name;
        else
            return url;
    }

    // TODO: argumented constructors
    public NtdAgency () {
        feeds = new TreeSet<GtfsFeed>();
    }

    public NtdAgency (String name, String url, String ntdId, int population,
                      List<String> uzaNames, int ridership, int passengerMiles) {
        this.name = name;
        this.url = url;
        this.ntdId = ntdId;
        this.population = population;
        this.uzaNames = uzaNames;
        this.ridership = ridership;
        this.passengerMiles = passengerMiles;
        feeds = new TreeSet<GtfsFeed>();
    }      
}
