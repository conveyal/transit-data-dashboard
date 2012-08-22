package models;

/**
 * Defines the type of a bike rental system
 * @author matthewc
 *
 */
public enum BikeRentalSystemType {	
	KEOLIS_RENNES ("Keolis Rennes"),
	BIXI ("Bixi"),
	CITYBIKES ("CityBik.es"),
	STATIC ("Static OSM");
	
	private String type;
	
	private BikeRentalSystemType (String type) {
		this.type = type;
	}
}
