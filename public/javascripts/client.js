$(document).ready(function () {
    map = new L.Map('map');
    var osm = new L.TileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: 'Map data &copy; OpenStreetMap contributors, CC-BY-SA, transit data courtesy' +
            ' GTFS Data Exchange',
        maxZoom: 18
    });

    map.addLayer(osm);

    map.setView(new L.LatLng(40, -100), 4);

    $.ajax({
        url: '/api/metroareas/getAll',
        dataType: 'json',
        success: function (data) {
            console.log('received geojson');
            var geojson = new L.GeoJSON(data);
            map.addLayer(geojson);
        },
        error: function (xhr, textStatus) {
            console.log(textStatus);
        }
    });
});
    