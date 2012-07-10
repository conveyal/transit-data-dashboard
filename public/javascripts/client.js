function DataController () {
    var instance = this;
    this.data = null;
    // start on the first page
    this.page = 0;

    $.ajax({
        url: '/api/ntdagencies/agencies',
        dataType: 'json',
        success: function (data) {
            console.log('received json');
            instance.data = data;
            
            // hide the next page button if need be
            if (data.length < DataController.PAGE_SIZE)
                $('#nextPage').fadeOut();

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

    // sort the data
    this.data.sort(function (a, b) {
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

    $.each(this.data.slice(pageStart, pageEnd), function (ind, agency) {
        var tr = create('tr');

        // name
        var name = create('td').append(
            create('a')
                .attr('href', agency.url)
                // use .text to prevent potential HTML entities in DB from causing issues
                .text(agency.name)
        );
        tr.append(name);

        // metro
        tr.append(create('td').text(agency.metro));

        // ridership
        tr.append(create('td').text(agency.ridership));
        
        // passenger miles
        tr.append(create('td').text(agency.passengerMiles));

        // population
        tr.append(create('td').text(agency.population));

        // google transit (TODO: icons)
        tr.append(create('td').html(agency.googleGtfs ? 
                                    '<span class="ui-icon ui-icon-check"></span>' : ''));
        
        // public gtfs
        tr.append(create('td').html(agency.publicGtfs ? 
                                    '<span class="ui-icon ui-icon-check"></span>' : ''));

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
        
/* Convenience function to create a detached jQuery DOM object */
function create (tag) {
    return $(document.createElement(tag));
}

$(document).ready(function () {
    /*
    map = new L.Map('map');
    var osm = new L.TileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: 'Map data &copy; OpenStreetMap contributors, CC-BY-SA',
        maxZoom: 18
    });

    var gtfs = new L.TileLayer('http://localhost:8001/{z}/{x}/{y}.png', {
        attribution: 'GTFS data courtesy GTFS Data Exchange'
    });

    map.addLayer(osm);
    map.addLayer(gtfs);

    map.setView(new L.LatLng(40, -100), 4);
    */

    dc = new DataController();
});


    