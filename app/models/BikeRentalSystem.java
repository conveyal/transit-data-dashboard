package models;

import java.util.List;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;

import com.google.gson.annotations.Expose;

import play.data.validation.URL;
import play.db.jpa.Model;

@Entity
public class BikeRentalSystem extends Model {
	/**
	 * The metro area this bike rental system is in.
	 */
	@ManyToOne
	public MetroArea metroArea;
	
	/**
	 * The name of this bike rental system.
	 */
	public String name;
	
	/**
	 * The ID in the CityBik.es database.
	 */
	public Integer cityBikesId;
	
    /**
     * Machine readable problem type requiring human review - picked up in the admin interface.
     */
    @Enumerated(EnumType.STRING)
    public ReviewType review;
	
	/**
	 * The type of this bike rental system.
	 * One of KeolisRennes, Static, CityBik.es or Bixi
	 * For people parsing deployment requests: static indicates that staticBikeRental should be
	 * true on OpenStreetMapGraphBuilderImpl; the others indicate that the correct class should be
	 * used: see https://github.com/openplans/OpenTripPlanner/wiki/Bike-Rental
	 */
	@Enumerated(EnumType.STRING)
	public BikeRentalSystemType type;
	
	/**
	 * The URL to grab updates from.
	 */
	@URL
	public String url;
	
	/**
	 * The currency for the fares.
	 */
	public String currency;
	
	/**
	 * The fare classes
	 */
	@ElementCollection
	public List<String> fareClasses; 
	
	public BikeRentalSystem () {
	    this.metroArea = null;
	}
	
	public String toString () {
	    return name + " (" + type.toString() + " system)"; 
	}
}
