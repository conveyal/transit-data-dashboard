# Find UZAs that should be merged

import csv

reader = csv.DictReader(open('Agency_UZAs.csv'))

uzaAllocation = dict()
allUzas = []

for row in reader:
    trsId = row['Trs_Id'].strip()
    uza = row['UZA_Name'].strip()

    if uza == 'Non-UZA':
        continue

    if not uzaAllocation.has_key(trsId):
        uzaAllocation[trsId] = []

    # regardless of whether it's new or not, add it to the list if it isn't 
    # there already
    if not uza in uzaAllocation[trsId]:
        uzaAllocation[trsId].append(uza)

    if not uza in allUzas:
        allUzas.append(uza)

# get all the significant ones
uzaAllocation = dict([(u, uzaAllocation[u]) for u in uzaAllocation 
                       if len(uzaAllocation[u]) > 1])
usedUzas = []

merged = uzaAllocation.values()

changed = True
iterations = 0
while changed:
    changed = False
    iterations += 1

    thelength = range(len(merged))
    print 'iter %s, length %s' % (iterations, len(merged))
    for i in thelength:
        if merged[i] == None:
            continue

        for uza in merged[i]:
            for j in thelength:
                #print i, j, merged[i], merged[j]
                if i != j and merged[j] != None and uza in merged[j]:
                    print 'merging'
                    for uza2 in merged[j]:
                        if not uza2 in merged[i]:
                            merged[i].append(uza2)
                    merged[j] = None # will be del'd later
                    changed = True
        
    merged = [i for i in merged if i != None]

# output
def mergeNames(area):
    cities = []
    states = []

    for i in area:
        city, state = i.split(', ')

        for j in city.split('-'):
            if not j in cities:
                cities.append(j)
        
        for j in state.split('-'):
            if not j in states:
                states.append(j)

    return '-'.join(cities) + ', ' + '-'.join(states)

print 'Conglomerate Metro Areas:'

i = 1
for area in merged:
    i += 1
    print mergeNames(area)



    


