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

import org.junit.*;
import org.postgresql.util.PSQLException;

import deployment.DeploymentPlan;
import deployment.DeploymentPlan.FeedDescriptor;
import deployment.DeploymentPlanScheduler;

import java.text.SimpleDateFormat;
import java.util.*;

import play.db.jpa.JPA;
import play.test.*;
import models.*;

public class DeploymentPlanTest extends UnitTest {
	@Before
    public void setUp () {
        // TODO: this seems to only delete the ones loaded from fixtures.
	    Fixtures.deleteAllModels();
	    JPA.em().getTransaction().rollback();
	    JPA.em().getTransaction().begin(); 
        Fixtures.loadModels("planner.yml");
        fixupTimezones();
        fixupStatuses();
    }
	
	/**
	 * The play YAML loader can't handle time zones, so we patch up the db here.
	 */
	private static void fixupTimezones () {
	    String[] americaChicagoKeys = new String[] {"cta1", "cta2", "metra"};
	    String[] pacificKiritimatiKeys = new String[] {"k1", "k2"};
	    TimeZone pacificKiritimati = TimeZone.getTimeZone("Pacific/Kiritimati");
	    TimeZone americaChicago = TimeZone.getTimeZone("America/Chicago");
	    TimeZone americaLosAngeles = TimeZone.getTimeZone("America/Los_Angeles");

	    for (GtfsFeed feed : GtfsFeed.<GtfsFeed>findAll()) {
	        if (Arrays.binarySearch(americaChicagoKeys, feed.storedId) >= 0) {
	            feed.timezone = americaChicago;
	        }
	        else if (Arrays.binarySearch(pacificKiritimatiKeys, feed.storedId) >= 0) {
                feed.timezone = pacificKiritimati;
            }
	        else {
	            feed.timezone = americaLosAngeles;
	        }
	        feed.save();
	    }
	}
	
	/**
	 * Add FeedParseStatus.FAILED where the YAML loader left it off.
	 */
	public void fixupStatuses () {
	    GtfsFeed feed;
	    
	    feed = GtfsFeed.find("byStoredId", "SC2").first();
	    feed.status = FeedParseStatus.FAILED;
	    feed.save();
	    
	    feed = GtfsFeed.find("byStoredId", "MAX2").first();
	    feed.status = FeedParseStatus.FAILED;
	    feed.save();
	}	 
	
	@Test
	public void testTimeZoneStorage () {
	    GtfsFeed feedWithTz = new GtfsFeed();
	    feedWithTz.timezone = TimeZone.getTimeZone("America/Los_Angeles");
	    feedWithTz.storedId = "feedWithTimeZone";
	    feedWithTz.save();
	    JPA.em().getTransaction().commit();
	    JPA.em().getTransaction().begin();
	    
	    feedWithTz = GtfsFeed.find("byStoredId", "feedWithTimeZone").first();
	    assertNotNull(feedWithTz);
	    assertNotNull(feedWithTz.timezone);
	    
	    for (GtfsFeed feed : GtfsFeed.<GtfsFeed>findAll()) {
	        assertNotNull(feed.timezone);
	    }
	}
    
    @Test
    public void testMultipleConcurrentFeeds () {
        DeploymentPlan dp;
        MetroArea sf = MetroArea.find("byName", "San Francisco, CA").first();

        // This should include all feeds, because it covers all BART feeds as well as the Muni feed
        dp = new DeploymentPlan(sf, getDate(2012, 5, 10), 420);
        
        String[] feeds = new String[4];

        FeedDescriptor[] fd = dp.getFeeds(); 
        
        assertEquals(4, fd.length);
        
        for (int i = 0; i < 4; i++) {
        	feeds[i] = fd[i].getFeedId();
        }
        
        Arrays.sort(feeds);
        
        assertEquals("bart1", feeds[0]);
        assertEquals("bart2", feeds[1]);
        assertEquals("bart3", feeds[2]);
        assertEquals("muni", feeds[3]);
        
        // The bart1 feed should have been forced to expire before bart2 starts
        String bart1expiration = null;
        for (FeedDescriptor f : fd) {
        	if (f.getFeedId().equals("bart1"))
        		bart1expiration = f.getExpireOn();
        }
        
        assertEquals("2012-07-15", bart1expiration);
        
        // The bart2 feed should have been forced to expire before bart3 starts
        String bart2expiration = null;
        for (FeedDescriptor f : fd) {
        	if (f.getFeedId().equals("bart2"))
        		bart2expiration = f.getExpireOn();
        }
        
        assertEquals("2012-12-09", bart2expiration);
        
        // this should include bart2 and Muni: though it's after Muni has expired, we don't
        // eliminate expired data from graph builds if nothing replaces it
        dp = new DeploymentPlan(sf, getDate(2012, 9, 10), 20);
        
        feeds = new String[2];

        fd = dp.getFeeds(); 
        
        assertEquals(2, fd.length);
        
        for (int i = 0; i < 2; i++) {
        	feeds[i] = fd[i].getFeedId();
        }
        
        Arrays.sort(feeds);
        
        assertEquals("bart2", feeds[0]);
        assertEquals("muni", feeds[1]);
        
        // This should include only BART, excluding feeds not yet valid
        dp = new DeploymentPlan(sf, getDate(2011, 11, 31), 1);
        fd = dp.getFeeds();     
        assertEquals(1, fd.length);
        
        assertEquals("bart1", fd[0].getFeedId());        
    }
    
    /**
     * Test that the deployment planner properly handles the situation where there is a
     * common feed being split to uncommon feeds at different times; keep in mind that "proper"
     * is a relative term here.
     */
    @Test
    public void testCommonFeedSplit () {
        // note that this implicitly tests Unicode in the DB.
        MetroArea sb = MetroArea.find("byName", "Santa Bárbara, CA").first();
        // sept 10, 2012
        DeploymentPlan dp = new DeploymentPlan(sb, getDate(2012, 6, 5), 365);
        FeedDescriptor[] feeds = dp.getFeeds();
        
        boolean mtdFound = false, rtaFound = false, countyFound = false;
        
        for (FeedDescriptor fd : feeds) {
            if (fd.getFeedId().equals("sbmtd")) {
                // it should only be found once
                assertTrue(!mtdFound);
                mtdFound = true;
            }
            else if (fd.getFeedId().equals("slorta")) {
                assertTrue(!rtaFound);
                rtaFound = true;
            }
            else if (fd.getFeedId().equals("sbcounty")) {
                assertTrue(!countyFound);
                countyFound = true;
                // it should run up to when the new MTD feed starts
                // if it's 2012-10-08, then it's running up to SLORTA
                // otherwise, something strange is happening.
                assertEquals("2012-10-31", fd.getExpireOn());
            }
            else {
                // This shouldn't happen; we've covered all the possible cases
                assertTrue(false);
            }
        }
        // make sure everything was found
        assertEquals(3, feeds.length);
        assertTrue(mtdFound);
        assertTrue(rtaFound);
        assertTrue(countyFound);
    }
    
    /**
     * Test the case of a regional GTFS feed to ensure nothing is added twice when feeds are shared
     * by multiple agencies.
     */
    @Test
    public void testMultipleGtfs () {
        MetroArea la = MetroArea.find("byName", "Los Angeles, CA").first();
        assertNotNull(la);
        DeploymentPlan dp = new DeploymentPlan(la, getDate(2012, 1, 1), 500);
        
        FeedDescriptor[] feeds = dp.getFeeds();
        assertEquals(2, feeds.length);
        
        boolean comb1Found = false, comb2Found = false;
        
        for (FeedDescriptor fd : feeds) {
            if ("lacombined1".equals(fd.getFeedId())) {
                assertTrue(!comb1Found);
                comb1Found = true;
            }
            else if ("lacombined2".equals(fd.getFeedId())) {
                assertTrue(!comb2Found);
                comb2Found = true;
            }
            else
                assertTrue(false);
        }
        
        assertTrue(comb1Found);
        assertTrue(comb2Found);
        assertEquals(2, feeds.length);
    }
    
    /**
     * Test that feed expirations are reported in local time.
     */
    @Test
    public void testFeedExpirationsAreReportedInLocalTime () {
        MetroArea k = MetroArea.find("byName", "Kiritimati").first();
        assertNotNull(k);
        
        DeploymentPlan dp = new DeploymentPlan(k, getDate(2012, 8, 10), 1000);
        
        FeedDescriptor[] feeds = dp.getFeeds();
        
        assertEquals(2, feeds.length);
        
        boolean k1Found = false;
        for (FeedDescriptor feed : feeds) {
            if ("k1".equals(feed.getFeedId())) {
                assertFalse(k1Found);
                k1Found = true;
                // would be 2012-10-14 in GMT
                assertEquals("2012-10-15", feed.getExpireOn());
            }
        }
        
        assertTrue(k1Found);
    }
    
    /**
     * Test the case of a GTFS feed beyond the window; make sure it is both (a) not included in the
     * current build and (b) there is a scheduled rebuild of the graph
     */ 
     @Test
     public void testDeploymentScheduler () {
         MetroArea chi = MetroArea.find("byName", "Chicago, IL").first();
         assertNotNull(chi);
         
         DeploymentPlan dp = new DeploymentPlan(chi, getDate(2012, 6, 15), 10);
         
         FeedDescriptor[] feeds = dp.getFeeds();
         
         boolean ctaFound = false, cta2Found = false, metraFound = false;
         
         for (FeedDescriptor feed : feeds) {
             if ("cta1".equals(feed.getFeedId())) {
                 assertTrue(!ctaFound);
                 ctaFound = true;
             }
             else if ("cta2".equals(feed.getFeedId())) {
                 assertTrue(!cta2Found);
                 cta2Found = true;
             }
             else if ("metra".equals(feed.getFeedId())) {
                 assertTrue(!metraFound);
                 metraFound = true;
             }
             else {
                 assertTrue(false);
             }
         }
         
         assertTrue(ctaFound);
         assertTrue(!metraFound);
         assertTrue(!cta2Found);
         assertEquals(1, feeds.length);
         
         // and make sure an entry was generated for it in the rebuild table
         List<Date> rebuilds = DeploymentPlanScheduler.getRebuildsForMetro(chi);
         SimpleDateFormat dates = new SimpleDateFormat("yyyy-MM-dd");
         
         // one for CTA, one for Metra
         assertEquals(2, rebuilds.size());
         
         Collections.sort(rebuilds);
         
         // CTA: 2012-10-15, less 10 days for the window, less 1 day to get the previous day 
         assertEquals("2012-10-04", dates.format(rebuilds.get(0)));
         
         // Metra
         assertEquals("2012-11-04", dates.format(rebuilds.get(1)));
         
         // and clear them all to make sure they go away
         DeploymentPlanScheduler.clearRebuilds(chi);
         
         rebuilds = DeploymentPlanScheduler.getRebuildsForMetro(chi);
         assertEquals(0, rebuilds.size());
     }
     
     /**
      * Test that disabled feeds work properly
      * A disabled feed at the end should have the previous feed included
      * A disabled feed in the middle should leave one feed enabled for the period of validity.
      * A failed feed should behave the same as a disabled one
      */
     @Test
     public void testFeedDisabling () {
         MetroArea pdx = MetroArea.find("byName", "Portland, OR").first();
         assertNotNull(pdx);
         
         DeploymentPlan dp = new DeploymentPlan(pdx, getDate(2011, 2, 2), 1400);
         
         String trimet1 = null, trimet2 = null,
                 wes1 = null, wes2 = null, wes3 = null,
                 max1 = null, max2 = null,
                 sc1 = null, sc2 = null, sc3 = null;
         
         for (FeedDescriptor fd : dp.getFeeds()) {
             if ("TriMet1".equals(fd.getFeedId())) {
                 assertNull(trimet1);
                 trimet1 = fd.getExpireOn();
             }
             else if ("TriMet2".equals(fd.getFeedId())) {
                 assertNull(trimet2);
                 trimet2 = fd.getExpireOn();
             }
             else if ("WES1".equals(fd.getFeedId())) {
                 assertNull(wes1);
                 wes1 = fd.getExpireOn();
             }
             else if ("WES2".equals(fd.getFeedId())) {
                 assertNull(wes2);
                 wes2 = fd.getExpireOn();
             }
             else if ("WES3".equals(fd.getFeedId())) {
                 assertNull(wes3);
                 wes3 = fd.getExpireOn();
             }
             else if ("MAX1".equals(fd.getFeedId())) {
                 assertNull(max1);
                 max1 = fd.getExpireOn();
             }
             else if ("MAX2".equals(fd.getFeedId())) {
                 assertNull(max2);
                 max2 = fd.getExpireOn();
             }
             else if ("SC1".equals(fd.getFeedId())) {
                 assertNull(sc1);
                 sc1 = fd.getExpireOn();
             }
             else if ("SC2".equals(fd.getFeedId())) {
                 assertNull(sc2);
                 sc2 = fd.getExpireOn();
             }
             else if ("SC3".equals(fd.getFeedId())) {
                 assertNull(sc3);
                 sc3 = fd.getExpireOn();
             }
             else {
                 fail("Unexpected agency ID " + fd.getFeedId());
             }
         }
         
         // check that the correct feeds are included
         assertNotNull(trimet1);
         assertNull(trimet2);
         assertNotNull(wes1);
         assertNull(wes2);
         assertNotNull(wes3);
         assertNotNull(max1);
         assertNull(max2);
         assertNotNull(sc1);
         assertNull(sc2);
         assertNotNull(sc3);
         
         // Check that expiration dates are correct
         assertEquals("2013-01-01", trimet1);
         assertEquals("2012-03-02", wes1);
         assertEquals("2013-01-01", wes3);
         assertEquals("2013-01-01", max1);
         assertEquals("2012-03-02", sc1);
         assertEquals("2013-01-01", sc3);
     }
     
     /**
      * Get a date
      * @param year The year, as you would expect, e.g. 2012
      * @param month The month, 1 based, i.e. 1 == January, 6 == June
      * @param day The day of the month, 1 - 31
      * @return A date representation of the given day
      */
     public static Date getDate (int year, int month, int day) {
         Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("gmt"));
         cal.set(year, month, day);
         return cal.getTime();
     }
}
