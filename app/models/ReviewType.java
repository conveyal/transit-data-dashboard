package models;
/**
 * A ReviewType handles the options for when the action is ambiguous and must be reviewed by a human.
 * @author matthewc
 *
 */
public enum ReviewType {
    NO_AGENCY ("Feed matches no agency"),
    AGENCY_MULTIPLE_AREAS ("Agency matches multiple metro areas");
    
    private String description;
    
    private ReviewType (String options) {
        this.description = options;
    }
    
    public String toString () {
        return description;
    }
}
