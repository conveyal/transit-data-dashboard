#!/usr/bin/python

# Load metro areas from the 2000 Census Urban Areas at 
# http://www2.census.gov/cgi-bin/shapefiles2009/national-files

from osgeo import ogr
from sys import argv
from urllib2 import urlopen
from urllib import urlencode

shpdrv = ogr.GetDriverByName('ESRI Shapefile')
uza = shpdrv.CreateDataSource(argv[1])
lay = uza.GetLayerByName(argv[2])

for feature in lay:
    #print name
    #name.encode('utf-8')

#    print feature.GetFieldAsString('NAME').replace('--', '-')

    data = dict(
        name = feature.GetFieldAsString('NAME').replace('--', '-'),

        # Build EWKT
        # census data is NAD 83
        # TODO: should we project to WGS 84 here?
        geometry = 'SRID=4269;' +
            ogr.ForceToMultiPolygon(feature.geometry()).ExportToWkt()
        )

    encoded = urlencode(data)
    urlopen('http://localhost:9000/api/metroareas/create', encoded)
        
