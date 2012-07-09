#!/usr/bin/python

# loadAgenciesFromNtd.py: load all the agencies in the National Transit Database to Transit Data
# Dashboard

import csv
from urllib2 import urlopen
from urllib import urlencode, quote_plus
import json

# a table row, with ntdId as index
ntdData = dict()

# First pass: read the main DB
reader = csv.DictReader(open('Agency_Information.csv'))

for row in reader:
    # Process population
    pop = row['Service_Area_Population'].strip().replace(',', '')
    if pop == '':
        print 'Invalid population "%s" for agency %s' %\
            (row['Service_Area_Population'], row['Company_Nm'].strip())
        pop = 0
    else:
        pop = int(pop)

    # This is set up for column names in NTD 2010
    ntdData[row['Trs_Id'].strip()] = dict(
        name = row['Company_Nm'].strip(),
        url = row['Url_Cd'].strip(),
        ntdId = row['Trs_Id'].strip(),
        population = pop
        # The rest come from the Service table
        )

# Second pass: read the service table
serviceReader = csv.DictReader(open('Service.csv'))
for row in serviceReader:
    if row['Time_Period_Desc'].strip() == 'Annual Total':
        ntdId = row['Trs_Id'].strip()
        
        if not ntdData.has_key(ntdId):
            print 'Service table has nonexistent id %s' % ntdId
            continue
        
        # There is no modal overlap; modes are documented on page 20 of
        # http://www.ntdprogram.gov/ntdprogram/pubs/ARM/2011/pdf/2011_Introduction.pdf
        
        # Make sure there is a valid number (not all have them), then increment the values
        # create the columns if needed
        ridershipForMode = row['Unlinked_Passenger_Trips'].strip().replace(',', '')
        if ridershipForMode != '':
            if not ntdData[ntdId].has_key('ridership'):
                ntdData[ntdId]['ridership'] = 0

            ntdData[ntdId]['ridership'] += int(ridershipForMode)

        else:
            # Nothing we can do about invalid data, tell the user
            print 'Warning: invalid value "%s" for trips for agency %s for mode %s' %\
                (row['Unlinked_Passenger_Trips'], ntdData[ntdId]['name'], row['Mode_Cd'])

        milesForMode = row['Passenger_Miles'].strip().replace(',', '')
        if milesForMode != '':
            if not ntdData[ntdId].has_key('passenger_miles'):
                ntdData[ntdId]['passenger_miles'] = 0

            ntdData[ntdId]['passenger_miles'] += int(milesForMode)

        else:
            print 'Warning: invalid value "%s" for miles for agency %s for mode_cd %s' %\
                (row['Passenger_Miles'], ntdData[ntdId]['name'], row['Mode_Cd'])

# Third pass: assign UZAs
uzaReader = csv.DictReader(open('Agency_UZAs.csv'))
for row in uzaReader:
    trsId = row['Trs_Id'].strip()
    uzaName = row['UZA_Name'].strip()
    
    if uzaName == 'Non-UZA':
        continue

    if not ntdData.has_key(trsId):
        print 'TRS ID %s is referenced in UZAs table but does not exist' % trsId
        continue

    if not ntdData[trsId].has_key('uzaNames'):
        ntdData[trsId]['uzaNames'] = []

    if uzaName not in ntdData[trsId]['uzaNames']:
        ntdData[trsId]['uzaNames'].append(uzaName)

# Fourth pass: upload
for agency in ntdData.values():
    # save out uzaNames and hand-encode them
    if agency.has_key('uzaNames'):
        uzaNames = agency['uzaNames']
        del agency['uzaNames']

    # an empty list; nothing will be sent to the server
    else:
        uzaNames = []

    # Fix Unicode issues
    for key in agency:
        # They'll be stringified in request anyhow
        agency[key] = str(agency[key]).encode('utf-8')

    data = urlencode(agency)
    
    # hand encode uzaNames
    for name in uzaNames:
        data += '&uzaNames=' + quote_plus(name)

    stat = urlopen('http://localhost:9000/api/ntdagencies/create', data)
    
    if json.load(stat)['status'] != 'success':
        print 'Agency %s failed to load' % agency['name']
