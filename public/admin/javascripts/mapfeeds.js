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
            '<tr class="agency">' +
                '<td><input type="checkbox" class="agencyBox" id="agency-' + agency.id + 
                    '" /></td>' +
                '<td>' + agency.name + '</td>' +
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
                '<td>' + feed.agencyName + '</td>' +
                '<td>' + feed.agencyUrl + '</td>' +
            '</tr>'
        );
    });
                
};

MapFeeds.prototype.filterAgencies = function () {
    var filter = $('#agencyFilter').val().toLowerCase();
    
    return this.agencies.filter(function (agency) {
        return (agency.name.toLowerCase().indexOf(filter) > -1 || 
                agency.url.toLowerCase().indexOf(filter) > -1 ||
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