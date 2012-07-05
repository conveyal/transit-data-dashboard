package models;

import javax.persistence.*;
import java.util.*;

import play.db.jpa.*;
import play.data.validation.*;

@Entity
public class GtfsFeed extends Model {

    /** The name of this agency, customer-facing */
    @Required
    public String agencyName;
    
    /** The human-readable area description, from Data Exchange, generally */
    public String areaDescription;
    
    /** The country where this agency operates. */
    // TODO: should we use some sort of stable identifier, like internet country code
    public String country;

    /** The GTFS Data Exchange ID */
    public String dataExchangeId;

    /** The GTFS Data Exchange URL */
    @URL
    public String dataExchangeUrl;

    // TODO: on these dates, should they be relative to GTFS Data Exchange or to this site?
    /** The date added to Data Exchange */
    public Date dateAdded;
    
    /** The date this feed was last updated */
    public Date dateUpdated;
    
    /** 
     * The URL where this feed may be found. This may be the feed itself, or may be a
     * developer site (e.g. http://developer.metro.net).
     */
    @URL
    public String feedBaseUrl;

    /** Is this feed a feed officially provided by the transit agency? */
    @Required
    public boolean official;

    /** The URL of the license for this feed */
    @URL
    public String licenseUrl;

    // TODO: this should probably use abbreviations to avoid spelling variations
    // e.g. Hawaii v. Hawai'i
    /** The US state of this feed */
    public String state;

    /** 
     * The URL of the agency producing this feed. This should be the stable URL, not a marketing
     * URL (e.g., http://wmata.com not http://metroopensdoors.com)
     */
    @Required
    @URL
    public String agencyUrl;

    // TODO: time period
    /** The number of trips in this GTFS */
    public int trips;

    /** The expiration date of this feed */
    //@Required
    public Date expirationDate;

    // TODO: what exactly does this mean
    /** The number of trips per calendar, on average */
    public int tripsPerCalendar;

    /**
     * Get the agencies this feed refers to.
     */
    public List<NtdAgency> getAgencies () {
        // modified from http://stackoverflow.com/questions/7898960
        return NtdAgency.find("SELECT a FROM NtdAgency a INNER JOIN a.feeds feeds WHERE ? IN feeds",
                              this)
            .fetch();
    }

    public String toString () {
        return "GTFS for " + agencyUrl;
    }

    public GtfsFeed () {};

    public GtfsFeed (String agencyName, String agencyUrl, String country, 
                     String dataExchangeId, String dataExchangeUrl, Date dateAdded,
                     Date dateUpdated, String feedBaseUrl, String licenseUrl, boolean official,
                     String state, String areaDescription) {
        this.agencyName = agencyName;
        this.areaDescription = areaDescription;
        this.country = country;
        this.dataExchangeId = dataExchangeId;
        this.dataExchangeUrl = dataExchangeUrl;
        this.dateAdded = dateAdded;
        this.dateUpdated = dateUpdated;
        this.feedBaseUrl = feedBaseUrl;
        this.official = official;
        this.licenseUrl = licenseUrl;
        this.state = state;
        this.agencyUrl = agencyUrl;
    }            
}
