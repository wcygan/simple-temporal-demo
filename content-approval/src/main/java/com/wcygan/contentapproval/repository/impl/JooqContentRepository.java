package com.wcygan.contentapproval.repository.impl;

import com.wcygan.contentapproval.entity.ContentEntity;
import com.wcygan.contentapproval.exception.ContentNotFoundException;
import com.wcygan.contentapproval.exception.ContentPersistenceException;
import com.wcygan.contentapproval.generated.tables.records.ContentRecord;
import com.wcygan.contentapproval.repository.ContentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.jooq.JSON;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.wcygan.contentapproval.generated.Tables.CONTENT;

/**
 * jOOQ implementation of ContentRepository.
 * Handles database operations using jOOQ with proper exception handling and entity mapping.
 */
@ApplicationScoped
public class JooqContentRepository implements ContentRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(JooqContentRepository.class);
    
    @Inject
    DSLContext dsl;
    
    @Override
    @Transactional
    public ContentEntity save(ContentEntity content) throws ContentPersistenceException {
        logger.debug("Saving content entity: {}", content);
        
        try {
            if (content.getId() == null) {
                // Insert new content
                ContentRecord record = dsl.newRecord(CONTENT);
                mapEntityToRecord(content, record);
                record.store();
                content.setId(record.getId());
                logger.debug("Created new content with ID: {}", content.getId());
            } else {
                // Update existing content
                int updated = dsl.update(CONTENT)
                    .set(CONTENT.TITLE, content.getTitle())
                    .set(CONTENT.CONTENT_, content.getContent())
                    .set(CONTENT.AUTHOR_ID, content.getAuthorId())
                    .set(CONTENT.STATUS, content.getStatus())
                    .set(CONTENT.TEMPORAL_WORKFLOW_ID, content.getTemporalWorkflowId())
                    .set(CONTENT.TAGS, content.getTags() != null ? JSON.valueOf(content.getTags()) : null)
                    .set(CONTENT.UPDATED_DATE, LocalDateTime.now())
                    .where(CONTENT.ID.eq(content.getId()))
                    .execute();
                
                if (updated == 0) {
                    throw new ContentNotFoundException(content.getId());
                }
                logger.debug("Updated content with ID: {}", content.getId());
            }
            
            return content;
            
        } catch (ContentNotFoundException e) {
            throw e;
        } catch (DataAccessException e) {
            logger.error("Database error saving content: {}", content, e);
            throw new ContentPersistenceException("Failed to save content due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error saving content: {}", content, e);
            throw new ContentPersistenceException("Failed to save content", e);
        }
    }
    
    @Override
    public Optional<ContentEntity> findById(Long id) throws ContentPersistenceException {
        logger.debug("Finding content by ID: {}", id);
        
        try {
            ContentRecord record = dsl.selectFrom(CONTENT)
                .where(CONTENT.ID.eq(id))
                .fetchOne();
            
            return record != null ? Optional.of(mapRecordToEntity(record)) : Optional.empty();
            
        } catch (DataAccessException e) {
            logger.error("Database error finding content by ID: {}", id, e);
            throw new ContentPersistenceException("Failed to find content due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error finding content by ID: {}", id, e);
            throw new ContentPersistenceException("Failed to find content", e);
        }
    }
    
    @Override
    public ContentEntity getById(Long id) throws ContentNotFoundException, ContentPersistenceException {
        Optional<ContentEntity> content = findById(id);
        return content.orElseThrow(() -> new ContentNotFoundException(id));
    }
    
    @Override
    @Transactional
    public void updateStatus(Long id, String status) throws ContentNotFoundException, ContentPersistenceException {
        logger.debug("Updating content {} status to {}", id, status);
        
        try {
            int updated = dsl.update(CONTENT)
                .set(CONTENT.STATUS, status)
                .set(CONTENT.UPDATED_DATE, LocalDateTime.now())
                .where(CONTENT.ID.eq(id))
                .execute();
            
            if (updated == 0) {
                throw new ContentNotFoundException(id);
            }
            
            logger.debug("Successfully updated content {} status to {}", id, status);
            
        } catch (ContentNotFoundException e) {
            throw e;
        } catch (DataAccessException e) {
            logger.error("Database error updating content {} status to {}", id, status, e);
            throw new ContentPersistenceException("Failed to update content status due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error updating content {} status to {}", id, status, e);
            throw new ContentPersistenceException("Failed to update content status", e);
        }
    }
    
    @Override
    @Transactional
    public void linkToWorkflow(Long id, String workflowId) throws ContentNotFoundException, ContentPersistenceException {
        logger.debug("Linking content {} to workflow {}", id, workflowId);
        
        try {
            int updated = dsl.update(CONTENT)
                .set(CONTENT.TEMPORAL_WORKFLOW_ID, workflowId)
                .set(CONTENT.UPDATED_DATE, LocalDateTime.now())
                .where(CONTENT.ID.eq(id))
                .execute();
            
            if (updated == 0) {
                throw new ContentNotFoundException(id);
            }
            
            logger.debug("Successfully linked content {} to workflow {}", id, workflowId);
            
        } catch (ContentNotFoundException e) {
            throw e;
        } catch (DataAccessException e) {
            logger.error("Database error linking content {} to workflow {}", id, workflowId, e);
            throw new ContentPersistenceException("Failed to link content to workflow due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error linking content {} to workflow {}", id, workflowId, e);
            throw new ContentPersistenceException("Failed to link content to workflow", e);
        }
    }
    
    @Override
    public List<ContentEntity> findByAuthorId(String authorId) throws ContentPersistenceException {
        logger.debug("Finding content by author ID: {}", authorId);
        
        try {
            return dsl.selectFrom(CONTENT)
                .where(CONTENT.AUTHOR_ID.eq(authorId))
                .orderBy(CONTENT.CREATED_DATE.desc())
                .fetch()
                .map(this::mapRecordToEntity);
            
        } catch (DataAccessException e) {
            logger.error("Database error finding content by author ID: {}", authorId, e);
            throw new ContentPersistenceException("Failed to find content by author due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error finding content by author ID: {}", authorId, e);
            throw new ContentPersistenceException("Failed to find content by author", e);
        }
    }
    
    @Override
    public List<ContentEntity> findByStatus(String status) throws ContentPersistenceException {
        logger.debug("Finding content by status: {}", status);
        
        try {
            return dsl.selectFrom(CONTENT)
                .where(CONTENT.STATUS.eq(status))
                .orderBy(CONTENT.UPDATED_DATE.desc())
                .fetch()
                .map(this::mapRecordToEntity);
            
        } catch (DataAccessException e) {
            logger.error("Database error finding content by status: {}", status, e);
            throw new ContentPersistenceException("Failed to find content by status due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error finding content by status: {}", status, e);
            throw new ContentPersistenceException("Failed to find content by status", e);
        }
    }
    
    @Override
    public Optional<ContentEntity> findByWorkflowId(String workflowId) throws ContentPersistenceException {
        logger.debug("Finding content by workflow ID: {}", workflowId);
        
        try {
            ContentRecord record = dsl.selectFrom(CONTENT)
                .where(CONTENT.TEMPORAL_WORKFLOW_ID.eq(workflowId))
                .fetchOne();
            
            return record != null ? Optional.of(mapRecordToEntity(record)) : Optional.empty();
            
        } catch (DataAccessException e) {
            logger.error("Database error finding content by workflow ID: {}", workflowId, e);
            throw new ContentPersistenceException("Failed to find content by workflow ID due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error finding content by workflow ID: {}", workflowId, e);
            throw new ContentPersistenceException("Failed to find content by workflow ID", e);
        }
    }
    
    @Override
    public boolean existsById(Long id) throws ContentPersistenceException {
        logger.debug("Checking if content exists by ID: {}", id);
        
        try {
            return dsl.fetchExists(CONTENT, CONTENT.ID.eq(id));
            
        } catch (DataAccessException e) {
            logger.error("Database error checking content existence by ID: {}", id, e);
            throw new ContentPersistenceException("Failed to check content existence due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error checking content existence by ID: {}", id, e);
            throw new ContentPersistenceException("Failed to check content existence", e);
        }
    }
    
    @Override
    @Transactional
    public void deleteById(Long id) throws ContentNotFoundException, ContentPersistenceException {
        logger.debug("Deleting content by ID: {}", id);
        
        try {
            int deleted = dsl.deleteFrom(CONTENT)
                .where(CONTENT.ID.eq(id))
                .execute();
            
            if (deleted == 0) {
                throw new ContentNotFoundException(id);
            }
            
            logger.debug("Successfully deleted content with ID: {}", id);
            
        } catch (ContentNotFoundException e) {
            throw e;
        } catch (DataAccessException e) {
            logger.error("Database error deleting content by ID: {}", id, e);
            throw new ContentPersistenceException("Failed to delete content due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error deleting content by ID: {}", id, e);
            throw new ContentPersistenceException("Failed to delete content", e);
        }
    }
    
    @Override
    public long countByStatus(String status) throws ContentPersistenceException {
        logger.debug("Counting content by status: {}", status);
        
        try {
            return dsl.selectCount()
                .from(CONTENT)
                .where(CONTENT.STATUS.eq(status))
                .fetchOne(0, long.class);
            
        } catch (DataAccessException e) {
            logger.error("Database error counting content by status: {}", status, e);
            throw new ContentPersistenceException("Failed to count content by status due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error counting content by status: {}", status, e);
            throw new ContentPersistenceException("Failed to count content by status", e);
        }
    }
    
    /**
     * Maps a jOOQ ContentRecord to a ContentEntity.
     */
    private ContentEntity mapRecordToEntity(ContentRecord record) {
        return new ContentEntity(
            record.getId(),
            record.getTitle(),
            record.getContent(),
            record.getAuthorId(),
            record.getStatus(),
            record.getTemporalWorkflowId(),
            record.getTags() != null ? record.getTags().toString() : null,
            record.getCreatedDate(),
            record.getUpdatedDate()
        );
    }
    
    /**
     * Maps a ContentEntity to a jOOQ ContentRecord (for inserts).
     */
    private void mapEntityToRecord(ContentEntity entity, ContentRecord record) {
        record.setTitle(entity.getTitle());
        record.setContent(entity.getContent());
        record.setAuthorId(entity.getAuthorId());
        record.setStatus(entity.getStatus());
        record.setTemporalWorkflowId(entity.getTemporalWorkflowId());
        record.setTags(entity.getTags() != null ? JSON.valueOf(entity.getTags()) : null);
        record.setCreatedDate(entity.getCreatedDate());
        record.setUpdatedDate(entity.getUpdatedDate());
    }
}