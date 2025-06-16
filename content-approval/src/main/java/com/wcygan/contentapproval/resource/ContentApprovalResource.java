package com.wcygan.contentapproval.resource;

import com.wcygan.contentapproval.dto.ContentApprovalResponse;
import com.wcygan.contentapproval.dto.ContentStatusResponse;
import com.wcygan.contentapproval.dto.ContentSubmissionRequest;
import com.wcygan.contentapproval.service.ContentService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST resource for content approval operations.
 * Provides endpoints for submitting content, checking status, and managing approvals.
 */
@Path("/content")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Content Approval", description = "Content approval workflow operations")
public class ContentApprovalResource {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentApprovalResource.class);
    
    @Inject
    ContentService contentService;
    
    /**
     * Submits content for approval workflow.
     */
    @POST
    @Operation(
        summary = "Submit content for approval",
        description = "Submits new content for the approval workflow process"
    )
    @APIResponse(responseCode = "200", description = "Content submitted successfully")
    @APIResponse(responseCode = "400", description = "Invalid content submission")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response submitContent(@Valid ContentSubmissionRequest request) {
        logger.info("Received content submission request: {}", request);
        
        try {
            ContentApprovalResponse response = contentService.submitContentForApproval(request);
            
            if (response.getWorkflowId() != null) {
                logger.info("Content submitted successfully: {}", response);
                return Response.ok(response).build();
            } else {
                logger.error("Content submission failed: {}", response.getMessage());
                return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
            }
            
        } catch (Exception e) {
            logger.error("Error processing content submission", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ContentApprovalResponse.error(null, "Internal server error"))
                    .build();
        }
    }
    
    /**
     * Gets the current status of content approval.
     */
    @GET
    @Path("/{contentId}/status")
    @Operation(
        summary = "Get content approval status",
        description = "Retrieves the current status of content in the approval workflow"
    )
    @APIResponse(responseCode = "200", description = "Status retrieved successfully")
    @APIResponse(responseCode = "404", description = "Content not found")
    public Response getContentStatus(
            @Parameter(description = "Content ID", required = true)
            @PathParam("contentId") Long contentId) {
        
        logger.info("Getting status for content ID: {}", contentId);
        
        try {
            ContentStatusResponse response = contentService.getContentStatus(contentId);
            
            if ("NOT_FOUND".equals(response.getStatus())) {
                return Response.status(Response.Status.NOT_FOUND).entity(response).build();
            }
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Error getting content status for ID: {}", contentId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ContentStatusResponse.notFound(contentId))
                    .build();
        }
    }
    
    /**
     * Approves content.
     */
    @POST
    @Path("/{contentId}/approve")
    @Operation(
        summary = "Approve content",
        description = "Approves content in the approval workflow"
    )
    @APIResponse(responseCode = "200", description = "Content approved successfully")
    @APIResponse(responseCode = "400", description = "Invalid approval request")
    @APIResponse(responseCode = "404", description = "Content not found")
    public Response approveContent(
            @Parameter(description = "Content ID", required = true)
            @PathParam("contentId") Long contentId,
            @Parameter(description = "Approver ID", required = true)
            @QueryParam("approverId") String approverId,
            @Parameter(description = "Approval comments")
            @QueryParam("comments") String comments) {
        
        logger.info("Approving content {} by reviewer {}", contentId, approverId);
        
        if (approverId == null || approverId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Approver ID is required\"}")
                    .build();
        }
        
        try {
            boolean success = contentService.approveContent(contentId, approverId, comments);
            
            if (success) {
                return Response.ok("{\"message\": \"Content approval signal sent successfully\"}")
                        .build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Failed to send approval signal\"}")
                        .build();
            }
            
        } catch (Exception e) {
            logger.error("Error approving content {} by reviewer {}", contentId, approverId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Internal server error\"}")
                    .build();
        }
    }
    
    /**
     * Rejects content.
     */
    @POST
    @Path("/{contentId}/reject")
    @Operation(
        summary = "Reject content",
        description = "Rejects content in the approval workflow"
    )
    @APIResponse(responseCode = "200", description = "Content rejected successfully")
    @APIResponse(responseCode = "400", description = "Invalid rejection request")
    @APIResponse(responseCode = "404", description = "Content not found")
    public Response rejectContent(
            @Parameter(description = "Content ID", required = true)
            @PathParam("contentId") Long contentId,
            @Parameter(description = "Reviewer ID", required = true)
            @QueryParam("reviewerId") String reviewerId,
            @Parameter(description = "Rejection reason")
            @QueryParam("reason") String reason) {
        
        logger.info("Rejecting content {} by reviewer {}", contentId, reviewerId);
        
        if (reviewerId == null || reviewerId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Reviewer ID is required\"}")
                    .build();
        }
        
        try {
            boolean success = contentService.rejectContent(contentId, reviewerId, reason);
            
            if (success) {
                return Response.ok("{\"message\": \"Content rejection signal sent successfully\"}")
                        .build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Failed to send rejection signal\"}")
                        .build();
            }
            
        } catch (Exception e) {
            logger.error("Error rejecting content {} by reviewer {}", contentId, reviewerId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Internal server error\"}")
                    .build();
        }
    }
    
    /**
     * Requests changes for content.
     */
    @POST
    @Path("/{contentId}/request-changes")
    @Operation(
        summary = "Request changes for content",
        description = "Requests changes for content in the approval workflow"
    )
    @APIResponse(responseCode = "200", description = "Change request sent successfully")
    @APIResponse(responseCode = "400", description = "Invalid change request")
    @APIResponse(responseCode = "404", description = "Content not found")
    public Response requestChanges(
            @Parameter(description = "Content ID", required = true)
            @PathParam("contentId") Long contentId,
            @Parameter(description = "Reviewer ID", required = true)
            @QueryParam("reviewerId") String reviewerId,
            @Parameter(description = "Change requests", required = true)
            @QueryParam("changeRequests") String changeRequests) {
        
        logger.info("Requesting changes for content {} by reviewer {}", contentId, reviewerId);
        
        if (reviewerId == null || reviewerId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Reviewer ID is required\"}")
                    .build();
        }
        
        if (changeRequests == null || changeRequests.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Change requests are required\"}")
                    .build();
        }
        
        try {
            boolean success = contentService.requestChanges(contentId, reviewerId, changeRequests);
            
            if (success) {
                return Response.ok("{\"message\": \"Change request signal sent successfully\"}")
                        .build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Failed to send change request signal\"}")
                        .build();
            }
            
        } catch (Exception e) {
            logger.error("Error requesting changes for content {} by reviewer {}", contentId, reviewerId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Internal server error\"}")
                    .build();
        }
    }
}