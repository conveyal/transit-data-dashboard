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

package controllers;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;

import javax.persistence.Query;

import com.vividsolutions.jts.geom.Geometry;

import play.Logger;
import play.db.jpa.JPA;
import play.modules.spring.Spring;
import updaters.FeedStorer;

import models.GtfsFeed;
import models.MetroArea;
import models.NtdAgency;
import models.ReviewType;
import models.UnmatchedMetroArea;
import models.UnmatchedPrivateGtfsProvider;

public class Admin extends Mapper {
    /**
     * The main admin screen.
     */
    public static void index () {
        // get counts for various types of problems
        // Feeds with no agency
        Query q = JPA.em().createNativeQuery("SELECT count(*) FROM GtfsFeed WHERE review = 'NO_AGENCY'");
        List<BigInteger> results = q.getResultList();
        int feedsNoAgency = results.get(0).intValue();
        
        q = JPA.em().createNativeQuery("SELECT count(*) FROM NtdAgency WHERE review = 'AGENCY_MULTIPLE_AREAS'");
        results = q.getResultList();
        int agenciesMultiAreas = results.get(0).intValue();
     
        long unmatchedPrivate = UnmatchedPrivateGtfsProvider.count();
        long unmatchedMetro = UnmatchedMetroArea.count();
        
        render(feedsNoAgency, agenciesMultiAreas, unmatchedPrivate, unmatchedMetro);
    }
    
    /**
     * Show feeds with no agencies.
     * @author mattwigway
     */
    public static void feedsNoAgencies () {
        List<GtfsFeed> feedsNoAgencies = GtfsFeed.find("byReview", ReviewType.NO_AGENCY).fetch();
        List<NtdAgency> agencies = NtdAgency.findAll();
        
        render(feedsNoAgencies, agencies);
    }
    
    /**
     * Link a feed to an agency
     * @author mattwigway
     */
    public static void linkFeedToAgency (GtfsFeed feed, NtdAgency agency) {
        agency.feeds.add(feed);
        feed.review = null;
        feed.save();
        agency.save();
    }
    
    /**
     * Create a new agency from a feed
     * @author mattwigway
     */
    public static void newAgencyFromFeed(GtfsFeed feed) {
        NtdAgency agency = new NtdAgency(feed);
        agency.review = ReviewType.NO_METRO;
        agency.save();
    }
    
    private static class MetroAreaWithGeom {
        private String name;
        // in GeoJSON format
        private String geom;
    }
    
    /**
     * Show agencies which match multiple areas
     */
    public static void agenciesMultiAreas () {
        List<NtdAgency> agenciesMultiAreas = 
                NtdAgency.find("byReview", ReviewType.AGENCY_MULTIPLE_AREAS).fetch();
        
        // This maps an agency ID to a metro area 
        Map<Long, List<MetroAreaWithGeom>> metros = new HashMap<Long, List<MetroAreaWithGeom>>();
        
        MetroAreaWithGeom metro;
        String query = "SELECT m.name, ST_AsGeoJSON(the_geom) FROM MetroArea m WHERE " + 
                "ST_DWithin(m.the_geom, transform(ST_GeomFromText(?, ?), ST_SRID(m.the_geom)), 0.04)";
        Geometry agencyGeom;
        Query ids;
        List<Object[]> metrosTemp;
        for (NtdAgency agency : agenciesMultiAreas) {
            if (!metros.containsKey(agency.id))
                metros.put(agency.id, new ArrayList<MetroAreaWithGeom>());
            
            // find metro area(s)
            agencyGeom = agency.getGeom();
            ids = JPA.em().createNativeQuery(query);
            ids.setParameter(1, agencyGeom.toText());
            ids.setParameter(2, agencyGeom.getSRID());
            metrosTemp = ids.getResultList();
            
            for (Object[] result : metrosTemp) {
                metro = new MetroAreaWithGeom();
                metro.name = (String) result[0];
                metro.geom = (String) result[1];
                
                metros.get(agency.id).add(metro);
            }
        }
        
        render(agenciesMultiAreas, metros);
    }
    
    /**
     * Decide whether to redirect to agenciesMultiAreas or not.
     */
    public static void showAgenciesMultiAreas () {
            //agenciesMultiAreas();
            renderText("Success");
    }
    
    /**
     * This just disables an agency and clears the review flag.
     */
    public static void disableAgency(NtdAgency agency) {
        agency.disabled = true;
        agency.review = null;
        agency.save();
        showAgenciesMultiAreas();
    }
    
    /**
     * This merges all the areas the agency could be considered part of.
     */
    public static void mergeAllAreas (NtdAgency agency) {
        try {
            agency.mergeAllAreas();
        } catch (NullPointerException e) {
            flash("error", "invalid geometry for agency");
            showAgenciesMultiAreas();
            return;
        }
        
        agency.review = null;
        agency.save();
        showAgenciesMultiAreas();
    }
    
    /**
     * For an agency that overlaps multiple metro areas, this makes the agency a member of each of them with no
     * merging. This should be used, for example, when there are multiple distinct metro areas that have a single agency that
     * serves all of them (for instance, NJ Transit in NYC).
     */
    public static void splitToAreas (NtdAgency agency) {
        try {
            agency.splitToAreas();
        } catch (NullPointerException e) {
            flash("error", "agency has no valid geometry");
            showAgenciesMultiAreas();
            return;
        }
        
        agency.review = null;
        agency.save();
        
        // redirect back
        // http://stackoverflow.com/questions/4283256
        showAgenciesMultiAreas();
    }
    
    /**
     * Actually perform the split for a given metro
     */
    // 
    public static void saveSplitMetro () {
        // we get the number of splits then parse down the URL params; each metro is named
        // metron, where n is a number greater than or equal to 1 and less than or equal to splits.
        int splits = params.get("splits", Integer.class);
        MetroArea original = MetroArea.findById(params.get("original", Long.class));
        
        // This is an array of lists of long.
        NtdAgency[][] splitAgencies = new NtdAgency[splits][];
        String[] currentAgencies;
        
        for (int i = 1; i <= splits; i++) {
            // get all of the agencies with the metro of this index
            currentAgencies = params.getAll("metro" + i);
            splitAgencies[i - 1] = new NtdAgency[currentAgencies.length];
            
            // loop through each one, getting the agencies
            for (int j = 0; j < currentAgencies.length; j++) {
                splitAgencies[i - 1][j] = NtdAgency.findById(Long.parseLong(currentAgencies[j]));
            }
        }
        
        // now, create new metros for each
        MetroArea metro;
        for (NtdAgency[] agencies: splitAgencies) {
            metro = new MetroArea();
            for (NtdAgency agency : agencies) {
                metro.agencies.add(agency);
            }
            metro.autoname();
            metro.save();
        }
        
        original.disabled = true;
        original.note = "superseded by split metro.";
        original.save();
    }

    /**
     * Split a metro area into the given number of parts interactively.
     * @param metroId The metro area to split
     * @param splits The number of pieces to split it into 
     */
    public static void splitMetroInteractively (long metroId, int splits) {
        MetroArea original = MetroArea.findById(metroId);
        render(original, splits);
    }

    /**
     * Find all the stored feeds that are not also in the database.
     */
    public static void removeUnreferencedStoredFeeds () {
        // TODO: is this safe?
        FeedStorer feedStorer = Spring.getBeanOfType(FeedStorer.class); 
        List<String> storedIds = feedStorer.getFeedIds();
        
        // get the ids stored in the db
        Query q = JPA.em().createQuery("SELECT storedId FROM GtfsFeed");
        List<String> dbIds = q.getResultList();
        
        List<String> notInDb = new ArrayList<String>();
        
        for (String storedId : storedIds) {
            if (!dbIds.contains(storedId)) {
                notInDb.add(storedId);
            }
        }
        
        String storerType = feedStorer.toString();
        render(notInDb, storerType);
    }
    
    /**
     * Delete the stored feeds referenced by the specified ID. Note that this is the 
     * ID in storage, not in the DB.
     * @param storedId
     */
    public static void deleteStoredFeeds(List<String> storedId) {
        Logger.debug("%s feeds to delete", storedId.size());
        FeedStorer feedStorer = Spring.getBeanOfType(FeedStorer.class); 

        for (String id : storedId) {
            feedStorer.deleteFeed(id);
        }
        
        index();
    }
    
    /**
     * Show all the unmatched non-public feeds and give the user a choice of what to do about them.
     */
    public static void unmatchedPrivate () {
        List<UnmatchedPrivateGtfsProvider> providers = UnmatchedPrivateGtfsProvider.findAll();
        render(providers);
    }
    
    /**
     * Assign a private feed to an agency
     */
    public static void assignPrivateToAgency (UnmatchedPrivateGtfsProvider privateProvider,
            NtdAgency agency) {
        agency.googleGtfs = true;
        agency.save();
        privateProvider.delete();
        renderText("Success");
    }
    
    /**
     * Create a new agency from an unmatched agency
     */
     public static void newAgencyFromPrivate(UnmatchedPrivateGtfsProvider privateProvider) {
         // saved inside the constructor
         new NtdAgency(privateProvider);
         privateProvider.delete();
         renderText("Success");
     }
    
     /**
      * Allow the user to match an unmatched metro to an existing metro.
      */
     public static void unmatchedMetro () {
         List<UnmatchedMetroArea> metros = UnmatchedMetroArea.findAll();
         render(metros);
     }
     
     /**
      * Map an unmatched metro to a matched metro, moving all of the formerly unmatched agencies
      * to the new metro and attempting to rematch them.
      */
     public static void mapUnmatchedMetro(UnmatchedMetroArea unmatched, MetroArea area) {
         // first, move all the agencies
         for (UnmatchedPrivateGtfsProvider provider : unmatched.getProviders()) {
             provider.unmatchedArea = null;
             provider.metroArea = area;
             provider.save();
             provider.search();
         }
         
         unmatched.delete();
     }
}
