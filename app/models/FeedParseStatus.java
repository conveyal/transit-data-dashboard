package models;

public enum FeedParseStatus {
    SUCCESSFUL("Successful"), FAILED("Failed");
    
    private String name;
    
    private FeedParseStatus(String name) {
        this.name = name;
    }
    
    public String toString () {
        return this.name;
    }
}
