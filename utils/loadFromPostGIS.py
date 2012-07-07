from psycopg2 import connect
from urllib import urlencode
from urllib2 import urlopen

conn = connect(database='matthewc')
cursor = conn.cursor()

cursor.execute('SELECT agencies, ST_AsEWKT(the_geom) FROM openplans_gtfs.dissolved_gtfs')

for line in cursor:
    req = dict(name=line[0][0], # use the first agency in the area as the name
               geometry=line[1])
    data = urlencode(req)
    urlopen('http://localhost:9000/api/metroareas/create', data)
