package models;

/**
 * The type of a FareConfiguration.
 * @author mattwigway
 *
 */
public enum FareServiceType {
    NYC ("org.opentripplanner.routing.impl.NycFareServiceImpl"), 
    SF_BAY_AREA ("org.opentripplanner.routing.impl.SFBayAreaFareServiceImpl"),
    // Used when addition config of default is needed
    DEFAULT ("org.opentripplanner.routing.impl.DefaultFareServiceImpl"),
    TIME_BASED_BIKE_RENTAL ("org.opentripplanner.routing.bike_rental.TimeBasedBikeRentalFareService");
    
    private String classRef;
    
    private FareServiceType(String classRef) {
        this.classRef = classRef;
    }
    
    public String getClassName () {
        return classRef;
    }
}
