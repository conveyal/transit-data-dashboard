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

function MapFeeds () {
    var instance = this;

    var fetchAgencies = $.ajax({
        url: '/api/ntdagencies/agencies',
        dataFormat: 'json',
        success: function (data) {
            instance.agencies = data;
        },
        error: function (jQxhr, textStatus, errorThrown) {
            console.log('Error retrieving agencies: ' + textStatus + ' ' + errorThrown);
        }
    });

    var fetchFeeds = $.ajax({
        url: '/api/gtfsfeeds/feeds',
        dataFormat: 'json',
        success: function (data) {
            instance.feeds = data;
        },
        error: function (jQxhr, textStatus, errorThrown) {
            console.log('Error retrieving feeds: ' + textStatus + ' ' + errorThrown);
        }
    });

    // when both requests have completed, we can populate the form
    $.when(fetchAgencies, fetchFeeds).then(function () {
        instance.populateForm();
    });

    // when either filter changes, repopulate form
    $('.filter').change(function () {
        instance.populateForm();
    });

    // when the user clicks, connect
    $('#connect').click(function () {
        instance.submit();
    });
}

/**
 * populate the form
 */
MapFeeds.prototype.populateForm = function () {
    var instance = this;

    var filteredAgencies = this.filterAgencies();
    var filteredFeeds = this.filterFeeds();

    $('.agency').remove();
    $('.feed').remove();

    $.each(filteredAgencies, function (ind, agency) {
        $('#agencies tbody').append(
            '<tr class="agency ' + (agency.publicGtfs ? 'public' : 'notPublic') + '">' +
                '<td><input type="checkbox" class="agencyBox" id="agency-' + agency.id + 
                    '" /></td>' +
                '<td><a href="/crud/ntdagenciescrud/' + agency.id + '">' + agency.name + '</a></td>' +
                '<td>' + agency.url + '</td>' +
                '<td>' + agency.metro + '</td>' +
            '</tr>'
        );
    });

    $.each(filteredFeeds, function (ind, feed) {
        $('#feeds tbody').append(
            '<tr class="feed">' +
                '<td><input type="checkbox" class="feedBox" id="feed-' + feed.id + 
                    '" /></td>' +
                '<td><a href="/crud/gtfsfeedscrud/' + feed.id + '">' + feed.agencyName + '</a></td>' +
                '<td>' + feed.agencyUrl + '</td>' +
            '</tr>'
        );
    });
                
};

MapFeeds.prototype.filterAgencies = function () {
    var filter = $('#agencyFilter').val().toLowerCase();
    
    return this.agencies.filter(function (agency) {
        return (agency.name.toLowerCase().indexOf(filter) > -1 || 
                (agency.url != undefined && agency.url.toLowerCase().indexOf(filter) > -1) ||
                (agency.metro != undefined && agency.metro.toLowerCase().indexOf(filter) > -1));
    });
};
        
MapFeeds.prototype.filterFeeds = function () {
    var filter = $('#feedFilter').val().toLowerCase();
    
    return this.feeds.filter(function (feed) {
        return (feed.agencyName.toLowerCase().indexOf(filter) > -1 || 
                feed.agencyUrl.toLowerCase().indexOf(filter) > -1);
    });
};

MapFeeds.prototype.submit = function () {
    var instance = this;

    // build the data
    var data = {};
    data.feed = [];
    data.agency = [];

    // http://jvance.com/blog/2009/07/14/GetValueOfCheckboxUsingJQueryToEnableButton.xhtml
    $('.feedBox:checked').each(function (ind, box) {
        box = $(box);
        data.feed.push(Number(box.attr('id').replace('feed-', '')));
    });

    $('.agencyBox:checked').each(function (ind, box) {
        box = $(box);
        data.agency.push(Number(box.attr('id').replace('agency-', '')));
    });

    $.ajax({
        url: '/api/mapper/connectFeedsAndAgencies',
        data: data,
        method: 'POST',
        success: function () {
            $('#status').text('Success').fadeIn();
            setTimeout(function () { $('#status').fadeOut(); }, 6000);

            // This will reset checkboxes
            instance.populateForm();
        },
        error: function (jQxhr, error, text) {
            $('#status').text('Failure: ' + error + ' ' + text).fadeIn();
            setTimeout(function () { $('#status').fadeOut(); }, 6000);

            // don't reset checkboxes
        }
    });
}

$(document).ready(function () {
    mf = new MapFeeds();
});