package models;

import java.util.List;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;

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
	 * The type of this bike rental system.
	 * One of KeolisRennes, Static, CityBikes or Bixi
	 * For people parsing deployment requests: static indicates that staticBikeRental should be
	 * true on OpenStreetMapGraphBuilderImpl; the other indicate that the correct class should be
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
}
