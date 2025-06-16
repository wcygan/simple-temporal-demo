package com.wcygan.contentapproval.activity.impl;

import com.wcygan.contentapproval.activity.ContentValidationActivity;
import com.wcygan.contentapproval.generated.tables.records.ContentRecord;
import io.temporal.activity.Activity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.wcygan.contentapproval.generated.Tables.CONTENT;

/**
 * Implementation of ContentValidationActivity.
 * Provides content validation logic including length checks, content appropriateness,
 * and quality scoring.
 */
@ApplicationScoped
public class ContentValidationActivityImpl implements ContentValidationActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentValidationActivityImpl.class);
    
    // Validation constants
    private static final int MIN_TITLE_LENGTH = 5;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MIN_CONTENT_LENGTH = 50;
    private static final int MAX_CONTENT_LENGTH = 50000;
    
    // Prohibited words list (in production, this would come from configuration)
    private static final List<String> PROHIBITED_WORDS = Arrays.asList(
        "spam", "scam", "illegal", "harmful"
    );
    
    // Quality scoring patterns
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b");
    
    @Inject
    DSLContext dsl;
    
    @Override
    public boolean validateContent(Long contentId) {
        logger.info("Validating content with ID: {}", contentId);
        
        try {
            // Get activity context for heartbeat
            Activity.getExecutionContext().heartbeat("Starting content validation");
            
            ContentRecord content = dsl.selectFrom(CONTENT)
                    .where(CONTENT.ID.eq(contentId))
                    .fetchOne();
            
            if (content == null) {
                logger.error("Content not found with ID: {}", contentId);
                return false;
            }
            
            // Perform all validation checks
            boolean titleValid = validateTitleInternal(content.getTitle());
            boolean contentValid = validateContentInternal(content.getContent());
            boolean appropriateContent = checkContentAppropriateForInternal(content.getContent());
            
            Activity.getExecutionContext().heartbeat("Validation checks completed");
            
            boolean isValid = titleValid && contentValid && appropriateContent;
            
            logger.info("Content validation completed for ID: {}, result: {}", contentId, isValid);
            return isValid;
            
        } catch (Exception e) {
            logger.error("Error validating content with ID: {}", contentId, e);
            throw new RuntimeException("Content validation failed", e);
        }
    }
    
    @Override
    public boolean validateTitle(Long contentId) {
        logger.info("Validating title for content ID: {}", contentId);
        
        try {
            ContentRecord content = dsl.selectFrom(CONTENT)
                    .where(CONTENT.ID.eq(contentId))
                    .fetchOne();
            
            if (content == null) {
                logger.error("Content not found with ID: {}", contentId);
                return false;
            }
            
            boolean isValid = validateTitleInternal(content.getTitle());
            logger.info("Title validation completed for ID: {}, result: {}", contentId, isValid);
            return isValid;
            
        } catch (Exception e) {
            logger.error("Error validating title for content ID: {}", contentId, e);
            throw new RuntimeException("Title validation failed", e);
        }
    }
    
    @Override
    public int calculateQualityScore(Long contentId) {
        logger.info("Calculating quality score for content ID: {}", contentId);
        
        try {
            ContentRecord content = dsl.selectFrom(CONTENT)
                    .where(CONTENT.ID.eq(contentId))
                    .fetchOne();
            
            if (content == null) {
                logger.error("Content not found with ID: {}", contentId);
                return 0;
            }
            
            int score = calculateQualityScoreInternal(content.getTitle(), content.getContent());
            logger.info("Quality score calculated for ID: {}, score: {}", contentId, score);
            return score;
            
        } catch (Exception e) {
            logger.error("Error calculating quality score for content ID: {}", contentId, e);
            throw new RuntimeException("Quality score calculation failed", e);
        }
    }
    
    @Override
    public boolean checkContentAppropriateFor(Long contentId) {
        logger.info("Checking content appropriateness for ID: {}", contentId);
        
        try {
            ContentRecord content = dsl.selectFrom(CONTENT)
                    .where(CONTENT.ID.eq(contentId))
                    .fetchOne();
            
            if (content == null) {
                logger.error("Content not found with ID: {}", contentId);
                return false;
            }
            
            boolean isAppropriate = checkContentAppropriateForInternal(content.getContent());
            logger.info("Content appropriateness check completed for ID: {}, result: {}", contentId, isAppropriate);
            return isAppropriate;
            
        } catch (Exception e) {
            logger.error("Error checking content appropriateness for ID: {}", contentId, e);
            throw new RuntimeException("Content appropriateness check failed", e);
        }
    }
    
    // Internal validation methods
    
    private boolean validateTitleInternal(String title) {
        if (title == null || title.trim().isEmpty()) {
            logger.warn("Title is null or empty");
            return false;
        }
        
        String trimmedTitle = title.trim();
        if (trimmedTitle.length() < MIN_TITLE_LENGTH) {
            logger.warn("Title too short: {} characters (minimum: {})", trimmedTitle.length(), MIN_TITLE_LENGTH);
            return false;
        }
        
        if (trimmedTitle.length() > MAX_TITLE_LENGTH) {
            logger.warn("Title too long: {} characters (maximum: {})", trimmedTitle.length(), MAX_TITLE_LENGTH);
            return false;
        }
        
        // Check for prohibited words in title
        String lowerTitle = trimmedTitle.toLowerCase();
        for (String prohibitedWord : PROHIBITED_WORDS) {
            if (lowerTitle.contains(prohibitedWord.toLowerCase())) {
                logger.warn("Title contains prohibited word: {}", prohibitedWord);
                return false;
            }
        }
        
        return true;
    }
    
    private boolean validateContentInternal(String content) {
        if (content == null || content.trim().isEmpty()) {
            logger.warn("Content is null or empty");
            return false;
        }
        
        String trimmedContent = content.trim();
        if (trimmedContent.length() < MIN_CONTENT_LENGTH) {
            logger.warn("Content too short: {} characters (minimum: {})", trimmedContent.length(), MIN_CONTENT_LENGTH);
            return false;
        }
        
        if (trimmedContent.length() > MAX_CONTENT_LENGTH) {
            logger.warn("Content too long: {} characters (maximum: {})", trimmedContent.length(), MAX_CONTENT_LENGTH);
            return false;
        }
        
        return true;
    }
    
    private boolean checkContentAppropriateForInternal(String content) {
        if (content == null) {
            return false;
        }
        
        String lowerContent = content.toLowerCase();
        for (String prohibitedWord : PROHIBITED_WORDS) {
            if (lowerContent.contains(prohibitedWord.toLowerCase())) {
                logger.warn("Content contains prohibited word: {}", prohibitedWord);
                return false;
            }
        }
        
        return true;
    }
    
    private int calculateQualityScoreInternal(String title, String content) {
        int score = 0;
        
        // Title quality (20 points max)
        if (title != null && !title.trim().isEmpty()) {
            score += 10; // Base points for having a title
            if (title.length() >= 10 && title.length() <= 100) {
                score += 10; // Good title length
            }
        }
        
        // Content quality (80 points max)
        if (content != null && !content.trim().isEmpty()) {
            score += 20; // Base points for having content
            
            // Length scoring (20 points max)
            if (content.length() >= MIN_CONTENT_LENGTH) {
                score += Math.min(20, content.length() / 100); // Up to 20 points based on length
            }
            
            // Sentence structure (20 points max)
            String[] sentences = SENTENCE_PATTERN.split(content);
            if (sentences.length >= 3) {
                score += Math.min(20, sentences.length * 2); // Points for sentence count
            }
            
            // Word variety (20 points max)
            String[] words = WORD_PATTERN.split(content.toLowerCase());
            long uniqueWords = Arrays.stream(words).distinct().count();
            if (uniqueWords > 10) {
                score += Math.min(20, (int) (uniqueWords / 5)); // Points for vocabulary variety
            }
        }
        
        return Math.min(100, score); // Cap at 100
    }
}