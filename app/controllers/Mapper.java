/**
 * Attempt to automatically match loaded data sets to metro areas and feeds
 */

package controllers;

import play.*;
import play.modules.spring.Spring;
import play.mvc.*;
import play.db.jpa.JPA;
import javax.persistence.Query;
import javax.persistence.NoResultException;
import models.*;
import proxies.NtdAgencyProxy;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.math.BigInteger;

import jobs.UpdateGtfs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import updaters.DeploymentPlan;
import updaters.FeedStatsCalculator;
import updaters.FeedStorer;
import utils.DbUtils;

public class Mapper extends Controller {
    /**
     * Attempt to automatch feeds to agencies by URL.
     */
    public static void mapFeedsToAgenciesByUrl () {
        long feedId;
        long agencyId;
        String[] report = new String[4];
        NtdAgency agency;
        GtfsFeed feed;

        // just to give the user an idea of percentages
        long feedCount = GtfsFeed.count();
        
        // Inner join - don't match those that don't match
        // this may match in two directions
        Query q = JPA.em().createNativeQuery(
                                       "SELECT f.id AS feedid, a.id AS agencyid, " + 
                                       "a.name AS agencyname, f.agencyName as feedname, " +
                                       "a.url AS agencyurl, f.agencyUrl AS feedurl " + 
                                       "FROM GtfsFeed f INNER JOIN NtdAgency a " +
                                       "ON (regexp_replace(LOWER(f.agencyUrl), " + 
                                       // that regular expression strips the protocol, 
                                       // strips pathinfo,
                                       // and strips www. to get a hopefully LCD domain name
                                       "'(?:\\W*)(?:https?://)?(?:www\\.)?([a-zA-Z0-9\\-_\\.]*)(?:/.*)?(?:\\W*)'," +
                                       "'\\1') = " + 
                                       "regexp_replace(LOWER(a.url)," + 
                                       "'(?:\\W*)(?:https?://)?(?:www\\.)?([a-zA-Z0-9\\-_\\.]*)(?:/.*)?(?:\\W*)'," +
                                       "'\\1'));"
                                       );
        
        List<Object[]> results = q.getResultList();

        List<String[]> matches = new ArrayList<String[]>();

        for (Object[] result : results) {
            // postgres query results are bigintegers in JPA, but longs in Hibernate
            feedId = ((BigInteger) result[0]).longValue();
            agencyId = ((BigInteger) result[1]).longValue();

            Logger.debug("Agency: %s, feed: %s", agencyId, feedId);

            agency = NtdAgency.findById(agencyId);
            feed = GtfsFeed.findById(feedId);

            if (agency.feeds.contains(feed)) {
                // don't bother to report or add
                continue;
            }

            report = new String[4];
            report[0] = (String) result[2];
            report[1] = (String) result[3];
            report[2] = (String) result[4];
            report[3] = (String) result[5];
            matches.add(report);

            agency.feeds.add(feed);

            agency.save();
        }

        render(matches, feedCount);
    }
    
    /**
     * Create NTD agencies and assign them to metro areas for all unmapped feeds.
     * Since many agencies do not have NTD entries, this fills out the database.
     */
    public static void mapFeedsWithNoAgencies () {
        Set<MetroArea> changed = DbUtils.mapFeedsWithNoAgencies();
        List<String> names = new ArrayList<String>();
        for (MetroArea m : changed) {
            names.add(m.name);
        }
        renderJSON(names);
    }

    /**
     * Clear ALL agency to feed mappings. This should be protected
     */
    public static void clearAllAgencyFeedMappings () {
        List<NtdAgency> agencies = NtdAgency.findAll();
        for (NtdAgency agency : agencies) {
            // remove every feed from this agency
            agency.feeds.removeAll(agency.feeds);
            agency.save();
        }
    }

    /**
     * Attempt to spatially assign metro areas to agencies
     */
    public static void mapAgenciesToMetroAreasSpatially () {
        String[] report;
        NtdAgency agency;
        MetroArea metro;
        long agencyId;
        long metroId;
        long agencyCount = NtdAgency.count();
        GtfsFeed feed;

        // TODO: how does this query behave when an agency touches multiple metros?
        String qs =
            "SELECT a.id AS agencyid, m.id AS metroid, a.name AS agencyname, " + 
            "       a.url AS agencyurl, m.name AS metroname " +
            "  FROM (NtdAgency a " +
            // use inner join; agencies with no feeds should not be considered
            // TODO: this shouldn't depend on how hibernate structures its tables
            "  INNER JOIN (SELECT nf.ntdagency_id AS id, ST_Union(f.the_geom) AS the_geom " +
            "             FROM NtdAgency_GtfsFeed nf " +
            "             LEFT JOIN GtfsFeed f ON (nf.feeds_id = f.id) " +
            "             GROUP BY nf.ntdagency_id) g" +
            "  USING (id)) " +
            // agencies outside a metro area cannot be mapped: use inner join
            "  INNER JOIN MetroArea m " +
            "  ON (ST_Intersects(g.the_geom, m.the_geom))";

        Query q = JPA.em().createNativeQuery(qs);
        List<Object[]> results = q.getResultList();
        List<String[]> matches = new ArrayList<String[]>();

        for (Object[] result : results) {
            report = new String[3];
     
            agencyId = ((BigInteger) result[0]).longValue();
            metroId = ((BigInteger) result[1]).longValue();

            Logger.debug("agency: %s, metro: %s", agencyId, metroId);
            agency = NtdAgency.findById(agencyId);
            metro = MetroArea.findById(metroId);

            // don't break ones that have already been fixed
            if (agency.getMetroAreas().size() == 0)
                continue;

            report[0] = (String) result[2];
            report[1] = (String) result[3];
            report[2] = (String) result[4];

            matches.add(report);

            metro.agencies.add(agency);
            agency.save();
            metro.save();
        }

        render(matches, agencyCount);
    }   

    /**
     * DANGER: clear all agency to metro area mappings
     */
    public static void clearAllAgencyMetroAreaMappings () {
        for (MetroArea metro : MetroArea.<MetroArea>findAll()) {
            metro.initializeAgencies();
            metro.save();
        }
    }

    /**
     * Delete the agency specified by from, moving all of its feeds to the agency specified by
     * to.
     */
    public static void moveFeedsRemoveAgency(long from, long to) {
        NtdAgency fromA = NtdAgency.findById(from);
        NtdAgency toA = NtdAgency.findById(to);
        
        for (GtfsFeed feed : fromA.feeds) {
            toA.feeds.add(feed);
        }
        
        fromA.note = "moved to " + toA.id;
        fromA.save();
        toA.save();
        
        renderJSON("{\"status\":\"success\"}");
    }
    
    /**
     * Delete all metro areas that have no agencies
     */
    public static void removeMetroAreasWithNoAgencies () {
        List<MetroArea> areas;
        
        areas = MetroArea.find("SELECT m FROM MetroArea m WHERE " +
                               "(SELECT count(*) FROM NtdAgency a WHERE a.metroArea.id = m.id) = 0")
            .fetch();

        for (MetroArea area : areas) {
            area.delete();
        }

        render(areas);
    }

    /**
     * Name metro areas based on area description of largest agency by ridership
     */
    public static void autoNameMetroAreas () {
        List<String[]> renames = new ArrayList<String[]>();
        String[] rename;

        // TODO: more efficient, DB driven algorithm
        for (MetroArea area : MetroArea.<MetroArea>findAll()) {
            rename = new String[2];
            rename[0] = area.name;

            area.autoname();

            rename[1] = area.name;
            area.save();
            
            renames.add(rename);
        }

        render(renames);
    }  

    /**
     * Manually map feeds to agencies. This will connect each feed specified with each agency
     * specified. All feeds specified will be connected to all agencies specified.
     * @param feed The feeds to map
     * @param agency The agencies to map
     */
    public static void connectFeedsAndAgencies (long[] feed, long[] agency) {
        List<NtdAgency> agencies = new ArrayList<NtdAgency>();
        List<GtfsFeed> feeds = new ArrayList<GtfsFeed>();
        NtdAgency currentAgency;
        GtfsFeed currentFeed;

        for (long agencyId : agency) {
            currentAgency = NtdAgency.findById(agencyId);
            if (currentAgency != null)
                agencies.add(currentAgency);
            else
                Logger.warn("Agency %s does not exist", agencyId);
        }

        for (long feedId : feed) {
            currentFeed = GtfsFeed.findById(feedId);
            if (currentFeed != null)
                feeds.add(currentFeed);
            else
                Logger.warn("Feed %s does not exist", feedId);
        }

        // now, link them
        for (NtdAgency linkAgency : agencies) {
            for (GtfsFeed linkFeed : feeds) {
                linkAgency.feeds.add(linkFeed);
            }
            linkAgency.save();
        }
    }

    /**
     * Make a best-guess for what agency is referenced by a name based on its location and name.
     */
    public static void setGoogleGtfsFromParse (String name, double lat, double lon) {
        long metroId;
        MetroArea metro;
        List<Object> results;
        List<NtdAgencyProxy> agencies = new ArrayList<NtdAgencyProxy>();
        NtdAgency agency;

        // First, get the MetroArea
        String qs = "SELECT id FROM MetroArea m " + 
            "WHERE ST_Within(" +
              "ST_GeomFromEWKT(CONCAT('SRID=4326;POINT(', ?, ' ', ?, ')'))" +
            ", m.the_geom)";
        Query q = JPA.em().createNativeQuery(qs);
        q.setParameter(1, "" + lon);
        q.setParameter(2, "" + lat);

        try {
            metroId = ((BigInteger) q.getSingleResult()).longValue();
        } catch (NoResultException e) {
            renderJSON("[]");
            return; // so Java won't warn about metroId be used unitialized.
        }

        metro = MetroArea.findById(metroId);
        
        Logger.debug("Found metro %s", metro.name);        
        
        // find matching agencies
        qs = "SELECT a.id FROM ntdagency a " +
                "INNER JOIN metroarea_ntdagency mn ON (a.id = mn.agencies_id) " +
                "INNER JOIN metroarea m ON (m.id = mn.metroarea_id)" +
                "WHERE (to_tsvector(CONCAT(a.name, ' ', " + 
                "regexp_replace(a.url, '\\.|https?://|/|_|\\-', ' ', 'g'))) " +
                "@@ plainto_tsquery(?) " +
                "OR a.url ILIKE CONCAT('%', ?, '%')) " +
                "AND m.id = ?";

        q = JPA.em().createNativeQuery(qs);
        q.setParameter(1, name);
        q.setParameter(2, name);
        q.setParameter(3, metroId);

        for (Object result : q.getResultList()) {
            agency = NtdAgency.findById(((BigInteger) result).longValue());
            agency.googleGtfs = true;
            agency.save();
            agencies.add(new NtdAgencyProxy(agency));
        }

        renderJSON(agencies);
    }
    
    public static void fetchGtfs () {
    	new UpdateGtfs().now();
    	renderJSON("{\"status\":\"running\"}");
    }
    
    public static void createDeploymentPlan (MetroArea metroArea, String send) {
    	DeploymentPlan dp = new DeploymentPlan(metroArea);
    	
    	if (send != null) {
    	    dp.sendTo(send);
    	}
    	
    	renderJSON(dp.toJson());
    }
    
    /**
     * Create GTFS bundle entries for the given metro area ID.
     */
    public static void createGtfsBundles (MetroArea metroArea) {
        Set<GtfsFeed> feeds = new HashSet<GtfsFeed>();
        
        for (NtdAgency agency : metroArea.agencies) {
            for (GtfsFeed feed : agency.feeds) {
                if (feed.supersededBy == null)
                    feeds.add(feed);
            }
        }
        
        String out = "";
        
        for (GtfsFeed feed : feeds) {
            out = out + "<bean class=\"org.opentripplanner.graph_builder.model.GtfsBundle\">\n" +
                    "  <property name=\"url\" value=\"" + feed.downloadUrl + "\" />\n" +
                    "  <property name=\"defaultAgencyId\" value=\"" + feed.dataExchangeId + "\" />\n" +
                    "</bean>\n";
        }
        
        renderText(out);
    }
    
    /**
     * Retrieve the GTFS with the given ID in storage and calculate its feed stats, displaying
     * them to the user. It uses a stored ID not a GtfsFeed ID so the user can diagnose stats on
     * feeds that downloaded successfully but crashed during feed stats calculation.
     */
    public static void calculateFeedStats(String storedId) throws Exception {
        FeedStorer storer = Spring.getBeanOfType(FeedStorer.class);
        File feed = storer.getFeed(storedId);
        FeedStatsCalculator stats;
        
        try {
            stats = new FeedStatsCalculator(feed);
        }
        finally {
            storer.releaseFeed(storedId);
        }
        
        renderJSON("{\"start\": \"" + stats.getStartDate() + "\",\"end\":\"" + stats.getEndDate() + 
                "\",\"the_geom\":\"" + stats.getGeometry().toText() + "\"}");
    }

    /**
     * Assign agencies to metro areas by their UZA and then merge connected UZAs.
     * @param commit If set to false, this transaction will not be committed. Useful for
     *   doing a dry run since this mapper is pretty destructive.
     */
    public static void mapAgenciesByUzaAndMerge (boolean commit) {
        List<NtdAgency> agencies = NtdAgency.findAll();
        List<NtdAgency> noUzaAgencies = new ArrayList<NtdAgency>();
        List<NtdAgency> unmappedAgencies = new ArrayList<NtdAgency>();
        MetroArea area = null;
        MetroArea toCheck = null;
        MetroArea other;
        MetroArea areaMergeInto;
        List<MetroArea> allAreas;
        boolean changed;
        // init only the outer; inner is inited on each iteration
        List<List<String>> toMerge = new ArrayList<List<String>>();
        List<String> agencyUzas;
        List<String> toDelete = null;
        List<String> toAddTo = null;
        List<String> resultingAreas = new ArrayList<String>();
        Set<String> nullUzas = new HashSet<String>();
        String currentUza;
    
        if (!commit)
            // prevent the changes in here from being committed
            JPA.setRollbackOnly();
    
        // The UZAs are merged in three passes. Here's how it works. In the first pass, each
        // agency is assigned to a UZA that it is a member of. If an agency has multiple UZAs,
        // one is selected. It doesn't matter which (as long as it's not null) because later all
        // connected UZAs will be merged. The first pass also creates a list of lists of connected
        // areas, which is just a list of all the UZAs of each agency. After the first iteration,
        // we have a starting point, but there are still overlaps between the service areas of
        // different agencies.
    
        // The next step is a nested loop. It iterates over that list of lists, and for each list
        // looks if any members of it are contained in other lists. If they are, it combines those
        // two lists, deletes the second one, and restarts the loop. This continues until there are
        // no overlaps between areas
    
        // Finally, the resultant list of lists is iterated over, and for each area all of the 
        // the UZAs are merged into the first one using the MetroArea.mergeWith method of the 
        // first UZA.
    
        // First, we map each one to its first UZA, which is fine because we merge all the
        // UZAs later anyways. Also, make a list of UZAs which should be merged.
        for (NtdAgency agency : agencies) {
            // make sure that UZAs don't leak between iterations
            area = null;
    
            // if this doesn't have a UZA, report it in the template
            if (agency.uzaNames.size() == 0) {
                noUzaAgencies.add(agency);
                continue;
            }
    
            // Loop over all the UZAs and report which ones are null for user inspection
            for (String uza : agency.uzaNames) {
                toCheck = MetroArea.find("byName", uza).first();
                if (toCheck == null)
                    nullUzas.add(uza);
                else
                    // don't assign null areas unless we have to
                    area = toCheck;
            }
    
            // if area is still null, warn the user that this won't have an area
            if (area == null)
                unmappedAgencies.add(agency);
    
            // set the area
            area.agencies.add(agency);
            area.save();
            agency.save();
    
            // intern all the strings for fast comparisons later
            // and put them into the list of lists for the reducer to chew on.
            agencyUzas = new ArrayList<String>();
            for (String uza : agency.uzaNames) {
                agencyUzas.add(uza.intern());
            }
            toMerge.add(agencyUzas);
        }
    
        // now, merge the lists of UZAs (pass 2)
        do {
            changed = false;
    
            for (List<String> mergeArea : toMerge) {
                // find areas with common UZAs
                for (List<String> otherMergeArea : toMerge) {
    
                    // don't merge areas with themselves
                    if (mergeArea == otherMergeArea) 
                        continue;
    
                    // for each of the UZAs in this area, see if there is overlap with the other
                    // areas
                    for (String uza : mergeArea) {
                        if (otherMergeArea.contains(uza)) {
                            // they need to be merged. mark them as such, and end the iteration
                            // so they can be.
                            toDelete = otherMergeArea;
                            toAddTo = mergeArea;
                            changed = true;
                            break;    
                        }
                    }
                }
            }
    
            // if one was marked for update
            if (toDelete != null) {
                for (String uza : toDelete) {
                    if (!toAddTo.contains(uza)) {
                        toAddTo.add(uza);
                    }
                }
                        
                // remove the merged object
                toMerge.remove(toDelete);
            }
            toDelete = null;
            toAddTo = null;
    
            Logger.debug("Reduced to %s areas", toMerge.size());
        } while (changed);
    
        // now, actually merge the areas
        for (List<String> connected : toMerge) {
            // clear this out so it can be used to track state below
            areaMergeInto = null;
    
            // merge each one with the first one, or the first one that exists
            // find one that isn't null. If this gets to the end of the list, the loop will
            // terminate by itself
            do {
                currentUza = connected.get(0);
                areaMergeInto = MetroArea.find("byName", currentUza).first();
                connected.remove(0);
    
                if (areaMergeInto == null) {
                    // ignore but report
                    nullUzas.add(currentUza);
                    continue;
                }
                else {
                    // we found one to use.
                    break;
                }
            } while (areaMergeInto == null && connected.size() > 0);
    
            // in this case, there were no existing agencies, so skip saving it &c.
            if (areaMergeInto == null)
                continue;
            
            // now, loop through the remaining UZAs and merge if not null.
            for (String otherName : connected) {
                // if it's null, ignore it but report it
                other = MetroArea.find("byName", otherName).first();
                if (other == null) {
                    nullUzas.add(otherName);
                    continue;
                }
    
                if (other == areaMergeInto)
                    Logger.warn("Areas are equal!");
    
                // otherwise, merge it and delete it
                areaMergeInto.mergeAreas(other);
                other.delete();
            }
            areaMergeInto.save();
            resultingAreas.add(areaMergeInto.name);
        }
    
        render(resultingAreas, noUzaAgencies, nullUzas, unmappedAgencies, commit);
    }
}
            
            
            
            