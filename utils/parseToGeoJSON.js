var fs = require('fs');

// http://geojson.org/geojson-spec.html#named-crs
var wgs84 = {
    "type": "name",
    "properties": {
        "name": "urn:ogc:def:crs:OGC:1.3:CRS84"
    }
};


var raw = fs.readFileSync('all.json')
var data = JSON.parse(raw);
var theLen = data.length;
for (var i = 0; i < theLen; i++) {
    // rename geometry
    data[i].geometry = data[i].geom;
    data[i].geom = undefined;
    
    // rename properties
    data[i].properties = data[i].info;
    data[i].info = undefined;
    
    // add a unique id
    data[i].id = data[i].properties.dataexchange_id;

    // it's a feature
    data[i].type = 'Feature';
}

var container = {
    "type": "FeatureCollection",
    "features": data,
    "crs": wgs84
};

var reformatted = JSON.stringify(container);
fs.writeFileSync('all.geojson', reformatted);

        