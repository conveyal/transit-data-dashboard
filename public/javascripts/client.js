/* 
  This program is free software: you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public License
  as published by the Free Software Foundation, either version 3 of
  the License, or (props, at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program. If not, see <http://www.gnu.org/licenses/>. 
*/

function DataController (mapController) {
    var instance = this;
    this.data = null;
    this.voteData = null;
    this.votedForAgencies = [];
    // start on the first page
    this.page = 0;
    this.filters = {};
    this.mapController = mapController;

    // the reason for two API calls is that agencies change infrequently
    // and can be cached, while votes change frequently but can be cheaply
    // retrieved from the DB
    var agenciesdf = //new $.Deferred();
    $.ajax({
        url: 'api/ntdagencies/agencies/JSON',
        dataType: 'json',
        success: function (data) {
        	instance.data = data;
        	//agenciesdf.resolve();
        }        	
    });
    
    var votesdf = //new $.Deferred();
    $.ajax({
    	url: DataController.API_LOCATION + 'getvotes/' +
    		DataController.VOTE_NAMESPACE,
    	dataType: 'json',
    	success: function (data) {
    		instance.voteData = data;
    		//votesdf.resolve();
    	}
    });
    
    $.when(votesdf, agenciesdf).done(function () {
     	// do the join
    	var agencyLen = instance.data.length;
    	var agency;
    	for (var i = 0; i < agencyLen; i++) {
    		agency = instance.data[i];
    		agency.votes = instance.voteData[agency.id];
    		if (agency.votes == undefined) 
    			agency.votes = 0;
    	}
    	
    	// there won't be any filters yet, but we still have to do this
    	instance.getFilteredData();
    	instance.sortBy('ridership', true);
    	
    	// lose the loading text
    	$('#loading').remove();
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

// The location of the vote API.
DataController.API_LOCATION = "http://localhost:9200/";
DataController.VOTE_NAMESPACE = "gtfsDataDashboard";

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
        
        // votes
        var votes = create('td').addClass('tblColEven');
        
        // we only do votes on agencies with no public gtfs
        if (!agency.publicGtfs) {
        	votes.append('<span class="numVotes float-left">' + agency.votes + '</span>');
        	// and the upvote button
        	var upvote = create('a').attr('href', '#')
        		.addClass('float-left')
        		.addClass('icon-thumbs-up')
        		.attr('title', 'Vote for ' + agency.name + ' to release their data!')
        		.data('id', agency.id)
        		.click(function (e) {
        			e.preventDefault();
        			
        			var voteButton = $(this);
        			var id = voteButton.data('id');
        			
        			// prevent multiple voting
        			if (instance.votedForAgencies.indexOf(id) == -1) {
        				$('.popover').remove();
        				
        				// pop up the popover, using Bootstrap
        				voteButton.popover({
        					// we want it on the left so it's over the table not
        					// off the screen
        					placement: 'left',
        					trigger: 'manual',
        					title: "",
        					content: 
        						'<div class="hidden508">Begin simulated dialog</div>' +
        						'<form id="voteform" class="control-horizontal" action="' + DataController.API_LOCATION + 
        						'upvote" method="get">' +
        						'<div class="alert alert-success">' +
        						"  If you'd like, give us some more information about yourself. " +
        						'</div>' +
        						'<div class="control-group">' +
        						'  <label class="control-label" for="user-name">Name</label>' +
        						'  <div class="controls">' +
        						'    <input type="text" id="user-name" name="name" />' +
        						'  </div>' +
        						'</div>' +
        						'<div class="control-group">' +
        						'  <label class="control-label" for="user-email">Email</label>' +
        						'  <div class="controls">' +
        						'    <input type="text" id="user-email" name="email" />' +
        						'  </div>' +
        						'</div>' +
        						'<div class="control-group">' +
        						'  <div class="controls">' +
        						// TODO: is this the proper 508 way to do things?
        						'    <label class="checkbox">' +
        						'      <input type="checkbox" name="takesLocalTransit" />' +
        						'      I ride transit in this area' +
        						'    </label>' +
        						'    <label class="checkbox">' +
        						'      <input type="checkbox" name="canEmail" />' +
        						'      <a href="http://www.openplans.org">OpenPlans</a> ' + 
        						'      may email me about open data initiatives in my community ' +
        						'      and around the world' +
        						'    </label>' +
        						'    <input type="hidden" name="namespace" value="' + DataController.VOTE_NAMESPACE + '" />' +
        						'    <input type="hidden" name="key" value="' + id + '" />' +
        						'    <div class="btn-group">' +
        						'      <button type="submit" class="btn btn-primary">Register my vote</button>' +
        						'      <button class="btn" id="closeform">Cancel</button>' +
        						'    </div>' +
        						'  </div>' +
        						'</div>' +
        						'</form>' +
        						'<div class="hidden508">End simulated dialog</div>'
        				});
        				$(this).popover('show');
        				// focus element for accessibility
    					$('.popover-title').attr('tabindex', '-1').focus();
        			
        				// make them think it happened right away, even if it didn't, to prevent
        				// repeated clicks
        				var numVotes = $(this).parent().find('.numVotes');
        				$('#voteform').submit(function (e) {
        					e.preventDefault();
        					$.ajax({
        						url: DataController.API_LOCATION + 'upvote?' +
        							$(this).serialize()
        					});
        					// make them think it happened right away
            				numVotes.text(Number(numVotes.text()) + 1);
            				instance.votedForAgencies.push(id);
            				voteButton.popover('hide');
            				
            				// restore focus
            				voteButton.focus();
            				
            				$('#voteform').remove();
        				});
        				
        				$('#closeform').click(function (e) {
        					e.preventDefault();
        					voteButton.popover('hide');
        					
            				voteButton.focus();
        					
        					$('#voteform').remove();
        				});
        			}
        		});
        	votes.append(upvote);
        	votes.append('<div class="clear"></div>');
        }

        tr.append(votes);
        
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
        colHead.append('<i class="sortIndicator ui-icon ui-icon-triangle-1-s"></i>');
    else
        colHead.append('<i class="sortIndicator ui-icon ui-icon-triangle-1-n"></i>');
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
    
    // google gtfs
    if (this.filters.googleGtfs) {
        if (!agency.googleGtfs) {
            return false;
        }
    }

    if (this.filters.noGoogleGtfs) {
        if (agency.googleGtfs) {
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
	// 0 === N/A in this case
	if (number === 0)
		return '';
	
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
	if (typeof url == 'undefined')
		return '';
	
    if (url.indexOf('://') == -1)
        url = 'http://' + url;
    return url;
};

DataController.prototype.showAgency = function (id) {
    $.ajax({
        url: 'api/ntdagencies/agency/' + id,
        dataType: 'json',
        success: function (agency) {
            $('.tabButton').fadeOut();

            $('#agencyName').text(agency.name);

            $('#agencyDownload').attr('href', 'api/ntdagencies/agency/' + agency.id);

            $('#agencyUrl').html('<a href="' + DataController.validUrl(agency.url) + '">' + 
                                 agency.url + '</a>');
            $('#agencyGoogle').text((agency.googleGtfs ? 'Yes' : 'No'));
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
                if (!isNaN(expires.getDate())) {
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
                }
                else {
                	var expireText = '';
                }

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
                        '<tr>' +
                          '<th>Valid</th>' +
                          '<td>' + feed.status + '</td>' +
                        '</tr>' +
                     '</table></li>'
                );
            });

            if (agency.feeds.length == 0)
                $('#agencyFeeds')
                    .append('<span class="feedFields">No public GTFS feed available.</span>');


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

    this.layers.osm = new L.TileLayer('http://{s}.tiles.mapbox.com/v3/openplans.map-g4j0dszr,openplans.gtfs_coverage/{z}/{x}/{y}.png', {
        attribution: 'Map data &copy; OpenStreetMap contributors, CC-BY-SA',
        maxZoom: 18
    });

    //this.layers.transit = new L.TileLayer('http://localhost:8001/{z}/{x}/{y}.png', {
    //    attribution: 'GTFS data courtesy GTFS Data Exchange'
    //});

    this.map.addLayer(this.layers.osm);
    //this.map.addLayer(this.layers.transit);
    this.zoomTo(39.87602, -95.97656, 4);

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
        // golden rectangle, horizontal
        .css('height', (parent.innerWidth() / 1.618) + 'px');

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
    this.sizeMapArea();
};
        
/* Convenience function to create a detached jQuery DOM object */
function create (tag) {
    return $(document.createElement(tag));
}

$(document).ready(function () {
    mc = new MapController();
    dc = new DataController(mc);
    
    $('form.xhr').submit(function (e) {
    	e.preventDefault();
    	$.ajax({
    		url: $(this).attr('action') + '?' +
    			$(this).serialize()
    	});
    });
});

    