#!/usr/bin/python

import json
from urllib2 import urlopen
from urllib import urlencode
from sys import argv
import geojson
from shapely.geometry import asShape
from datetime import datetime, timedelta

if len(argv) != 2:
    print 'Usage: %s input.json' % argv[0]
    exit()

infile = open(argv[1])
data = json.load(infile)

def toCountry(c):
    "Convert to a international country code"
    if c == None: return None

    if c == 'United States':
        return 'us'
    elif c == 'Canada':
        return 'ca'

states = dict(
    alabama='al',
    arizona='az',
    arkansas='ak',
    california='ca',
    colorado='co',
    connecticut='ct',
    delaware='de',
    florida='fl',
    hawaii='hi',
    illinois='il',
    indiana='in',
    kansas='ks',
    kentucky='ky',
    maryland='md',
    massachusetts='ma',
    michigan='mi',
    minnesota='mn',
    missouri='mo',
    montana='mt',
    nevada='nv',
    ohio='oh',
    oregon='or',
    pennsylvania='pa',
    tennessee='tn',
    texas='tx',
    utah='ut',
    virginia='va',
    washington='wa',
    wisconsin='wi'
)
states['new jersey'] = 'nj'
states['new york'] = 'ny'
states['north carolina'] = 'nc'
states['south carolina'] = 'sc'
states['washington dc'] = 'dc'

def toState(state):
    if state == None: return None

    state = state.lower()
    if states.has_key(state):
        return states[state]
    else:
        print 'Unmatched state %s' % state
        return None
    
def to8601(ts):
    if ts == None: return None

    dt = datetime.utcfromtimestamp(ts)
    return dt.isoformat() + 'Z'

def toEWKT(gj):
    "Convert geojson to EWKT"
    mp = geojson.MultiPolygon(gj['coordinates'])
    return 'SRID=4326;' + asShape(mp).wkt
    
def confirmOrWarn(info, key, default):
    if not info.has_key(key):
        print "Agency %s has no key %s, setting to %s" % (info['name'], key, default)
        info[key] = default

for feed in data:
    i = feed['info']

    confirmOrWarn(i, 'url', None)
    confirmOrWarn(i, 'country', None)
    confirmOrWarn(i, 'state', None)
    confirmOrWarn(i, 'dataexchange_id', None)
    confirmOrWarn(i, 'dataexchange_url', None)
    confirmOrWarn(i, 'date_added', None)
    confirmOrWarn(i, 'date_last_updated', None)
    confirmOrWarn(i, 'feed_baseurl', None)
    confirmOrWarn(i, 'license_url', None)
    confirmOrWarn(i, 'is_official', None)
    confirmOrWarn(i, 'area', None)

    generation_date = datetime(2012, 07, 15, 12, 0, 0)

    post = dict(
        agency_name = i['name'],
        agency_url = i['url'],
        country = toCountry(i['country']),
        data_exchange_id = i['dataexchange_id'],
        data_exchange_url = i['dataexchange_url'],
        date_added = to8601(i['date_added']),
        date_updated = to8601(i['date_last_updated']),
        expiration_date = (generation_date + timedelta(i['days_to_expiration'])).isoformat() + 'Z',
        feed_base_url = i['feed_baseurl'],
        license_url = i['license_url'],
        official = i['is_official'],
        state = toState(i['state']),
        area_description = i['area'],
        geometry = toEWKT(feed['geom'])
    )

    for key in post.keys():
        # http://mail.python.org/pipermail/tutor/2007-May/054340.html
        try:
            post[key] = post[key].encode('utf-8')
        except AttributeError:
            # boolean type has no attribute encode
            pass

        if post[key] == None:
            del post[key]

    postData = urlencode(post)
    
    urlopen('http://localhost:9000/api/gtfsfeeds/create', postData)


    
        
