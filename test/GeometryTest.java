import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

import models.FeedParseStatus;
import models.GtfsFeed;
import models.MetroArea;
import models.NtdAgency;
import play.db.jpa.JPA;
import play.test.UnitTest;
import utils.GeometryUtils;


public class GeometryTest extends UnitTest {
    GtfsFeed feedNoAgency, feedWithAgency, feedWithAgencyNoMetro;
    NtdAgency agencyNoMetro, agencyWithMetro;
    MetroArea primaryMetro;
    
    /**
     * This function builds the database.
     */
    @Before
    public void buildDb () {
        JPA.em().getTransaction().rollback();
        JPA.em().getTransaction().begin();
        
        // A feed with no agency
        feedNoAgency = new GtfsFeed();
        feedNoAgency.the_geom = buildGeometry(37, -122);
        feedNoAgency.save();
        
        feedWithAgency = new GtfsFeed();
        feedWithAgency.the_geom = buildGeometry(34, -118);
        feedWithAgency.save();
        
        agencyWithMetro = new NtdAgency();
        agencyWithMetro.feeds.add(feedWithAgency);
        agencyWithMetro.save();
        
        primaryMetro = new MetroArea();
        // notice that this is _not_ spatially equal to the feed extent; otherwise,
        // the autogeom test would have nothing to test
        primaryMetro.the_geom = buildGeometry(34, -118.005);
        primaryMetro.agencies.add(agencyWithMetro);
        primaryMetro.save();
        
        feedWithAgencyNoMetro = new GtfsFeed();
        feedWithAgencyNoMetro.the_geom = buildGeometry(42, -70);
        feedWithAgencyNoMetro.save();
        
        agencyNoMetro = new NtdAgency();
        agencyNoMetro.feeds.add(feedWithAgencyNoMetro);
    }
    
    private void commitBegin() {
        JPA.em().getTransaction().commit();
        JPA.em().getTransaction().begin();
    }
    
    private MultiPolygon buildGeometry (double lat, double lon) {
        return buildGeometry(lat, lon, 1.0);
    }
    
    private MultiPolygon buildGeometry (double lat, double lon, double scale) {
        GeometryFactory gf = GeometryUtils.getGeometryFactoryForSrid(4326);
        Coordinate[] coords = new Coordinate[] {
                new Coordinate(lon, lat),
                new Coordinate(lon + 0.01 * scale, lat),
                new Coordinate(lon + 0.01 * scale, lat + 0.01 * scale),
                new Coordinate(lon, lat + 0.01 * scale),
                new Coordinate(lon, lat)                
        };
        LinearRing ring = gf.createLinearRing(coords);
        LinearRing[] holes = new LinearRing[0];
        Polygon[] polys = new Polygon[] {
                gf.createPolygon(ring, holes)
        };
        
        MultiPolygon out = gf.createMultiPolygon(polys);
        
        return out;
    }
    
    @Test
    public void testAutoGeom () {
        primaryMetro.autogeom();
        assertNotNull(primaryMetro.the_geom);
        // it should take on the geom of its feed(s)
        assertEquals(feedWithAgency.the_geom.toText(), primaryMetro.the_geom.toText());
    }
    
    @Test
    public void testMetroAssignment () {
        // This GTFS feed touches no metro, so everything should be merged
        GtfsFeed feed = new GtfsFeed();
        feed.the_geom = buildGeometry(56, 0);
        feed.status = FeedParseStatus.SUCCESSFUL;
        feed.save();
        
        NtdAgency agency = new NtdAgency(feed);
        agency.feeds.add(feed);
        agency.save();
        
        Set<MetroArea> changed = agency.findAndAssignMetroArea();
        assertEquals(1, changed.size());
        assertEquals(feed.the_geom.toText(), changed.iterator().next().the_geom.toText());
        
        // this feed touches the primary metro; it should be assigned as such
        feed = new GtfsFeed();
        feed.the_geom = buildGeometry(33.99, -118.01, 15);
        feed.status = FeedParseStatus.SUCCESSFUL;
        feed.save();
        
        agency = new NtdAgency(feed);
        agency.feeds.add(feed);
        
        changed = agency.findAndAssignMetroArea();
        
        assertEquals(1, changed.size());
        assertEquals(primaryMetro, changed.iterator().next());
        
        // this feed touches the primary metro and also a non-transit metro, the two metros should
        // be merged
        MetroArea nonTransitMetro = new MetroArea();
        nonTransitMetro.the_geom = buildGeometry(34.06, -118.06);
        nonTransitMetro.save();
        
        feed = new GtfsFeed();
        feed.the_geom = buildGeometry(34, -118, 35);
        feed.status = FeedParseStatus.SUCCESSFUL;
        feed.save();
        
        agency = new NtdAgency(feed);
        agency.feeds.add(feed);
        
        changed = agency.findAndAssignMetroArea();
        
        assertEquals(3, changed.size());
        assertTrue(changed.contains(primaryMetro));
        assertTrue(changed.contains(nonTransitMetro));
        
        assertTrue(primaryMetro.disabled);
        assertTrue(nonTransitMetro.disabled);
        
        // This feed will touch these two transit containing metros
        MetroArea metro1 = new MetroArea();
        agency = new NtdAgency();
        agency.save();
        metro1.agencies.add(agency);
        metro1.the_geom = buildGeometry(55, -94);
        metro1.save();
        
        MetroArea metro2 = new MetroArea();
        agency = new NtdAgency();
        agency.save();
        metro2.agencies.add(agency);
        metro2.the_geom = buildGeometry(55.02, -94.02);
        metro2.save();
        
        feed = new GtfsFeed();
        feed.the_geom = buildGeometry(55, -94, 100);
        feed.status = FeedParseStatus.SUCCESSFUL;
        feed.save();
        
        agency = new NtdAgency(feed);
        agency.feeds.add(feed);
        
        changed = agency.findAndAssignMetroArea();
        
        assertEquals(0, changed.size());
        assertFalse(metro1.disabled);
        assertFalse(metro2.disabled);
    }
}
