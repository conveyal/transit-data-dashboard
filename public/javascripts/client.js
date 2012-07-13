function DataController () {
    var instance = this;
    this.data = null;
    // start on the first page
    this.page = 0;
    this.filters = {};

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

    // reset page number if too high
    var lastPage = Math.ceil(this.filteredData.length / DataController.PAGE_SIZE);
    if (this.page > lastPage)
        // - 1 to convert to 0-based
        this.page = lastPage - 1;

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

    this.filteredData = this.data.filter(function (agency) {
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
 * A controller for the metro area map
 */
function MapController () {
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


    