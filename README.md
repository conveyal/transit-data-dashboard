# transit-data-dashboard


A dashboard for tracking transit data coverage and updates.

## Loading Data

Data come from a variety of sources, mostly GTFS feeds and the [National Transit Database](http://ntdprogram.gov). GTFS feeds are processed into a JSON-based format by the tools in the [otp_gtfs](https://github.com/demory/otp_gtfs) project maintained by David Emory. Metro areas can be produced in one of a number of ways; the current methodology is to
dissolve the boundaries between touching GTFS feeds and create metro areas that way. That is described in more detail below. Though the data sets are linked in the database, it is generally not
possible to find datasets that are pre-linked, more on that below as well. The general procedure is to load unlinked data and then link it after the load.

### GTFS data

Once you have a JSON file produced by the otp_gtfs tools, you can load it to the database using the `utils/loadFeedsToDashboard.py` script. This script is used like so:

 `loadFeedsToDashboard.py input.json`

It loads to a server running on localhost:9000. It parses the JSON file, reformats it slightly for use in Dashboard, and then hits the API with a request to create a feed record for each feed in the file. 
### Agencies

You'll need to get the latest `Agency_Information.xls` and `Service.xls` files from NTD. Save those as CSV, for instance using LibreOffice. The CSV dialect is not critical; the Python CSV module is very good at detecting the variant of CSV in use and adapting to it. For reference, I used LibreOffice 3.5.3.2 on Ubuntu 12.04. Save those two CSV files in the same directory and then run the `loadAgenciesFromNtd.py` in that same directory. It will create a record for each agency in the NTD data.

### Metro Areas

Metro areas can come from a variety of sources; if you've got a PostGIS DB, then this is the place for you. The `loadAgenciesFromPostGIS.py` script takes a PostGIS table and loads it to the database; you'll likely want to modify it a bit for your specific data. 

The data I loaded was all of the GTFS data dissolved where there are intersections.  following this procedure. I first used the `parseToGeoJSON.js` to convert the data to pure GeoJSON; that script runs in Node.js. That file has file names hard-wired in it; it starts with all.json and outputs all.geojson. I then opened all.geojson in [QGIS](http://qgis.org) and right-clicked on the layer to export as a Shapefile. Then I loaded the Shapefile to PostGIS using the `shp2pgsql-gui` program (I loaded to the table openplans_gtfs.undissolved_gtfs). I then ran the following SQL to dissolve the boundaries:

```sql
-- speeds listed below are from an Athlon64 3200+ with 4GB of RAM
-- running Ubuntu 12.04 and Postgres 9.1.4, PostGIS 1.5
-- add the column to store the group. 45ms.
ALTER TABLE openplans_gtfs.undissolved_gtfs ADD COLUMN area_group int4;

-- simplify the geometries for speedier searches, at the 6th decimal place to match previous truncation


-- create a unique group id for each group, based on the lowest gid of a member agency (15448ms)
UPDATE openplans_gtfs.undissolved_gtfs
SET area_group = (
 SELECT min(gtfs2.gid)
 FROM openplans_gtfs.undissolved_gtfs AS gtfs1,
      openplans_gtfs.undissolved_gtfs AS gtfs2
 WHERE ST_Intersects(gtfs1.the_geom, gtfs2.the_geom) AND gtfs1.gid = undissolved_gtfs.gid
 )

-- re-run this until the db does not change; it expands each group by selecting the lowest group ID of a member group. I ran it 5 times. (~15s each).
UPDATE openplans_gtfs.undissolved_gtfs
SET area_group = (
SELECT min(gtfs2.area_group)
FROM openplans_gtfs.undissolved_gtfs AS gtfs1,
     openplans_gtfs.undissolved_gtfs AS gtfs2
WHERE ST_Intersects(gtfs1.the_geom, gtfs2.the_geom) AND gtfs1.gid = undissolved_gtfs.gid
)

-- now, run this to merge nearby agencies. rerun as necessary. This is slower, which is why we don’t just use it all along. I ran it twice. 10452ms.
-- yes, it uses raw decimal degrees because doing it with geographies is very slow. When data is loaded to the DB, this will be fixed.
UPDATE openplans_gtfs.undissolved_gtfs
SET area_group = (
SELECT min(gtfs2.area_group)
FROM openplans_gtfs.undissolved_gtfs AS gtfs1,
    openplans_gtfs.undissolved_gtfs AS gtfs2
WHERE ST_DWithin(gtfs1.the_geom, gtfs2.the_geom, .04) AND gtfs1.gid = undissolved_gtfs.gid
)

-- check how many groups there are
SELECT count(DISTINCT area_group) FROM openplans_gtfs.undissolved_gtfs;

-- now, make a dissolved view (eventually this will be the urbanareas table). 203ms.
CREATE VIEW openplans_gtfs.dissolved_gtfs AS
SELECT area_group, ARRAY_AGG(name) AS agencies, ST_Union(the_geom) AS the_geom
FROM openplans_gtfs.undissolved_gtfs
GROUP BY area_group;
```

## Linking data

Once you've loaded data, you'll want to link the different datasets together to make them more useful. The program has several tools, which must be used in order. The tool names are not links, because in some cases they are destructive. You'll have to copy and paste the URL.

`mapFeedsToAgenciesByUrl` - `/api/mapper/mapFeedsToAgenciesByUrl`:

This tool parses the URLs on both the feeds and the agencies and tries to map between them. You'll see a report of what matched.

`clearAllAgencyFeedMappings` - `/api/mapper/clearAllAgencyFeedMappings`:

DANGER! Clears *all* agency to feed mappings, regardless of whether the mapper created them.

`mapAgenciesToMetroAreasSpatially` - `/api/mapper/mapAgenciesToMetroAreasSpatially`:

This tries to set the metro area for each agency that has GTFS feeds by looking at what metro area (if any) the GTFS feeds lie in.

`clearAllAgencyMetroAreaMappings` - `/api/mapper/clearAllAgencyMetroAreaMappings`:

DANGER! Clears all agency to metro area mappings, regardless of how they were created.

`autoNameMetroAreas` - `/api/mapper/autoNameMetroAreas`:

Attempt to auto name metro areas based on the feeds in them. DANGER! Will overwrite any already-defined names.
