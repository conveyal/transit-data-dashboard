package models;

public enum FeedParseStatus {
    // Keep in mind that these names will be displayed as
    // Valid: ______ in the webapp, so they should make sense
    // in that context
    SUCCESSFUL("Yes"), FAILED("No");
    
    private String name;
    
    private FeedParseStatus(String name) {
        this.name = name;
    }
    
    public String toString () {
        return this.name;
    }
}
