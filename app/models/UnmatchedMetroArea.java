package models;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Query;

import play.db.jpa.JPA;
import play.db.jpa.Model;

@Entity
public class UnmatchedMetroArea extends Model {
    /** The name of this metro */
    public String name;
    
    /** The latitude of this metro (WGS 1984) */
    public double lat;
    
    /** The longitude of this metro (WGS 1984) */
    public double lon;
    
    /**
     * Get all of the providers that have this metro location.
     */
    public List<UnmatchedPrivateGtfsProvider> getProviders () {
        return UnmatchedPrivateGtfsProvider.find("byUnmatchedArea", this).fetch();
    }
    
    /**
     * Get all of the metros which potentially match this unmatched area.
     */
    public List<MetroArea> getPotentialMetroAreas () {
        String qs = "SELECT id FROM MetroArea m WHERE " +
                // 1 degree radius
                "ST_DWithin(the_geom, ST_SetSRID(ST_Point(?, ?), 4326), 1)";
        Query q = JPA.em().createNativeQuery(qs);
        q.setParameter(1, this.lon);
        q.setParameter(2, this.lat);
        List<BigInteger> metroIds = q.getResultList();
        List<MetroArea> metros = new ArrayList<MetroArea>();
        
        MetroArea toAdd;
        for (BigInteger id : metroIds) {
            toAdd = MetroArea.findById(id.longValue());
            if (toAdd != null) {
                metros.add(toAdd);
            }
        }
        
        return metros;
    }
}
