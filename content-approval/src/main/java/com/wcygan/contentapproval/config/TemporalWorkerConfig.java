package com.wcygan.contentapproval.config;

import com.wcygan.contentapproval.activity.impl.ContentPersistenceActivityImpl;
import com.wcygan.contentapproval.activity.impl.ContentValidationActivityImpl;
import com.wcygan.contentapproval.activity.impl.NotificationActivityImpl;
import com.wcygan.contentapproval.workflow.ContentApprovalWorkflowImpl;
import io.quarkus.runtime.StartupEvent;
import io.temporal.worker.WorkerOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for Temporal workers.
 * Sets up the worker factory and registers workflow and activity implementations.
 */
@ApplicationScoped
public class TemporalWorkerConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(TemporalWorkerConfig.class);
    
    public static final String CONTENT_APPROVAL_TASK_QUEUE = "content-approval-queue";
    
    @Inject
    WorkerFactory workerFactory;
    
    @Inject
    ContentValidationActivityImpl contentValidationActivity;
    
    @Inject
    ContentPersistenceActivityImpl contentPersistenceActivity;
    
    @Inject
    NotificationActivityImpl notificationActivity;
    
    /**
     * Initializes and starts the Temporal worker on application startup.
     * 
     * @param event The startup event
     */
    void onStart(@Observes StartupEvent event) {
        logger.info("Initializing Temporal workers...");
        
        try {
            // Configure worker options for optimal performance
            WorkerOptions workerOptions = WorkerOptions.newBuilder()
                    .setMaxConcurrentActivityExecutionSize(100)
                    .setMaxConcurrentWorkflowTaskExecutionSize(50)
                    .setMaxConcurrentLocalActivityExecutionSize(200)
                    .build();
            
            // Create worker for content approval task queue
            Worker contentApprovalWorker = workerFactory.newWorker(CONTENT_APPROVAL_TASK_QUEUE, workerOptions);
            
            // Register workflow implementations
            contentApprovalWorker.registerWorkflowImplementationTypes(ContentApprovalWorkflowImpl.class);
            logger.info("Registered workflow implementation: {}", ContentApprovalWorkflowImpl.class.getSimpleName());
            
            // Register activity implementations
            contentApprovalWorker.registerActivitiesImplementations(
                contentValidationActivity,
                contentPersistenceActivity,
                notificationActivity
            );
            logger.info("Registered activity implementations: {}, {}, {}", 
                contentValidationActivity.getClass().getSimpleName(),
                contentPersistenceActivity.getClass().getSimpleName(),
                notificationActivity.getClass().getSimpleName());
            
            // Start the worker factory (this starts all registered workers)
            workerFactory.start();
            logger.info("Temporal workers started successfully on task queue: {}", CONTENT_APPROVAL_TASK_QUEUE);
            
            // Add shutdown hook to gracefully stop workers
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down Temporal workers...");
                workerFactory.shutdown();
                logger.info("Temporal workers shut down completed");
            }));
            
        } catch (Exception e) {
            logger.error("Failed to initialize Temporal workers", e);
            throw new RuntimeException("Temporal worker initialization failed", e);
        }
    }
    
    /**
     * Gets the task queue name for content approval workflows.
     * 
     * @return The task queue name
     */
    public static String getContentApprovalTaskQueue() {
        return CONTENT_APPROVAL_TASK_QUEUE;
    }
}