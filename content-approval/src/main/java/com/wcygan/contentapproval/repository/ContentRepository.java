package com.wcygan.contentapproval.repository;

import com.wcygan.contentapproval.entity.ContentEntity;
import com.wcygan.contentapproval.exception.ContentNotFoundException;
import com.wcygan.contentapproval.exception.ContentPersistenceException;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for content data access operations.
 * Provides a clean abstraction over the database layer.
 */
public interface ContentRepository {
    
    /**
     * Saves a new content entity or updates an existing one.
     * 
     * @param content The content entity to save
     * @return The saved content entity with generated ID (if new)
     * @throws ContentPersistenceException if the save operation fails
     */
    ContentEntity save(ContentEntity content) throws ContentPersistenceException;
    
    /**
     * Finds content by its ID.
     * 
     * @param id The content ID
     * @return Optional containing the content if found, empty otherwise
     * @throws ContentPersistenceException if the query fails
     */
    Optional<ContentEntity> findById(Long id) throws ContentPersistenceException;
    
    /**
     * Gets content by ID, throwing exception if not found.
     * 
     * @param id The content ID
     * @return The content entity
     * @throws ContentNotFoundException if content doesn't exist
     * @throws ContentPersistenceException if the query fails
     */
    ContentEntity getById(Long id) throws ContentNotFoundException, ContentPersistenceException;
    
    /**
     * Updates the status of content.
     * 
     * @param id The content ID
     * @param status The new status
     * @throws ContentNotFoundException if content doesn't exist
     * @throws ContentPersistenceException if the update fails
     */
    void updateStatus(Long id, String status) throws ContentNotFoundException, ContentPersistenceException;
    
    /**
     * Links content to a Temporal workflow.
     * 
     * @param id The content ID
     * @param workflowId The workflow ID
     * @throws ContentNotFoundException if content doesn't exist
     * @throws ContentPersistenceException if the update fails
     */
    void linkToWorkflow(Long id, String workflowId) throws ContentNotFoundException, ContentPersistenceException;
    
    /**
     * Finds content by author ID.
     * 
     * @param authorId The author ID
     * @return List of content entities by the author
     * @throws ContentPersistenceException if the query fails
     */
    List<ContentEntity> findByAuthorId(String authorId) throws ContentPersistenceException;
    
    /**
     * Finds content by status.
     * 
     * @param status The content status
     * @return List of content entities with the given status
     * @throws ContentPersistenceException if the query fails
     */
    List<ContentEntity> findByStatus(String status) throws ContentPersistenceException;
    
    /**
     * Finds content by workflow ID.
     * 
     * @param workflowId The Temporal workflow ID
     * @return Optional containing the content if found, empty otherwise
     * @throws ContentPersistenceException if the query fails
     */
    Optional<ContentEntity> findByWorkflowId(String workflowId) throws ContentPersistenceException;
    
    /**
     * Checks if content exists with the given ID.
     * 
     * @param id The content ID
     * @return true if content exists, false otherwise
     * @throws ContentPersistenceException if the query fails
     */
    boolean existsById(Long id) throws ContentPersistenceException;
    
    /**
     * Deletes content by ID.
     * 
     * @param id The content ID
     * @throws ContentNotFoundException if content doesn't exist
     * @throws ContentPersistenceException if the delete fails
     */
    void deleteById(Long id) throws ContentNotFoundException, ContentPersistenceException;
    
    /**
     * Gets count of content by status.
     * 
     * @param status The content status
     * @return Number of content entities with the given status
     * @throws ContentPersistenceException if the query fails
     */
    long countByStatus(String status) throws ContentPersistenceException;
}