package proxies;

/**
 * Defines the interface of an object proxy
 */
public interface Proxy {
    public String[] toHeader();
    public String[] toRow();
}