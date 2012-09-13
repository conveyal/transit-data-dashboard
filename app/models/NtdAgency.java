/* 
  This program is free software: you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public License
  as published by the Free Software Foundation, either version 3 of
  the License, or (props, at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program. If not, see <http://www.gnu.org/licenses/>. 
*/

package models;

import javax.persistence.*;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.operation.overlay.OverlayOp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import play.db.jpa.*;
import play.data.validation.*;
import utils.GeometryUtils;

@Entity
public class NtdAgency extends Model {
    
    /** Human-readable agency name */
    @Required
    public String name;

    /** This agency's primary location on the WWW */
    @Required
    @URL
    public String url;

    /** 
     * This agency's ID in the National Transit Database. Stored as string to preserve
     * leading zeros.
     */
    public String ntdId;

    /** This agency's UZA name(s) in the National Transit Database */
    @ElementCollection
    public List<String> uzaNames;

    /** Service area population */
    public int population;

    /** Annual unlinked passenger trips */
    public int ridership;
    
    /** Annual passenger miles */
    public int passengerMiles;
    
    /** Where the data for this agency came from */
    @Enumerated(EnumType.STRING)
    public AgencySource source;
    
    /**
     * Machine readable problem type requiring human review - picked up in the admin interface.
     */
    @Enumerated(EnumType.STRING)
    public ReviewType review;

    /** Does this agency provide GTFS to Google? */
    public boolean googleGtfs;

    /** A note for human review */
    public String note;

    /** The metro for this agency. Since agencies and metros now have a many-to-many relationship, this is deprecated */
    @ManyToOne
    @Deprecated
    public MetroArea metroArea;

    /**
     * A list of metro areas that contain this agency.
     */
    public List<MetroArea> getMetroAreas () {
        return MetroArea.find("SELECT m FROM MetroArea m INNER JOIN m.agencies agencies WHERE ? in agencies",
                    this).fetch();  
    }
    
    @ManyToMany(cascade=CascadeType.PERSIST)
    public Set<GtfsFeed> feeds;

    /**
     * Is this agency disabled?
     */
    public boolean disabled;

    /** 
     * Convert to a human-readable string. This is exposed in the admin interface, so it should be
     * correct.
     */
    public String toString () {
        if (name != null && !name.equals(""))
            return name;
        else
            return url;
    }

    // TODO: argumented constructors
    public NtdAgency () {
        this(null, null, null, 0, null, 0, 0);
    }

    public NtdAgency (String name, String url, String ntdId, int population,
                      List<String> uzaNames, int ridership, int passengerMiles) {
        this.name = name;
        this.url = url;
        this.ntdId = ntdId;
        this.population = population;
        this.uzaNames = uzaNames;
        this.ridership = ridership;
        this.passengerMiles = passengerMiles;
        this.source = AgencySource.NTD;
        this.note = null;
        this.disabled = false;
        feeds = new HashSet<GtfsFeed>();
    }

    /**
     * Build an NTD agency based on information in a GTFS feed.
     * @param feed
     */
    public NtdAgency(GtfsFeed feed) {
        this.name = feed.agencyName;
        this.url = feed.agencyUrl;
        this.note = null;
        this.ntdId = null;
        this.population = 0;
        this.ridership = 0;
        this.passengerMiles = 0;
        this.disabled = feed.disabled;
        this.source = AgencySource.GTFS;
        feeds = new HashSet<GtfsFeed>();
    }

    public NtdAgency(UnmatchedPrivateGtfsProvider privateProvider) {
        this.name = privateProvider.name;
        this.url = null;
        this.note = null;
        this.ntdId = null;
        this.population = 0;
        this.ridership = 0;
        this.passengerMiles = 0;
        this.disabled = false;
        this.googleGtfs = true;
        
        feeds = new HashSet<GtfsFeed>();
        
        if (privateProvider.metroArea == null)
            this.review = ReviewType.NO_METRO;
        
        // This is a bit odd, but required
        this.save();
        
        if (privateProvider.metroArea != null) {
            privateProvider.metroArea.agencies.add(this);
            privateProvider.metroArea.save();
        }
        
    }

    public Geometry getGeom() {
        Geometry out = null;
        Integer srid = null;
        
        for (GtfsFeed feed : feeds) {
            // ignore feeds that did not parse as they will have null geoms
            if (feed.status != FeedParseStatus.SUCCESSFUL)
                continue;
            
            if (srid == null)
                srid = feed.the_geom.getSRID();
            
            if (out == null)
                out = feed.the_geom;
            else
                out = OverlayOp.overlayOp(out, feed.the_geom, OverlayOp.UNION);
        }
        
        // re-set SRID, it gets lost
        if (out != null)
            out.setSRID(srid);
        
        return out;
    }

    /**
     * Make this agency a member of every metro it overlaps, without merging anything.
     */
    public void splitToAreas() {
        Geometry agencyGeom = this.getGeom();

        String query = "SELECT m.id FROM MetroArea m WHERE " + 
                "ST_DWithin(m.the_geom, transform(ST_GeomFromText(?, ?), ST_SRID(m.the_geom)), 0.04)";;
        Query ids = JPA.em().createNativeQuery(query);
        ids.setParameter(1, agencyGeom.toText());
        ids.setParameter(2, agencyGeom.getSRID());
        List<BigInteger> metrosTemp = ids.getResultList();
        
        MetroArea metro;
        for (BigInteger metroId : metrosTemp) {
            metro = MetroArea.findById(metroId.longValue());
            metro.agencies.add(this);
            metro.save();
        }
    }

    /**
     * Merge all the areas this agency is potentially a part of. Beware this will create huge agencies if
     * it is applied to (a) something like Amtrak or Greyhound or (b) an agency with a few misplaced stops
     * far away in other metros; since geoms are convex-hulled, they will cross lots of areas.
     */
    public void mergeAllAreas() {
        Geometry agencyGeom = this.getGeom();

        String query = "SELECT m.id FROM MetroArea m WHERE " + 
                "ST_DWithin(m.the_geom, transform(ST_GeomFromText(?, ?), ST_SRID(m.the_geom)), 0.04)";;
        Query ids = JPA.em().createNativeQuery(query);
        ids.setParameter(1, agencyGeom.toText());
        ids.setParameter(2, agencyGeom.getSRID());
        List<BigInteger> metrosTemp = ids.getResultList();
        
        MetroArea metro;
        MetroArea first = MetroArea.findById(metrosTemp.get(0).longValue());
        metrosTemp.remove(0);
        
        for (BigInteger metroId : metrosTemp) {
            metro = MetroArea.findById(metroId.longValue());
            first.mergeAreas(metro);
            metro.delete();
        }
        
        first.agencies.add(this);
        first.save();
    }
    
    /**
     * Find the metro area this agency belongs in and assign to that metro area. There are several
     * things this may do:
     * - if there is exactly one metro area that this agency touches, this agency is assigned to
     *   that metro area
     * - if there is more than one metro area, but only one has transit, all of the metro areas
     *   are merged
     * - if there are multiple transit-containing metros, the agency is flagged for review (nothing
     *   is done automatically, to prevent, say, an Amtrak feed from merging New York, Chicago, 
     *   San Francisco, Los Angeles and intermediate points into one metro)
     * - if there are no matched metros whatsoever, a new one is created.
     * 
     * When this function completes, everything has been saved.
     * 
     * @return the metro areas that were changed in this process, which may be none.
     */
    public Set<MetroArea> findAndAssignMetroArea () {
        Set<MetroArea> changedMetros = new HashSet<MetroArea>();
        
        // find metro area(s)
        String query = "SELECT m.id FROM MetroArea m WHERE " + 
                "ST_DWithin(m.the_geom, transform(ST_GeomFromText(?, ?), ST_SRID(m.the_geom)), 0.04)";
        Query ids = JPA.em().createNativeQuery(query);
        Geometry geom = this.getGeom();
        
        if (geom == null) {
            if (this.getMetroAreas().size() == 0)
                this.review = ReviewType.NO_METRO;
            
            this.save();
            return changedMetros;
        }
        
        ids.setParameter(1, geom.toText());
        ids.setParameter(2, geom.getSRID());
        List<BigInteger> metroIds = ids.getResultList();            
        List<MetroArea> metros = new ArrayList<MetroArea>();
        MetroArea metro;

        for (BigInteger id : metroIds) {
            metros.add(MetroArea.<MetroArea>findById(id.longValue()));
        }

        // easy case
        if (metros.size() == 1) {
            metro = metros.get(0);
            this.note = "Found single metro.";
            this.save();
            metro.agencies.add(this);
            metro.save();
            changedMetros.add(metro);
        }

        else if (metros.size() > 1) {
            MetroArea metroWithTransit = null;
            boolean isSingleMetroWithTransit = true;
            for (MetroArea m : metros) {
                if (m.agencies.size() > 0) {
                    if (metroWithTransit != null) {
                        isSingleMetroWithTransit = false;
                        break;
                    }
                    else {
                        metroWithTransit = m;
                    }
                }
            }
            
            // merge into the metro with transit, or the first metro if none have transit
            if (isSingleMetroWithTransit) {
                if (metroWithTransit == null) {
                    metroWithTransit = metros.get(0);
                }
                
                for (MetroArea m : metros) {
                    if (m == metroWithTransit)
                        continue;
                    
                    metroWithTransit.mergeAreas(m);
                    m.disabled = true;
                    m.save();
                    changedMetros.add(m);
                }
                
                this.note = "Merged several non-transit metros.";
                this.save();
                metroWithTransit.agencies.add(this);                    
                metroWithTransit.save();
                changedMetros.add(metroWithTransit);
            }
            else {
                this.note = "Too many metro areas";
                this.review = ReviewType.AGENCY_MULTIPLE_AREAS;
                this.save();
            }
        }
        
        // no metro areas found: create one
        else {
            this.note = "No metro areas found, created from feed geometry.";
 
            MetroArea area = new MetroArea(null, null);
            area.source = MetroAreaSource.GTFS;
            area.agencies.add(this);
            area.autogeom();
            area.autoname();
            this.save();
            area.save();
            changedMetros.add(area);
        }
        
        return changedMetros;
    }
     
}
