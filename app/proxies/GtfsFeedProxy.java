package proxies;

import models.GtfsFeed;

public class GtfsFeedProxy {
    public long id;
    public String agencyName;
    public String agencyUrl;

    public GtfsFeedProxy (GtfsFeed feed) {
        this.id = feed.id;
        this.agencyName = feed.agencyName;
        this.agencyUrl = feed.agencyUrl;
    }
}