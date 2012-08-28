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
    
    /** Where the data for this agency came from */
    @Enumerated(EnumType.STRING)
    public AgencySource source;

    /** Does this agency provide GTFS to Google? */
    public boolean googleGtfs;
    
    /** The metro areas this agency is part of */
    @ManyToMany
    public Set<MetroArea> metroAreas;

    /** A note for human review */
    public String note;

    /** The metro for this agency. Since agencies and metros now have a many-to-many relationship, this is deprecated */
    @ManyToOne
    @Deprecated
    public MetroArea metroArea;

    /**
     * A list of metro areas that contain this agency.
     */
    public List<MetroArea> getMetroAreas () {
        return MetroArea.find("SELECT m FROM MetroArea m INNER JOIN m.agencies agencies WHERE ? in agencies",
                    this).fetch();  
    }
    
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
        this.source = AgencySource.NTD;
        this.note = null;
        feeds = new TreeSet<GtfsFeed>();
    }

    /**
     * Build an NTD agency based on information in a GTFS feed.
     * @param feed
     */
    public NtdAgency(GtfsFeed feed) {
        this.name = feed.agencyName;
        this.url = feed.agencyUrl;
        this.note = null;
        this.ntdId = null;
        this.population = 0;
        this.ridership = 0;
        this.passengerMiles = 0;
        this.source = AgencySource.GTFS;
        feeds = new TreeSet<GtfsFeed>();
    }      
}
