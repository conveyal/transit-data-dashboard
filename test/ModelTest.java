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
import java.util.*;
import play.test.*;
import models.*;
import play.db.jpa.JPA;
import play.db.jpa.Model;

public class ModelTest extends UnitTest {
    @Before
    public void setUp () {
        // TODO: this seems to only delete the ones loaded from fixtures.
        Fixtures.deleteAllModels();
        JPA.em().getTransaction().rollback();
        JPA.em().getTransaction().begin();
        Fixtures.loadModels("relationships.yml");
    }

    // these next 3 functions return a valid instance of the object, unsaved. They are used to
    // test validation: the test receives a valid object, invalidates one field, then tries to 
    // save
    private MetroArea getValidMetroArea () {
        // null geometry for now
        return new MetroArea ("Redding, CA", null);
    }

    private NtdAgency getValidNtdAgency () {
        MetroArea metro = MetroArea.find("byName", "San Francisco-Oakland-San José, CA").first();
        NtdAgency agency = new NtdAgency(
            // name
            "My Test Agency",
            // url
            "http://example.org",
            // ntdId
            "00099",
            // population
            1000,
            // uzaNames
            new ArrayList<String>(),
            // ridership
            1000000,
            // passenger miles
            750000
                                         );

        agency.metroArea = metro;
        return agency;
    }

    private GtfsFeed getValidGtfsFeed () {
        return new GtfsFeed(
            "My Test Agency", // agency name
            "http://valid-gtfs.example.org", // agency url
            "us", // country
            "my-test-agency", // data exchange id
            "http://example.net/my-test-agency", // data exchange url
            new Date(), // date added
            new Date(), // date modified
            null,
            "http://valid-gtfs.example.org/dev", // feed base url
            "http://valid-gtfs.example.org/lic", // license url
            true, // official
            "HI", // state
            "Honolulu, HI", // area description
            null
                            );
    }
        
    // this function returns true if there was a validation exception saving the object
    private boolean throwsValidationException (Model object) {
        object.save();

        // to test: http://stackoverflow.com/questions/156503
        return true;
    }

    // these two test to see if there is leakage between tests
    @Test
    public void testInstantiateToSeeIfAvailableLater () {
        getValidMetroArea().save();
    }

    @Test
    public void testSeeIfStillAvailable () {
        assertEquals(0, MetroArea.find("byName", "Redding, CA").fetch().size());
    }

    @Test
    public void testValidationAndInstantiation () {
        // first, make sure we can save them
        // note, this also confirms we can instantiate them.
        getValidMetroArea().save();
        getValidNtdAgency().save();
        getValidGtfsFeed().save();

        MetroArea metro;
        NtdAgency agency;
        GtfsFeed feed;

        metro = MetroArea.find("byName", "Redding, CA").first();
        assertNotNull(metro);
        assertEquals("Redding, CA", metro.name);

        agency = NtdAgency.find("byUrl", "http://example.org").first();
        assertNotNull(agency);
        assertEquals("My Test Agency", agency.name);
        assertEquals("http://example.org", agency.url);
        assertEquals("00099", agency.ntdId);
        assertEquals(1000, agency.population);
        assertEquals(1000000, agency.ridership);
        assertEquals(750000, agency.passengerMiles);

        feed = GtfsFeed.find("byAgencyUrl", "http://valid-gtfs.example.org").first();
        assertNotNull(feed);
        assertEquals("My Test Agency", feed.agencyName);
        assertEquals("http://valid-gtfs.example.org", feed.agencyUrl);
        assertEquals("us", feed.country);
        assertEquals("my-test-agency", feed.dataExchangeId);
        assertEquals("http://example.net/my-test-agency", feed.dataExchangeUrl);
        // TODO: check date
        assertEquals("http://valid-gtfs.example.org/dev", feed.feedBaseUrl);
        assertEquals("http://valid-gtfs.example.org/lic", feed.licenseUrl);
        assertEquals(true, feed.official);
        assertEquals("HI", feed.state);
        assertEquals("Honolulu, HI", feed.areaDescription);

        // no validation rules on MetroArea class, at least not yet
        // no name
        agency = getValidNtdAgency();
        agency.name = null;
        agency.url = "this is not a url";
        throwsValidationException(agency);
    }

    @Test
    public void testMetroAreaToString () {
        // TODO: assuming the autogenerated IDs are sequential from 0 isn't safe. Something
        // must be done.
        MetroArea sf = MetroArea.find("byName", "San Francisco-Oakland-San José, CA").first();
        MetroArea empty = MetroArea.find("byId", 1L).first();
        MetroArea full = ((NtdAgency) NtdAgency.find("byUrl", "http://metro.kingcounty.gov")
                          .first())
            .metroArea;
        MetroArea fullNoAgencyName = ((NtdAgency) NtdAgency.find("byUrl", "http://example.com")
                                      .first())
            .metroArea;
        assertEquals("San Francisco-Oakland-San José, CA", sf.toString());
        //        assertEquals("Empty metro area", empty.toString());
        assertEquals("Metro including King County Metro", full.toString());
        assertEquals("Metro including http://example.com", fullNoAgencyName.toString());
    }

    @Test
    public void testAgencyFeedRelation () {
        NtdAgency bart = NtdAgency.find("byUrl", "http://www.bart.gov").first();
        NtdAgency kcm = NtdAgency.find("byUrl", "http://metro.kingcounty.gov").first();

        assertNotNull(bart);
        assertNotNull(kcm);

        assertEquals(2, bart.feeds.size());
        assertEquals(2, kcm.feeds.size());
        
        // test that the feeds are allocated correctly and that the shared feed is shared
        GtfsFeed[] bartGtfs = new GtfsFeed[2];
        GtfsFeed[] kcmGtfs = new GtfsFeed[2];
        bart.feeds.toArray(bartGtfs);
        kcm.feeds.toArray(kcmGtfs);

        GtfsFeed bartSingle, kcmSingle, bartMerged, kcmMerged;

        // this is the combined GTFS
        // sort out the arrays
        if (bartGtfs[0].getAgencies().size() == 2) {
            bartMerged = bartGtfs[0];
            bartSingle = bartGtfs[1];
        }
        else {
            bartMerged = bartGtfs[1];
            bartSingle = bartGtfs[0];
        }

        if (kcmGtfs[0].getAgencies().size() == 2) {
            kcmMerged = kcmGtfs[0];
            kcmSingle = kcmGtfs[1];
        }
        else {
            kcmMerged = kcmGtfs[1];
            kcmSingle = kcmGtfs[0];
        }

        assertEquals(bartMerged, kcmMerged);
        assertEquals("http://www.bart.gov", bartSingle.agencyUrl);
        assertEquals("http://metro.kingcounty.gov", kcmSingle.agencyUrl);
    }

    @Test
    public void testMetroAreaHasAgencies () {
        MetroArea sf = MetroArea.find("byName", "San Francisco-Oakland-San José, CA").first();
        
        assertNotNull(sf);
        assertEquals(1, sf.agencies.size());

        for (NtdAgency agency : sf.agencies) {
            assertEquals("BART", agency.name);
            break;
        }
    }

    @Test
    public void testFeedHasAgencies () {
        GtfsFeed mergedFeed = GtfsFeed.find("byAgencyUrl", "http://example.com").first();
        
        assertNotNull(mergedFeed);
        
        assertEquals(2, mergedFeed.getAgencies().size());
    }

    @Test
    public void testBidirectionalRelationships () {
        // TODO: create constructors, maybe project lombok
        NtdAgency agency = new NtdAgency();
        GtfsFeed feed = new GtfsFeed();
        MetroArea metro = new MetroArea();

        agency.name = "The Funicular";
        agency.url = "http://funicular.example.com";
        agency.ntdId = "99991";
        agency.population = 2000;

        feed.country = "Mars";
        feed.feedBaseUrl = "http://funicular.example.com/gtfs";
        feed.official = true;
        feed.agencyUrl = "http://funicular.example.com";

        metro.name = "Los Angeles, CA";

        agency.metroArea = metro;
        assertNotNull(agency.feeds);
        agency.feeds.add(feed);

        // wrt http://stackoverflow.com/questions/8169279
        metro.save();
        agency.save();
        feed.save();

        // confirm that they worked in the direction they were defined
        assertEquals(metro, agency.metroArea);
        // Set does not provide a get method.
        assertEquals(feed, agency.feeds.toArray()[0]);

        // confirm that the bidirectionality worked correctly
        assertEquals(1, metro.agencies.size());
        assertTrue(metro.agencies.contains(agency));

        assertEquals(1, feed.getAgencies().size());
        assertEquals(agency, feed.getAgencies().get(0));
    }

    @Test
    public void testArgumentedConstructors () {
        NtdAgency agency = new NtdAgency("The Funicular", "http://example.net", "09999", 100000, 
        				new ArrayList<String>(), 590000, 4000000);

        agency.save();
        
        assertNotNull(agency.feeds);
        assertEquals(0, agency.feeds.size());
        assertEquals("The Funicular", agency.name);
        assertEquals("09999", agency.ntdId);
        assertEquals(100000, agency.population);
        assertEquals(590000, agency.ridership);
        assertEquals(4000000, agency.passengerMiles);
    }
}
