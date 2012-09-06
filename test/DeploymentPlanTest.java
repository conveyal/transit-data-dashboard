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

import java.util.*;
import play.test.*;
import models.*;

public class DeploymentPlanTest extends UnitTest {
	@Before
    public void setUp () {
        // TODO: this seems to only delete the ones loaded from fixtures.
	    Fixtures.deleteAllModels();
        Fixtures.loadModels("planner.yml");
    }
    
    @Test
    public void testMultipleConcurrentFeeds () {
        DeploymentPlan dp;
        MetroArea sf = MetroArea.find("byName", "San Francisco, CA").first();

        // This should include all feeds, because it covers all BART feeds as well as the Muni feed
        dp = new DeploymentPlan(sf, new Date(112, 5, 10), 420);
        
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
        dp = new DeploymentPlan(sf, new Date(112, 9, 10), 20);
        
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
        dp = new DeploymentPlan(sf, new Date(111, 11, 31), 1);
        fd = dp.getFeeds();     
        assertEquals(1, fd.length);
        
        assertEquals("bart1", fd[0].getFeedId());        
    }
    
    /**
     * Test that the deployment planner properly handles the situation where there is a
     * common feed being split to uncommon feeds at different times; keep in mind that "proper"
     * is a relative term here.
     */
    //@Test
    public void testCommonFeedSplit () {
        // note that this implicitly tests Unicode in the DB.
        MetroArea sb = MetroArea.find("byName", "Santa Bárbara, CA").first();
        // sept 10, 2012
        DeploymentPlan dp = new DeploymentPlan(sb, new Date(112, 6, 5), 365);
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
        DeploymentPlan dp = new DeploymentPlan(la, new Date(112, 1, 1), 500);
        
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
}
