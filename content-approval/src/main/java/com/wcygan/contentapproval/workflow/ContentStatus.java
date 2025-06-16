package com.wcygan.contentapproval.workflow;

/**
 * Enumeration of possible content approval statuses.
 * These statuses correspond to the workflow states and database status values.
 */
public enum ContentStatus {
    
    /**
     * Content is in draft state, not yet submitted for review.
     */
    DRAFT("DRAFT"),
    
    /**
     * Content has been submitted and is under review.
     */
    UNDER_REVIEW("UNDER_REVIEW"),
    
    /**
     * Content has been approved and is ready for publication.
     */
    APPROVED("APPROVED"),
    
    /**
     * Content has been published and is publicly available.
     */
    PUBLISHED("PUBLISHED"),
    
    /**
     * Content has been rejected and cannot be published.
     */
    REJECTED("REJECTED"),
    
    /**
     * Content requires changes before it can be approved.
     */
    CHANGES_REQUESTED("CHANGES_REQUESTED");
    
    private final String databaseValue;
    
    ContentStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }
    
    /**
     * Gets the database representation of this status.
     * 
     * @return The status value as stored in the database
     */
    public String getDatabaseValue() {
        return databaseValue;
    }
    
    /**
     * Creates a ContentStatus from a database value.
     * 
     * @param databaseValue The database status value
     * @return The corresponding ContentStatus enum
     * @throws IllegalArgumentException if the database value is not recognized
     */
    public static ContentStatus fromDatabaseValue(String databaseValue) {
        for (ContentStatus status : ContentStatus.values()) {
            if (status.databaseValue.equals(databaseValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown content status: " + databaseValue);
    }
    
    /**
     * Checks if this status represents a completed workflow state.
     * 
     * @return True if the workflow is complete, false otherwise
     */
    public boolean isComplete() {
        return this == PUBLISHED || this == REJECTED;
    }
    
    /**
     * Checks if this status allows for approval actions.
     * 
     * @return True if content can be approved, false otherwise
     */
    public boolean canBeApproved() {
        return this == UNDER_REVIEW || this == CHANGES_REQUESTED;
    }
    
    /**
     * Checks if this status allows for rejection actions.
     * 
     * @return True if content can be rejected, false otherwise
     */
    public boolean canBeRejected() {
        return this == UNDER_REVIEW || this == CHANGES_REQUESTED;
    }
    
    /**
     * Checks if changes can be requested for this status.
     * 
     * @return True if changes can be requested, false otherwise
     */
    public boolean canRequestChanges() {
        return this == UNDER_REVIEW;
    }
}