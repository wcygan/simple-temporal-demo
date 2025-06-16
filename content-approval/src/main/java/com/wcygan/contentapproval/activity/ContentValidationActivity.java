package com.wcygan.contentapproval.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for content validation operations.
 * Validates content according to business rules and policies.
 */
@ActivityInterface
public interface ContentValidationActivity {
    
    /**
     * Validates content for approval workflow.
     * Checks content against validation rules like minimum length,
     * prohibited content, spam detection, etc.
     * 
     * @param contentId The ID of the content to validate
     * @return True if content passes validation, false otherwise
     */
    @ActivityMethod
    boolean validateContent(Long contentId);
    
    /**
     * Validates content title for compliance with naming standards.
     * 
     * @param contentId The ID of the content to validate
     * @return True if title is valid, false otherwise
     */
    @ActivityMethod
    boolean validateTitle(Long contentId);
    
    /**
     * Performs content quality checks including grammar, readability, etc.
     * 
     * @param contentId The ID of the content to validate
     * @return Validation score from 0-100
     */
    @ActivityMethod
    int calculateQualityScore(Long contentId);
    
    /**
     * Checks content for prohibited words or inappropriate content.
     * 
     * @param contentId The ID of the content to validate
     * @return True if content is appropriate, false otherwise
     */
    @ActivityMethod
    boolean checkContentAppropriateFor(Long contentId);
}