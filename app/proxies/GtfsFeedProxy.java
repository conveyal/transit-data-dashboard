package proxies;

import models.GtfsFeed;
import java.util.Date;

public class GtfsFeedProxy {
    public long id;
    public String agencyName;
    public String agencyUrl;
    public String feedBaseUrl;
    public boolean official;
    public Date expires; 

    public GtfsFeedProxy (GtfsFeed feed) {
        this.id = feed.id;
        this.agencyName = feed.agencyName;
        this.agencyUrl = feed.agencyUrl;
        this.feedBaseUrl = feed.feedBaseUrl;
        this.expires = feed.expirationDate;
        this.official = feed.official;
    }
}