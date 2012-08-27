package models;

import java.util.List;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.OneToOne;

import play.db.jpa.Model;

/**
 * Represents a fare service that should be applied to the graph. They can be chained together.
 * Note that while you can have two initial fare services in different metros point to the same
 * next fare service, such a configuration is not recommended because then changes in the options
 * to one fare service will break the other metro.
 * 
 * @author mattwigway
 */
@Entity
public class FareConfiguration extends Model {
    /**
     * A human-readable comment for this fare service
     */
    public String comment;
    
    /**
     * The type of this fare service.
     */
    public FareServiceType type;
    
    /**
     * The options for this fare service, in XML/Spring IOC format.
     */
    public String springOptions;
    
    /**
     * The next fare service for chained implementations
     */
    @OneToOne
    public FareConfiguration nextFareService;
    
    public String toString() {
        return this.comment;
    }
}
