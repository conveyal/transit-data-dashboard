package models;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import play.db.jpa.Model;

/**
 * This represents an agency, loaded from Google, that was not automatically matched.
 * @author matthewc
 *
 */
@Entity
public class UnmatchedPrivateGtfsProvider extends Model {
    /** The metro area that was matched */
    @ManyToOne
    public MetroArea metroArea;
    
    /** The latitude of this area on Google Transit */
    public double lat;
    
    /** The longitude */
    public double lon;
    
    /** The name of the agency */
    public String name;
}