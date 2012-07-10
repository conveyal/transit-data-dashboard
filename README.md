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

You'll need to get the latest `Agency_Information.xls`, `Service.xls` and `Agency_UZA.xls` files from NTD. Save those as CSV, for instance using LibreOffice. The CSV dialect is not critical; the Python CSV module is very good at detecting the variant of CSV in use and adapting to it. For reference, I used LibreOffice 3.5.3.2 on Ubuntu 12.04. Save those two CSV files in the same directory and then run the `loadAgenciesFromNtd.py` in that same directory. It will create a record for each agency in the NTD data.

### Metro Areas

Metro areas come from NTD's UZAs, with geometries from the Census Bureau. The load process is like this: when you load agencies to the database, one of the columns that is loaded is the agency's UZAs (actually, it's not a column but a relation, but that's beside the point). Using the `mapAgenciesByUZAAndMerge` will merge metro areas that share agencies and map each constituent agency to the appropriate metro. Running `autoNameMetroAreas` after that will clean up the names.

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

`mapAgenciesByUZAAndMerge` - `/api/mapper/mapAgenciesByUZAAndMerge`

Assign transit agencies to metro areas by their UZAs, then merge metro areas that share agencies. You must pass a ?commit=true or ?commit=false to this mapper; it is destructive and, since it is mapping based on free form text, a bit tricky. It is recommended that it be run with ?commit=false and its output reviewed before being run with ?commit=true

## Serving map tiles

You need to install [TileLite](https://bitbucket.org/springmeyer/tilelite/wiki/Home) to serve the map tiles. Then, you can run `liteserv.py -p 8001 path/to/app/dir/tiles/tiles.xml`. Currently, the web map assumes it will find TileLite on localhost:8001.

## Using the web interface

Once you've loaded and linked your data, you can use the dashboard. Load up [http://localhost:9000/public/] in your browser. There you will see the map on the first tab, showing the extent of your metro areas. On the next tab, the data tab, you will see a list of all the NtdAgencies you have loaded. You can sort them by clicking the column heads (click a second time to sort descending). You may also filter your data, by typing a filter expression in the filter text box and pressing enter. Filters have a column name on the left hand side, and operator and a value on the right. There may be no whitespace between column names, operators and right-hand sides, however whitespace may be placed in the right-hand side if one wants to match it. Here are the current column names:

* name
* metro
* ridership
* passengerMiles
* population
* googleGtfs
* publicGtfs

If you want to match one of the boolean columns, use 0 for false and 1 for true.
