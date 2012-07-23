
fs = require('fs')
$ = require('jquery')

var doc = $(fs.readFileSync('google_transit.html').toString());

allAreas = doc.find('li#us ol li ol li');

function parseUrl (url) {
    //console.log(url);
    var result = /(?:ll=)(-?[0-9]+\.[0-9]+)(?:,)(-?[0-9]+\.[0-9]+)/.exec(url);
    return {lat: result[1], lon: result[2]};
}

$.each(allAreas, function (ind, area) {
    area = $(area);

    var theA = area.find('a').first();

    if (theA.length == 0) {
        console.log('No spatial data found for area ' + area.text());
        return;
    }
    
    var latlng = parseUrl(area.find('a').attr('href'));
    area.find('a').remove();

    $.each(area.text().split(','), function (ind, agencyName) {
        var local = $.extend({}, latlng);
        local.name = agencyName.replace('\n', '').trim();
        if (latlng.name == '')
            return;

        $.ajax({
            url: 'http://localhost:9000/api/mapper/setGoogleGtfsFromParse',
            method: 'GET',
            data: local,
            dataType: 'json',
            success: function (data) {
                if (data.length == 0)
                    console.log('WARN: no match for agency ' + local.name);
                else {
                    console.log('Agency ' + local.name + ' matched ' + data.length + ' agencies:');
                    $.each(data, function (ind, agency) {
                        console.log(' - ' + agency.name + ' in ' + agency.metro);
                    });
                }
            },
            error: function (xhr, textStatus, errorThrown) {
                console.log('Error updating ' + local.name + ': ' + errorThrown + ': ' + textStatus);
            }
        });
    });
});
    