#!/usr/bin/python

# makeStatic.py: make a running install of transit data dashboard static

from os import system, chdir, mkdir
import json

host = 'localhost:9000'

# This gets all the static files
system('wget -r http://' + host)
chdir(host + '/api/ntdagencies')
system('wget http://' + host + '/api/ntdagencies/agencies')

# this is already downloaded
agencies = json.load(open('agencies/JSON'))

mkdir('agency')
chdir('agency')

for agency in agencies:
    system('wget http://' + host + '/api/ntdagencies/agency/' + str(agency['id']))
