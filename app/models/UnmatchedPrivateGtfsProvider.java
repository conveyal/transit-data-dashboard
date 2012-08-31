package models;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Query;

import play.db.jpa.JPA;
import play.db.jpa.Model;
import proxies.NtdAgencyProxy;

/**
 * This represents an agency, loaded from Google, that was not automatically matched.
 * @author mattwigway
 *
 */
@Entity
public class UnmatchedPrivateGtfsProvider extends Model {
    /** The metro area that was matched */
    @ManyToOne
    public MetroArea metroArea;
    
    /** Alternately the description of the unmatched metro */
    @ManyToOne
    public UnmatchedMetroArea unmatchedArea;
    
    /** The latitude of this area on Google Transit */
    public double lat;
    
    /** The longitude */
    public double lon;
    
    /** The name of the agency */
    public String name;

    /**
     * Attempt to find the agency this goes with.
     */
    public void search () {
        if (this.metroArea == null)
            return;
        
        // find matching agencies
        String qs = "SELECT a.id FROM ntdagency a " +
                "INNER JOIN metroarea_ntdagency mn ON (a.id = mn.agencies_id) " +
                "INNER JOIN metroarea m ON (m.id = mn.metroarea_id)" +
                "WHERE (to_tsvector(CONCAT(a.name, ' ', " + 
                "regexp_replace(a.url, '\\.|https?://|/|_|\\-', ' ', 'g'))) " +
                "@@ plainto_tsquery(?) " +
                "OR a.url ILIKE CONCAT('%', ?, '%')) " +
                "AND m.id = ?";

        Query q = JPA.em().createNativeQuery(qs);
        q.setParameter(1, this.name);
        q.setParameter(2, this.name);
        q.setParameter(3, this.metroArea.id);
        
        NtdAgency agency;
        boolean hadAtLeastOneAgency = false;
        for (Object result : q.getResultList()) {
            hadAtLeastOneAgency = true;
            agency = NtdAgency.findById(((BigInteger) result).longValue());
            agency.googleGtfs = true;
            agency.save();
        }
        
        if (hadAtLeastOneAgency) {
            this.delete();
        } 
    }
}