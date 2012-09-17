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
import java.util.*;
import java.math.BigInteger;

import play.Play;
import play.db.jpa.*;
import play.data.validation.*;
import utils.GeometryUtils;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.overlay.OverlayOp;

import deployment.DeploymentPlan;

import org.hibernate.annotations.Type;

@Entity
public class MetroArea extends Model {

    /** 
     * The name of this Metro area. Should take the form
     * City 1-City 2-City 3, State 1-State 2-State 3
     */
    public String name;
    
    @Type(type = "org.hibernatespatial.GeometryUserType")
    public MultiPolygon the_geom;

    /**
     * The fare service (or first fare service, in the chained case) for this metro.
     */
    public FareConfiguration fareConfiguration;
    
    /**
     * The agencies in this metro
     */
    @ManyToMany
    public Set<NtdAgency> agencies;
    
    /**
     * Is this metro disabled?
     */
    public boolean disabled;
    
    /**
     * Have updates been made to this metro that would necessitate a graph rebuild?
     */
    public Boolean needsUpdate;
    
    /**
     * Human readable note.
     */
    public String note;
    
    /**
     * Machine readable problem type requiring human review - picked up in the admin interface.
     */
    @Enumerated(EnumType.STRING)
    public ReviewType review;
    
    /**
     * The source of this metro area
     */
    @Enumerated(EnumType.STRING)
    public MetroAreaSource source;
    
    /**
     * Needed from old DB structure; still used in some code.
     */
    @Deprecated
    public Set<NtdAgency> getAgencies() {
        return agencies;
    }
    
    /**
     * Get a list of bike rental systems in this metro.
     */
    public List<BikeRentalSystem> getBikeRentalSystems () {
        return BikeRentalSystem.find("byMetroArea", this).fetch();
    }

    /**
     * Make a new metro area
     */
    public MetroArea (String name, MultiPolygon the_geom) {
        this.name = name;
        this.the_geom = the_geom;
        this.initializeAgencies();
    }

    public MetroArea () {
        this(null, null);
    };

    /**
     * Autoname this metro area.
     */
    public void autoname () {
        NtdAgency largestAgency = null;
        
        for (NtdAgency agency : agencies) {
            if (agency.uzaNames == null || agency.uzaNames.size() == 0)
                continue;
                
            // if there's one agency, it's the largest
            if (largestAgency == null) {
                largestAgency = agency;
                continue;
            }

            if (agency.population > largestAgency.population) {
                largestAgency = agency;
            }
        }
        
        if (largestAgency != null) {
            String[] uzaNames = new String[largestAgency.uzaNames.size()];
            largestAgency.uzaNames.toArray(uzaNames);
            this.name = mergeAreaNames(255, uzaNames);
        }
        else {
            Set<String> cities = new HashSet<String>();
            Set<String> states = new HashSet<String>();
            
            // go through agencies looking for data exchange information
            for (NtdAgency agency : agencies) {
                for (GtfsFeed feed : agency.feeds) {
                    if (feed.areaDescription != null && !feed.areaDescription.equals("")) {
                        cities.add(feed.areaDescription);
                    }
                    if (feed.state != null && !feed.state.equals("")) {
                        states.add(feed.state);
                    }
                }
            }
            
            String name = mergeAreaNames(255, cities, states);
            if (name != null && !name.equals("") && name.length() >= 4)
                this.name = name; 
        }
    }
    
    /**
     * Return the string used in the admin interface
     */
    public String toString () {
        if (name != null && !name.equals("")) {
            return (this.disabled ? "disabled " : "") + name + "(" + agencies.size() + " agencies)";
        }
        else if (agencies.size() != 0) {
            return "Metro including " + agencies.size() + " agencies.";
        }
        else {
            return "Empty metro area";
        }
    }

    /**
     * Initialize this metro's agencies. Warning: will erase all agency mappings.
     */
    public void initializeAgencies() {
        this.agencies = new HashSet<NtdAgency>();
    }

    /**
     * Merge two metro areas.
     */
    public void mergeAreas (MetroArea other) {
        Geometry result;
    
        for (NtdAgency agency : other.agencies) {
            this.agencies.add(agency);;
        }
        
        // erase all other's agencies
        other.initializeAgencies();
    
        // now, combine geometries
        
        result = OverlayOp.overlayOp(this.the_geom, other.the_geom, OverlayOp.UNION);
        
        // somewhere this gets lost
        result.setSRID(this.the_geom.getSRID());
        
        // sometimes it's a polygon, not sure why
        result = GeometryUtils.forceToMultiPolygon(result);
    
        this.the_geom = (MultiPolygon) result;
        
        this.name = mergeAreaNames(255, this.name, other.name);
    }

    /**
     * Merge UZA names
     * @param names The names to merge
     * @param maxLength The maximum length of the resulting string
     */
    public static String mergeAreaNames (int maxLength, String... names) {
        String[] thisSplit;
        Set<String> cities = new HashSet<String>();
        Set<String> states = new HashSet<String>();

        try {
            for (String name : names) {
                if (name == null)
                    continue;

                thisSplit = name.split(", ");

                for (String city : thisSplit[0].split("-")) {
                    cities.add(city);
                }

                if (thisSplit.length >= 2) {
                    for (String state : thisSplit[1].split("-")) {
                        states.add(state);
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        
        return mergeAreaNames(maxLength, cities, states);
    }
    
    public static String mergeAreaNames(int maxLength, Set<String> cities, Set<String> states) {
        try {
            StringBuilder out;
        
            out = new StringBuilder(maxLength);

            if (cities.size() > 0) {
                for (String city : cities) {
                    out.append(city);
                    out.append('-');
                }

                // delete last -
                out.deleteCharAt(out.length() - 1);
            }
            
            if (cities.size() > 0 && states.size() > 0)
                out.append(", ");

            if (states.size() > 0) {
                for (String state : states) {
                    out.append(state);
                    out.append('-');
                }

                out.deleteCharAt(out.length() - 1);
            }

            // truncate if needed
            if (out.length() >= maxLength)
                out.setLength(maxLength);

            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Find the metro area that contains the point given (in WGS84 coordinates). If more than one
     * metro meets this criterion, one of them will be returned (no guarantee as to which one).
     * @param lat
     * @param lon
     * @return
     */
    public static MetroArea findByGeom(double lat, double lon) {
        Query q = JPA.em().createNativeQuery("SELECT m.id FROM MetroArea m WHERE ST_Within(ST_SetSRID(ST_Point(?, ?), 4326), m.the_geom) " +
        		"LIMIT 1");
        q.setParameter(1, lon);
        q.setParameter(2, lat);
        long id;
        try {
            id = ((BigInteger) q.getSingleResult()).longValue();
        } catch (NoResultException e) {
            return null;
        }
        
        return MetroArea.findById(id);
    }

    public static List<MetroArea> getAllMetrosWithTransit() {
        List<MetroArea> metrosWithTransit = new ArrayList<MetroArea>();
        for (MetroArea m : 
                MetroArea.find("SELECT m FROM MetroArea m WHERE size(m.agencies) > 0 ")
                    .<MetroArea>fetch()) {
            metrosWithTransit.add(m);
        }
        
        return metrosWithTransit;
    }

    /**
     * Automatically create geometries from the member agencies' GTFS feeds.
     */
    public void autogeom() {
        Geometry out = null;
        Geometry agencyGeom;
        Integer srid = null;
        
        for (NtdAgency agency : agencies) {
            agencyGeom = agency.getGeom();
            if (agencyGeom == null)
                continue;
            
            if (out == null) {
                out = agencyGeom;
                srid = agencyGeom.getSRID();
            }
            else
                out = OverlayOp.overlayOp(out, agencyGeom, OverlayOp.UNION);
        }
        
        if (srid == null)
            the_geom = null;
        else {
            out.setSRID(srid);
            the_geom = GeometryUtils.forceToMultiPolygon(out);
        }
    }

    /**
     * Generate and send a deployment plan for this metro to the deployer.
     * @return the deployment plan, or null if there is no deployment plan.
     */
    // pass the illegal state exception up the tree
    public DeploymentPlan rebuild() throws IllegalStateException {
        if (!this.disabled) {
            if (Boolean.parseBoolean(
                    Play.configuration.getProperty("dashboard.send_requests_automatically"))) {
                // generate the plan
                DeploymentPlan plan = new DeploymentPlan(this);

                // and dispatch the JSON
                plan.sendTo(Play.configuration.getProperty("dashboard.send_deployer_requests_to"));
                return plan;
            }
        }
        return null;
    }
}
    