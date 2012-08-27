package utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Query;

import models.FeedParseStatus;
import models.GtfsFeed;
import models.MetroArea;
import models.NtdAgency;
import play.db.jpa.JPA;

public class DbUtils {
    public static Set<MetroArea> mapFeedsWithNoAgencies () {
        Set<MetroArea> changedMetros = new HashSet<MetroArea>();
        
        NtdAgency agency;
        for (GtfsFeed feed : GtfsFeed.<GtfsFeed>findAll()) {
            if (feed.getAgencies().size() != 0)
                continue;
            
            if (feed.status != FeedParseStatus.SUCCESSFUL)
                continue;
            
            agency = new NtdAgency(feed);

            // find metro area(s)
            String query = "SELECT m.id FROM MetroArea m WHERE " + 
                    "ST_DWithin(m.the_geom, transform(ST_GeomFromText(?, ?), ST_SRID(m.the_geom)), 0.04)";;
            Query ids = JPA.em().createNativeQuery(query);
            ids.setParameter(1, feed.the_geom.toText());
            ids.setParameter(2, feed.the_geom.getSRID());
            List<BigInteger> metroIds = ids.getResultList();            
            List<MetroArea> metros = new ArrayList<MetroArea>();

            for (BigInteger id : metroIds) {
                metros.add(MetroArea.<MetroArea>findById(id.longValue()));
            }

            // easy case
            if (metros.size() == 1) {
                agency.metroArea = metros.get(0);
                changedMetros.add(metros.get(0));
                agency.save();
            }

            // flag for review
            else {
                feed.disabled = true;
                agency.note = "Too few or too many metro areas.";
            }
        }
        
        return changedMetros;
    }
}
