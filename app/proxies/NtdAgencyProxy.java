package proxies;

import models.NtdAgency;

/**
 * Proxy class for serialization of an NtdAgency
 */
public class NtdAgencyProxy {
    public String name;
    public String url;
    // a String since it has to be serialized
    public String metro;
    public int population;
    public int ridership;
    public int passengerMiles;
    public boolean publicGtfs;
    public boolean googleGtfs;

    public NtdAgencyProxy (String name, String url, String metro, int population, int ridership,
                           int passengerMiles, boolean publicGtfs, boolean googleGtfs) {
        this.name = name;
        this.url = url;
        this.metro = metro;
        this.population = population;
        this.ridership = ridership;
        this.passengerMiles = passengerMiles;
        this.publicGtfs = publicGtfs;
        this.googleGtfs = googleGtfs;
    }

    /**
     * Make an NtdAgencyProxy from an NtdAgency
     */
    public NtdAgencyProxy (NtdAgency agency) {
        name = agency.name;
        url = agency.url;

        if (agency.metroArea != null)
            metro = agency.metroArea.toString();
        else
            metro = null;

        population = agency.population;
        ridership = agency.ridership;
        passengerMiles = agency.passengerMiles;
        
        if (agency.feeds.size() > 0)
            publicGtfs = true;
        else
            publicGtfs = false;

        // TODO: parse google gtfs
        googleGtfs = false;
    }
}