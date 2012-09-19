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
  along with this program. If not, see  <http://www.gnu.org/licenses/>. 
*/

package controllers;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;

import javax.persistence.Query;

import com.vividsolutions.jts.geom.Geometry;

import deployment.DeploymentPlan;

import play.Logger;
import play.Play;
import play.db.jpa.JPA;
import play.modules.spring.Spring;
import updaters.FeedStorer;

import models.FeedParseStatus;
import models.GtfsFeed;
import models.MetroArea;
import models.MetroAreaSource;
import models.NtdAgency;
import models.ReviewType;
import models.UnmatchedMetroArea;
import models.UnmatchedPrivateGtfsProvider;

/**
 * This defines the admin interface, which is a place to deal with things that are unmapped, &c.
 * It works in conjunction with /admin/mapfeeds.html and /crud/
 * @author mattwigway
 */
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
        
        q = JPA.em().createNativeQuery("SELECT count(*) FROM NtdAgency WHERE review = 'NO_METRO'");
        int agenciesNoMetro = ((BigInteger) q.getSingleResult()).intValue();
        
        q = JPA.em().createNativeQuery("SELECT count(*) FROM NtdAgency WHERE review = 'AGENCY_MULTIPLE_AREAS'");
        results = q.getResultList();
        int agenciesMultiAreas = results.get(0).intValue();

        q = JPA.em().createNativeQuery("SELECT count(*) FROM MetroArea WHERE needsUpdate = true");
        results = q.getResultList();
        int metrosNeedingUpdate = results.get(0).intValue();
        
        long unmatchedPrivate = UnmatchedPrivateGtfsProvider.count();
        long unmatchedMetro = UnmatchedMetroArea.count();
        
        render(feedsNoAgency, agenciesMultiAreas, agenciesNoMetro, unmatchedPrivate,
                unmatchedMetro, metrosNeedingUpdate);
    }
    
    /**
     * Rebuild metros needing rebuild.
     */
    public static void rebuildMetrosNeedingRebuild () {
        // TODO: only a POST request should be able to trigger this
        for (MetroArea metro : MetroArea.find("byNeedsUpdate", true).<MetroArea>fetch()) {
            try {
                metro.rebuild();
                metro.needsUpdate = false;
                metro.save();
                
            // do nothing if we can't rebuild; leave it flagged
            } catch (IllegalStateException e) {};
        }
        
        index();
    }
    
    /**
     * Choose an agency and send the user on to the given URL.
     */
    public static void chooseAgency (String redirectTo) {
        List<NtdAgency> agencies = NtdAgency.findAll();
        render(agencies, redirectTo);
    }
    
    /**
     * Edit the realtime feeds for an agency
     */
    public static void editRealtimeFeeds(NtdAgency agency) {
        // it would be a lot nicer to do this in the template on render, but I (MWC) can't figure
        // out how to import the enum there
        List<GtfsFeed> feeds = new ArrayList<GtfsFeed>();
        
        for (GtfsFeed feed : agency.feeds) {
            if (feed.status == FeedParseStatus.SUCCESSFUL && feed.supersededBy == null)
                feeds.add(feed);
        }
        
        render(agency, feeds);
    }
    
    /**
     * Save the realtime feeds for an agency
     */
    public static void saveRealtimeFeeds(GtfsFeed feed, List<String> feedUrls) {
        feed.realtimeUrls = feedUrls;
        feed.save();
        
        for (NtdAgency agency : feed.getEnabledAgencies()) {
            for (MetroArea metro : agency.getEnabledMetroAreas()) {
                metro.needsUpdate = true;
                metro.save();
            }
        }
        
        index();
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
        
        for (MetroArea metro : agency.getEnabledMetroAreas()) {
            metro.needsUpdate = true;
            metro.save();
        }
    }
    
    /**
     * Create a new agency from a feed
     * @author mattwigway
     */
    public static void newAgencyFromFeed(GtfsFeed feed) {
        NtdAgency agency = new NtdAgency(feed);
        feed.review = null;
        feed.save();
        agency.feeds.add(feed);
        agency.save();
        
        // basically, for the findAndAssignToMetroArea, the feed has to be in the DB, so we flush
        // to make sure there is nothing in the JPA cache
        JPA.em().flush();
        // agency is saved in here
        agency.findAndAssignMetroArea();
        
        for (MetroArea metro : agency.getEnabledMetroAreas()) {
            metro.needsUpdate = true;
            metro.save();
        }
    }
    
    
    /**
     * Handle agencies with no metros.
     */
    public static void agenciesNoMetros () {
        for (NtdAgency agency : NtdAgency.find("byReview", ReviewType.NO_METRO)
                .<NtdAgency>fetch()) {
            agency.review = null;
            agency.findAndAssignMetroArea();
        }
        index();
    }
    
    /**
     * This is just a proxy to serialize the important parts of a metro to JSON.
     */
    @SuppressWarnings("unused")
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
            for (MetroArea metro : agency.getEnabledMetroAreas()) {
                metro.needsUpdate = true;
                metro.save();
            }
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
            for (MetroArea metro : agency.getEnabledMetroAreas()) {
                metro.needsUpdate = true;
                metro.save();
            }
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
	    
	    if (currentAgencies == null)
		continue;

            splitAgencies[i - 1] = new NtdAgency[currentAgencies.length];
            
            // loop through each one, getting the agencies
            for (int j = 0; j < currentAgencies.length; j++) {
                splitAgencies[i - 1][j] = NtdAgency.findById(Long.parseLong(currentAgencies[j]));
            }
        }
        
        // now, create new metros for each
        MetroArea metro;
        for (NtdAgency[] agencies: splitAgencies) {
	    if (agencies == null)
		continue;

            metro = new MetroArea();
            for (NtdAgency agency : agencies) {
                metro.agencies.add(agency);
            }
            metro.autoname();
            metro.autogeom();
            metro.source = MetroAreaSource.GTFS;
            metro.needsUpdate = true;
            metro.save();
        }
        
        original.disabled = true;
        original.note = "superseded by split metro.";
        original.save();
        
        // note unmapped agencies now
        for (NtdAgency agency : original.agencies) {
            boolean isInSplitMetro = false;
            SPLITS: for (NtdAgency[] split : splitAgencies) {
		if (split == null)
		    continue;

                for (NtdAgency other : split) {
                    if (agency.equals(other)) {
                        isInSplitMetro = true;
                        break SPLITS;
                    }
                }
            }
            
            if (!isInSplitMetro) {
                agency.review = ReviewType.NO_METRO;
                agency.save();
            }
        }
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
     
     /**
      * Generate deployment plans for every single metro with transit
      */
     public static void generateDeploymentPlansForAllMetros () {
         int count = 0;
         DeploymentPlan dp;
         for (MetroArea metro : MetroArea.getAllMetrosWithTransit()) {
             metro.rebuild();
             count++;
         }
         renderText("Deployed " + count + " metros.");
     }
}
