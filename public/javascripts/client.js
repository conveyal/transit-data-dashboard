function DataController () {
    var instance = this;
    this.data = null;
    // start on the first page
    this.page = 0;
    this.filters = [];

    $.ajax({
        url: '/api/ntdagencies/agencies',
        dataType: 'json',
        success: function (data) {
            console.log('received json');
            instance.data = data;
            
            // hide the next page button if need be
            if (data.length < DataController.PAGE_SIZE)
                $('#nextPage').fadeOut();

            // there won't be any filters yet, but we still have to do this
            instance.getFilteredData();
            instance.sortBy('metro');
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

    // next page
    $('#nextPage').click(function (e) {
        e.preventDefault();

        instance.page++;
        
        // remove link if need be
        if (((instance.page + 1) * DataController.PAGE_SIZE) >= instance.data.length)
            $('#nextPage').fadeOut();

        // always will be a previous page
        $('#prevPage').fadeIn();

        // re-do sort/display for this screen
        instance.sortBy(instance.sortedBy, instance.descending);
    });

    // next page
    $('#prevPage').click(function (e) {
        e.preventDefault();

        instance.page--;
        
        // remove link if need be
        if (instance.page == 0)
            $('#prevPage').fadeOut();

        // always will be a next page
        $('#nextPage').fadeIn();

        instance.sortBy(instance.sortedBy, instance.descending);
    });

    // hide initially
    $('#prevPage').fadeOut();

    // filter input
    // clear on first keypress
    $('#filterForm input').one('keypress', function (e) {
        $('#filterForm input').val(
            $('#filterForm input').val().replace('New filter', '')
        );
    });

    $('#filterForm').submit(function (e) {
        e.preventDefault();
        instance.parseAndAddFilter($('#filterForm input').val());
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
            instance.sortBy(field);
        }, 2000);
    }

    // sort and filter the data
    this.filteredData.sort(function (a, b) {
        var retval = 0;
        
        if (a[field] == b[field]) retval = 0;
        else if (a[field] < b[field]) retval = -1;
        else if (a[field] > b[field]) retval = 1;
        
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

        // name
        var url = agency.url;

        // TODO: catch non-urls.

        // this will catch https as well
        if (url.slice(0, 4) != 'http')
            url = 'http://' + url;

        var name = create('td').addClass('tblColOdd').append(
            create('a')
                .attr('href', url)
                // use .text to prevent potential HTML entities in DB from causing issues
                .text(agency.name)
        );
        tr.append(name);

        // metro
        tr.append(create('td').text(agency.metro).addClass('tblColEven'));

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
                                    '<span class="ui-icon ui-icon-check"></span>' : '')
                      .addClass('tblColEven'));
        
        // public gtfs
        tr.append(create('td').html(agency.publicGtfs ? 
                                    '<span class="ui-icon ui-icon-check"></span>' : '')
                      .addClass('tblColOdd'));

        $('tbody#data').append(tr);
        
    });

    // indicate sort direction
    this.addSortIndicator();
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
 * Add a new filter
 */
DataController.prototype.parseAndAddFilter = function (filter) {
    // OK to not use > -1, because we also want to exclude filters starting with =
    if (filter.indexOf('=') > 0) {
        var split = filter.split('=');
        var lhs = split[0];
        var rhs = split[1];

        // if the column equals the value
        this.filters.push([filter, function (agency) {
            return agency[lhs] == rhs;
        }]);

    }
    else {
        // no need to redisplay/refilter
        return;
    }

    // redisplay and refilter the data
    this.getFilteredData();
    this.sortBy(this.sortedBy, this.descending);    

    // show the filters
    this.showFilters();
};
    
/** Set up the filters UI */    
DataController.prototype.showFilters = function () {
    var instance = this;

    $('.filter').remove();
    
    $.each(this.filters, function (ind, filter) {
        var link = $('<span class="filter">' + filter[0] + '</span>');
        var close = $('<button>&times;</button>')
            .click(function () {
                instance.filters.pop(ind);
                
                // re filter
                instance.getFilteredData();
                instance.sortBy(instance.sortedBy, instance.descending);

                instance.showFilters();

            })
            .appendTo(link);

        $('#filters').append(link)
    });
};

/**
 * Return a filtered version of the data list
 */
DataController.prototype.getFilteredData = function () {
    // has to be in a closure to have scope access
    var instance = this;

    this.filteredData = this.data.filter(function (agency) {
        return instance.filterCallback(agency)
    });
};

/**
 * Return true if this agency is not filtered
 */
DataController.prototype.filterCallback = function (agency) {
    var instance = this;
    var filterLen = this.filters.length;

    for (var i = 0; i < filterLen; i++) {
        if (!instance.filters[i][1](agency))
            // short circuit
            return false;
    }

    // if we haven't returned by here, it passes muster
    return true;
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
 * A controller for the metro area map
 */
function MapController () {
    this.sizeMapArea();

    this.map = new L.Map('map');

    this.layers = {};

    this.layers.osm = new L.TileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: 'Map data &copy; OpenStreetMap contributors, CC-BY-SA',
        maxZoom: 18
    });

    this.layers.transit = new L.TileLayer('http://localhost:8001/{z}/{x}/{y}.png', {
        attribution: 'GTFS data courtesy GTFS Data Exchange'
    });

    this.map.addLayer(this.layers.osm);
    this.map.addLayer(this.layers.transit);
    this.map.setView(new L.LatLng(40, -100), 4);
}

/**
 * Make the map area as large as possible but no larger
 */
MapController.prototype.sizeMapArea = function () {
    var parent = $('#map').parent();
    
    $('#map')
        .css('width',  parent.innerWidth() + 'px')
        .css('height', $('body').innerHeight() + 'px');
}
    
        
/* Convenience function to create a detached jQuery DOM object */
function create (tag) {
    return $(document.createElement(tag));
}

$(document).ready(function () {
    mc = new MapController();
    dc = new DataController();
});


    