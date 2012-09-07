package models;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

import play.db.jpa.Model;

/**
 * A scheduled rebuild indicates the graph should be rebuilt as soon as possible after the member date
 * (generally within 24 hours).
 * 
 * @author mattwigway
 */
@Entity
public class ScheduledRebuild extends Model {
    /** The metro area to rebuild */
    @ManyToOne
    public MetroArea metroArea;
    
    /** When to rebuild it */
    public Date rebuildAfter;
    
    public ScheduledRebuild(MetroArea metroArea, Date rebuildAfter) {
        this.metroArea = metroArea;
        this.rebuildAfter = rebuildAfter;
    }
}
