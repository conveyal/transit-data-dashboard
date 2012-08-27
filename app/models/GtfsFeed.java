package models;

import javax.persistence.*;
import java.util.*;

import play.Logger;
import play.db.jpa.*;
import play.data.validation.*;
import com.vividsolutions.jts.geom.MultiPolygon;
import org.hibernate.annotations.Type;

@Entity
public class GtfsFeed extends Model implements Cloneable {

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
    
    /**
     * This is the URL to download this feed. Generally it's a GTFS Data Exchange
     * file URL.
     */
    @URL
    public String downloadUrl;
    
    /**
     * The URL of the GTFS Realtime feed for this feed, if available.
     */
    @URL
    public String realtimeUrl;
    
    /**
     * Does this feed allow bikes on transit with unspecified bike rules in GTFS?
     */
    @Enumerated(EnumType.STRING)
    public DefaultBikesAllowedType defaultBikesAllowed;
    
    /**
     * Was the parsing of this feed successful?
     */
    @Enumerated(EnumType.STRING)
    public FeedParseStatus status;

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

    @Type(type = "org.hibernatespatial.GeometryUserType")
    public MultiPolygon the_geom;

    /**
     * Was this feed superseded?
     */
    @OneToOne
	public GtfsFeed supersededBy;

    /**
     * The ID of this file in storage. Exact contents is storage backend dependent.
     */
	public String storedId;

	/**
	 * The date this schedule becomes valid.
	 */
	public Date startDate;

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
        return "GTFS for " + agencyName;
    }

    public GtfsFeed () {};

    public GtfsFeed (String agencyName, String agencyUrl, String country, 
                     String dataExchangeId, String dataExchangeUrl, Date dateAdded, 
                     Date dateUpdated, Date expirationDate, String feedBaseUrl, String licenseUrl, 
                     boolean official, String state, String areaDescription, 
                     MultiPolygon the_geom) {
        this.agencyName = agencyName;
        this.areaDescription = areaDescription;
        this.country = country;
        this.dataExchangeId = dataExchangeId;
        this.dataExchangeUrl = dataExchangeUrl;
        this.dateAdded = dateAdded;
        this.dateUpdated = dateUpdated;
        this.expirationDate = expirationDate;
        this.feedBaseUrl = feedBaseUrl;
        this.official = official;
        this.licenseUrl = licenseUrl;
        this.state = state;
        this.agencyUrl = agencyUrl;
        this.the_geom = the_geom;
        this.supersededBy = null;
        this.storedId = null;
        this.downloadUrl = null;
        this.defaultBikesAllowed = DefaultBikesAllowedType.WARN;
        this.realtimeUrl = null;
        this.status = null;
    }
    
    public GtfsFeed clone () {
    	// can't just clone, because of IDs &c; JPA gives us the 'detached entity passed to persist'
    	GtfsFeed ret = new GtfsFeed();
    	ret.agencyName = this.agencyName;
    	ret.agencyUrl = this.agencyUrl;
    	ret.areaDescription = this.areaDescription;
    	ret.country = this.country;
    	ret.dataExchangeId = this.dataExchangeId;
    	ret.dataExchangeUrl = this.dataExchangeUrl;
    	ret.dateAdded = this.dateAdded;
    	ret.dateUpdated = this.dateUpdated;
    	ret.downloadUrl = this.downloadUrl;
    	ret.expirationDate = this.expirationDate;
    	ret.feedBaseUrl = this.feedBaseUrl;
    	ret.licenseUrl = this.licenseUrl;
    	ret.official = this.official;
    	ret.state = this.state;
    	ret.supersededBy = this.supersededBy;
    	ret.the_geom = this.the_geom;
    	ret.trips = this.trips;
    	ret.tripsPerCalendar = this.tripsPerCalendar;
    	ret.storedId = this.storedId;
    	ret.realtimeUrl = this.realtimeUrl;
    	ret.defaultBikesAllowed = this.defaultBikesAllowed;
    	// this should always be overwritten
    	ret.status = this.status;
    	
    	// add it to agencies as appropriate
    	for (NtdAgency agency : getAgencies()) {
    		agency.feeds.add(ret);
    		agency.save();
    	}    	
    	
    	return ret;
    			
    }
}
