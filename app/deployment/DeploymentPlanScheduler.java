package deployment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.GtfsFeed;
import models.MetroArea;
import models.NtdAgency;
import models.ScheduledRebuild;

/**
 * This handles the scheduling of graph rebuilds in the future. It is a layer of abstraction
 * between the DB and the deployment planner - if one wanted to schedule plans without using
 * the DB, a superclass/interface could be extracted from this and used to create a new deployment
 * plan scheduler.
 *  
 * @author mattwigway
 */
public class DeploymentPlanScheduler {
    /**
     * Schedule a rebuild for all metros associated with feed feed on date date.
     */
    public static void scheduleRebuild(GtfsFeed feed, Date date) {
        for (NtdAgency agency : feed.getAgencies()) {
            scheduleRebuild(agency, date);
        }
    }
    
    /**
     * Schedule a rebuild for all agencies associated with the given agency on the given date.
     */
    public static void scheduleRebuild(NtdAgency agency, Date date) {
        for (MetroArea area : agency.getMetroAreas()) {
            scheduleRebuild(area, date);
        }
    }
    
    /**
     * Schedule a rebuild for the given metro area on the given date
     */
    public static void scheduleRebuild(MetroArea area, Date date) {
        new ScheduledRebuild(area, date).save();
    }
    
    /**
     * Clear all scheduled rebuilds for the given metro
     */
    public static void clearRebuilds(MetroArea area) {
        for (ScheduledRebuild rebuild : 
                ScheduledRebuild.find("byMetroArea", area).<ScheduledRebuild>fetch()) {
            rebuild.delete();
        }
    }
    
    /**
     * Get all scheduled rebuilds for the given metro
     */
    public static List<Date> getRebuildsForMetro (MetroArea area) {
        List<Date> ret = new ArrayList<Date>();
        for (ScheduledRebuild rebuild : 
                ScheduledRebuild.find("byMetroArea", area).<ScheduledRebuild>fetch()) {
            ret.add(rebuild.rebuildAfter);
        }
        
        return ret;
    }
    
    /**
     * Return all the metros that need to be updated
     */
    public static Set<MetroArea> getMetroAreasNeedingUpdate () {
        Date now = new Date();
        Set<MetroArea> ret = new HashSet<MetroArea>();
        
        for (ScheduledRebuild rebuild : ScheduledRebuild.<ScheduledRebuild>findAll()) {
            if (rebuild.rebuildAfter != null && now.compareTo(rebuild.rebuildAfter) >= 0)
                if (rebuild.metroArea != null)
                    ret.add(rebuild.metroArea);
                
        }   
                
        return ret;
    }
    
    /**
     * Generate deployment plans for all the metros that need them.
     */
    public static List<DeploymentPlan> generatePlans () {
        return generatePlans(null);
    }
    
    /**
     * Generate deployment plans for all the metros that need them, and send them to the given URL.
     */
    public static List<DeploymentPlan> generatePlans (String url) {
        List<DeploymentPlan> plans = new ArrayList<DeploymentPlan>();
        DeploymentPlan plan;
        
        for (MetroArea area : getMetroAreasNeedingUpdate()) {
            plan = new DeploymentPlan(area);
            plans.add(plan);
            
            if (url != null) {
                plan.sendTo(url);
            }
        }
        
        return plans;
    }
}
