from urllib2 import urlopen
from sys import argv

with open(argv[1]) as f:
    for line in f:
        url = argv[2] % line[:-1]
        urlopen(url)
