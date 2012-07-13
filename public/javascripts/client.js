function DataController (mapController) {
    var instance = this;
    this.data = null;
    // start on the first page
    this.page = 0;
    this.filters = {};
    this.mapController = mapController;

    $.ajax({
        url: '/api/ntdagencies/agencies',
        dataType: 'json',
        success: function (data) {
            console.log('received json');
            instance.data = data;

            // lose the loading text
            $('#loading').remove();
            
            // hide the next page button if need be
            if (data.length < DataController.PAGE_SIZE)
                $('#nextPage').fadeOut();

            // there won't be any filters yet, but we still have to do this
            instance.getFilteredData();
            instance.sortBy('metro', false);
        },
        error: function (xhr, textStatus) {
            console.log('Error retrieving JSON: ', textStatus);
        }
    });

    // set up the sort buttons
    $('.sortBtn').click(function (e) {
        // don't go to the top of the page
        e.preventDefault();

        // e.currentTarget is a DOM element not a jQ        
        var field = e.currentTarget.name;
        var desc = false;

        // two clicks on the head sorts descending
        if (instance.sortedBy == field && instance.descending == false)
            desc = true;

        instance.sortBy(e.currentTarget.name, desc);
    });

    $('#filters li a').click(function (e) {
        var filter = e.currentTarget.name;
        var opposite = e.currentTarget.getAttribute('opposite');

        if (instance.filters[filter]) {
            instance.filters[filter] = false;
            $(e.currentTarget).find('.ui-icon').removeClass('ui-icon-check')
                .addClass('ui-icon-blank')
                .text('Disabled filter');
        }
        else {
            instance.filters[filter] = true;
            $(e.currentTarget).find('.ui-icon').addClass('ui-icon-check')
                .removeClass('ui-icon-blank')
                .text('Enabled filter');
        }

        if (opposite != null) {
            $('#filters li').find('[name="' + opposite + '"]')
                .find('.ui-icon').removeClass('ui-icon-check')
                .addClass('ui-icon-blank')
                .text('Disabled filter');

            instance.filters[opposite] = false;
        }

        instance.getFilteredData();
        instance.sortBy(instance.sortedBy, instance.descending);
    });

    // hide a shown agency upon request
    $('#agencyClose').click(function (e) {
        e.preventDefault();
        
        $('.tabButton').fadeIn();

        $('#agencyInfo').hide('drop');
        $('#tabs').fadeIn();
    });
        
}

// STATIC CONFIG
DataController.PAGE_SIZE = 100;

DataController.prototype.sortBy = function (field, desc) {
    var instance = this;
    this.sortedBy = field;
    this.descending = desc;

    // app has not initialized yet, try again in 2s
    if (this.data == null) {
        setTimout(function () {
            instance.sortBy(field, desc);
        }, 2000);
    }

    // reset page number if too high
    var lastPage = Math.ceil(this.filteredData.length / DataController.PAGE_SIZE);
    if (this.page > lastPage)
        // - 1 to convert to 0-based
        this.page = lastPage - 1;

    console.log(this.filteredData.length);

    // sort and filter the data
    this.filteredData.sort(function (a, b) {
        var retval = 0;
        
        var aField = (a[field] != undefined ? a[field] : '');
        var bField = (b[field] != undefined ? b[field] : '');

        if (aField == bField) retval = 0;
        else if (aField < bField) retval = -1;
        else if (aField > bField) retval = 1;
        
        if (desc)
            return -1 * retval;
        else
            return retval;
    });

    // clear old data
    $('tbody#data tr').remove();

    // pagination
    var pageStart = this.page * DataController.PAGE_SIZE;
    var pageEnd = pageStart + DataController.PAGE_SIZE;

    $('#pageNum').text(this.page + 1);

    $.each(this.filteredData.slice(pageStart, pageEnd), function (ind, agency) {
        var tr = create('tr');

        // alternate classes
        tr.addClass(ind % 2 ? 'tblRowEven' : 'tblRowOdd');

        var name = create('td').addClass('tblColOdd').append(
            create('a')
                .attr('href', '#')
                // use .text to prevent potential HTML entities in DB from causing issues
                .text(agency.name)
                .data('id', agency.id)
                .click(function (e) {
                    e.preventDefault();
                    
                    instance.showAgency($(this).data('id'));
                })
        );
        tr.append(name);

        // metro
        var metro = create('td').addClass('tblColEven');
        var metroLink = create('a')
            .attr('href', '#')
            .text(agency.metro)
            .data('lat', agency.lat)
            .data('lon', agency.lon)
            .click(function (e) {
                e.preventDefault();
                
                var a = $(this);
                // switch to the map tab
                $('#mapTabToggle').click();
                
                // we need to wait until the click has propagated before doing this
                setTimeout(function () {
                    instance.mapController.zoomTo(a.data('lat'), a.data('lon'), 10);
                }, 1000);
            });

        metro.append(metroLink).appendTo(tr);

        // ridership
        tr.append(create('td').text(DataController.formatNumber(agency.ridership))
                      .addClass('tblColOdd'));
        
        // passenger miles
        tr.append(create('td').text(DataController.formatNumber(agency.passengerMiles))
                      .addClass('tblColEven'));

        // population
        tr.append(create('td').text(DataController.formatNumber(agency.population))
                      .addClass('tblColOdd'));

        // google transit (TODO: icons)
        tr.append(create('td').html(agency.googleGtfs ? 
                                    '<span class="ui-icon ui-icon-check">Yes</span>' : 
                                    '<span class="ui-icon ui-icon-blank">No</span>')
                      .addClass('tblColEven'));
        
        // public gtfs
        tr.append(create('td').html(agency.publicGtfs ? 
                                    '<span class="ui-icon ui-icon-check">Yes</span>' :
                                    '<span class="ui-icon ui-icon-blank">No</span>')
                      .addClass('tblColOdd'));

        $('tbody#data').append(tr);
        
    });

    // indicate sort direction
    this.addSortIndicator();
    this.setUpPagination();
}

// now, add the indicator showing the sort column and direction
DataController.prototype.addSortIndicator = function () {
    // get rid of old ones
    $('.sortIndicator').remove();

    var colHead = $('a.sortBtn[name=' + this.sortedBy + ']');

    // add a descending icon
    if (this.descending)
        colHead.append('<span class="sortIndicator ui-icon ui-icon-triangle-1-s"></span>');
    else
        colHead.append('<span class="sortIndicator ui-icon ui-icon-triangle-1-n"></span>');
}

/**
 * Return a filtered version of the data list
 */
DataController.prototype.getFilteredData = function () {
    // has to be in a closure to have scope access
    var instance = this;

    // use the jQuery function not array.filter for browser that don't implement
    // that part of the spec (IE 8)
    this.filteredData = $.grep(this.data, function (agency) {
        return instance.filterCallback(agency)
    });
};

/**
 * Return true if this agency is not filtered
 */
DataController.prototype.filterCallback = function (agency) {
    // various filters
    if (this.filters.publicGtfs) {
        if (!agency.publicGtfs) {
            return false;
        }
    }

    if (this.filters.noPublicGtfs) {
        if (agency.publicGtfs) {
            return false;
        }
    }
    
    // if we're here it passes muster
    return true;
};

/**
 * Set up proper pagination
 */
DataController.prototype.setUpPagination = function () {
    var instance = this;

    $('#page ul li').remove();
    
    // calculate the number of pages
    var numPages = Math.ceil(this.filteredData.length / DataController.PAGE_SIZE);
    
    for (var i = 0; i < numPages; i++) {
        var li = create('li');
        var a = $('<a href="#">' + (i + 1) + '</a>')
            .data('pagenumber', i)
            .click(function () {
                instance.page = $(this).data('pagenumber');
                instance.sortBy(instance.sortedBy, instance.descending);
            });

        if (i == instance.page)
            li.addClass('active');

        li.append(a);
        $('#page ul').append(li);
    }
};
                

// Static Functions
/**
 * Format a large integer
 */
DataController.formatNumber = function (number) {
    number = '' + number;
    numLen = number.length;
    output = '';

    for (var i = 0; i < numLen; i++) {
        // the position in a number group i.e. 123,452,932 is three groups
        posInGroup = (numLen - i) % 3;

        if (posInGroup == 0 && i != 0) {
            output += ',';
        }

        output += number[i];
    }

    return output;
}

/**
 * Make a URL valid by adding a protocol if needed
 */
DataController.validUrl = function (url) {
    if (url.indexOf('://') == -1)
        url = 'http://' + url;
    return url;
};

DataController.prototype.showAgency = function (id) {
    $.ajax({
        url: '/api/ntdagencies/agency',
        data: {id: id},
        dataType: 'json',
        success: function (agency) {
            $('.tabButton').fadeOut();

            $('#agencyName').text(agency.name);

            $('#agencyDownload').attr('href', '/api/ntdagencies/agency?id=' + agency.id);

            $('#agencyUrl').html('<a href="http://' + DataController.validUrl(agency.url) + '">' + 
                                 agency.url + '</a>');
            $('#agencyNtdId').text(agency.ntdId);
            $('#agencyRidership').text(DataController.formatNumber(agency.ridership));
            $('#agencyPassengerMiles').text(DataController.formatNumber(agency.passengerMiles));
            $('#agencyPopulation').text(DataController.formatNumber(agency.population));
            
            // and the feeds
            $('.feedFields').remove();
            $.each(agency.feeds, function (ind, feed) {
                // color-code the feed by expiration status
                var status;
                var now = new Date();
                var later = new Date();
                var expires = new Date(Date.parse(feed.expires));
                // if it expires in under 2 months
                later.setMonth(later.getMonth() + 2);
                if (expires < now)
                    status = 'feedExpired';
                else if (expires < later)
                    status = 'feedToExpire';
                else
                    // just black, don't use red and green together.
                    status = 'feedOk';

                var expireText = ['January', 'February', 'March', 'April', 'May', 'June',
                                  'July', 'August', 'September', 'October', 'November', 
                                  'December'][expires.getMonth()] + ' ' + expires.getDate() +
                    ', ' + expires.getFullYear();

                $('#agencyFeeds').append(
                    '<li class="feedFields"><table>' +
                        '<tr>' +
                          '<th>Agency Name</th>' +
                          '<td>' + feed.agencyName + '</td>' +
                        '</tr>' +
                        '<tr>' +
                          '<th>Agency URL</th>' +
                          '<td><a href="' + DataController.validUrl(feed.agencyUrl) + '">' + 
                             feed.agencyUrl + '</a></td>' +
                        '</tr>' +
                        '<tr>' +
                          '<th>Feed Base URL</th>' +
                          '<td><a href="' + DataController.validUrl(feed.feedBaseUrl) + '">' + 
                             feed.feedBaseUrl + '</a></td>' +
                        '</tr>' +
                        '<tr>' +
                          '<th>Expires on</th>' +
                          '<td class="' + status + '">' + expireText + '</td>' +
                        '</tr>' +
                        '<tr>' +
                          '<th>Official</th>' +
                          '<td>' + (feed.official ? 'Yes' : 'No') + '</td>' +
                        '</tr>' +
                     '</table></li>'
                );
            });

            if (agency.feeds.length == 0)
                $('#agencyFeeds')
                    .append('<span class="feedFields">No public GTFS available.</span>');


            $('#tabs').hide();
            $('#agencyInfo').show('drop');
        }
    });
};

/**
 * A controller for the metro area map
 */
function MapController () {
    var instance = this;

    this.sizeMapArea();

    this.map = new L.Map('map');

    this.layers = {};

    this.layers.osm = new L.TileLayer('http://{s}.tiles.mapbox.com/v3/openplans.map-g4j0dszr/{z}/{x}/{y}.png', {
        attribution: 'Map data &copy; OpenStreetMap contributors, CC-BY-SA',
        maxZoom: 18
    });

    this.layers.transit = new L.TileLayer('http://localhost:8001/{z}/{x}/{y}.png', {
        attribution: 'GTFS data courtesy GTFS Data Exchange'
    });

    this.map.addLayer(this.layers.osm);
    this.map.addLayer(this.layers.transit);
    this.zoomTo(40, -100, 4);

    // https://groups.google.com/forum/?fromgroups#!topic/leaflet-js/2QN0diKp5UY
    $('#mapTab').on('shown', function () {
        instance.sizeMapArea();
    });
}

/**
 * Make the map area as large as possible but no larger
 */
MapController.prototype.sizeMapArea = function () {
    var parent = $('#map').parent();
    
    $('#map')
        .css('width',  parent.innerWidth() + 'px')
        .css('height', $('body').innerHeight() + 'px');

    if (this.map != undefined) {
        this.map.invalidateSize();
    }
};
    
/**
 * Zoom the map to specific place
 * @param {Number} lat Center the map here
 * @param {Number} lon and here
 * @param {Number} zoom The zoom level
 */
MapController.prototype.zoomTo = function (lat, lng, zoom) {
    this.map.setView(new L.LatLng(lat, lng), zoom);
};
        
/* Convenience function to create a detached jQuery DOM object */
function create (tag) {
    return $(document.createElement(tag));
}

$(document).ready(function () {
    mc = new MapController();
    dc = new DataController(mc);
});


    