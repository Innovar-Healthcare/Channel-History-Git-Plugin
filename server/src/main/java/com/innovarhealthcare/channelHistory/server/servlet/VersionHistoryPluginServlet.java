package com.innovarhealthcare.channelHistory.server.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Properties;

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitFileNotFoundException;
import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.server.exception.VersionHistoryApiException;
import com.innovarhealthcare.channelHistory.server.service.VersionHistoryService;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryErrorCodes;
import com.innovarhealthcare.channelHistory.shared.util.JsonUtils;
import com.kaurpalang.mirth.annotationsplugin.annotation.MirthApiProvider;
import com.kaurpalang.mirth.annotationsplugin.type.ApiProviderType;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.User;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.CodeTemplateController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.UserController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@MirthApiProvider(type = ApiProviderType.SERVER_CLASS)
public class VersionHistoryPluginServlet extends MirthServlet implements VersionHistoryServletInterface {
    private static final Logger logger = LogManager.getLogger(VersionHistoryPluginServlet.class);
    private static final UserController userController = ControllerFactory.getFactory().createUserController();
    private static final CodeTemplateController codeTemplateController = ControllerFactory.getFactory().createCodeTemplateController();

    public VersionHistoryPluginServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, VersionControlConstants.PLUGIN_POINTNAME);
    }

    @Override
    public String validateSetting(Properties properties) throws ClientException {
        try {
            return GitRepositoryController.getInstance().validate(properties);
        } catch (Exception e) {
            throw createErrorResponse(e);
        }
    }

    /**
     * Gets commit history for an entity
     *
     * @param id   Entity ID
     * @param mode Entity type: "channel", "library", "codetemplate"
     * @return JSON response with commit history
     */
    @Override
    public String getHistory(String id, String mode) {

        // Validate inputs
        if (id == null || id.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Entity ID is required");
        }
        if (mode == null || mode.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Mode is required");
        }

        logger.info("getHistory: id={}, mode={}", id, mode);

        try {
            List<CommitMetaData> history;

            // Route based on mode
            switch (mode) {
                case VersionControlConstants.MODE_CHANNEL:
                    history = getService().getChannelHistory(id);
                    break;

                case VersionControlConstants.MODE_CODE_TEMPLATE_LIBRARY:
                    history = getService().getLibraryHistory(id);
                    break;

                case VersionControlConstants.MODE_CODE_TEMPLATE:
                    history = getService().getCodeTemplateHistory(id);
                    break;

                default:
                    throw new IllegalArgumentException("Invalid mode: " + mode + ". Must be 'channel', 'library', or 'codetemplate'");
            }

            return JsonUtils.toJson(history);
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: id={}, mode={}", id, mode);
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");

        } catch (IllegalArgumentException e) {
            logger.error("Validation error: id={}, mode={}", id, mode);
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, e.getMessage());

        } catch (GitOperationException e) {
            logger.error("Git operation failed: id={}, mode={}", id, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get commit history: " + e.getMessage());

        } catch (VersionHistoryApiException e) {
            throw e;

        } catch (Exception e) {
            logger.error("Unexpected error: id={}, mode={}", id, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get commit history: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    /**
     * Gets entity content from repository at a specific revision
     *
     * @param id       Entity ID
     * @param revision Commit SHA or ref (e.g., "HEAD", commit hash)
     * @param mode     Entity type: "channel", "library", "codetemplate"
     * @return Entity content as XML string
     */
    @Override
    public String getContentAtRevision(String id, String revision, String mode) {

        // Validate inputs
        if (id == null || id.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Entity ID is required");
        }
        if (revision == null || revision.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Revision is required");
        }
        if (mode == null || mode.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Mode is required");
        }

        logger.info("getContentAtRevision: id={}, revision={}, mode={}", id, revision, mode);

        try {
            // Route based on mode
            switch (mode) {
                case VersionControlConstants.MODE_CHANNEL:
                    return getService().getChannelContentAtRevision(id, revision);

                case VersionControlConstants.MODE_CODE_TEMPLATE_LIBRARY:
                    return getService().getLibraryContentAtRevision(id, revision);

                case VersionControlConstants.MODE_CODE_TEMPLATE:
                    return getService().getCodeTemplateContentAtRevision(id, revision);

                default:
                    throw new IllegalArgumentException("Invalid mode: " + mode + ". Must be 'channel', 'library', or 'codetemplate'");
            }

        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: id={}, revision={}, mode={}", id, revision, mode);
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");

        } catch (GitFileNotFoundException e) {
            logger.warn("File not found: id={}, revision={}, mode={}", id, revision, mode);
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.FILE_NOT_FOUND, "File not found: " + id + " at revision: " + revision);

        } catch (IllegalArgumentException e) {
            logger.error("Validation error: id={}, revision={}, mode={}", id, revision, mode);
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, e.getMessage());

        } catch (GitOperationException e) {
            logger.error("Git operation failed: id={}, revision={}, mode={}", id, revision, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get content: " + e.getMessage());

        } catch (VersionHistoryApiException e) {
            throw e;

        } catch (Exception e) {
            logger.error("Unexpected error: id={}, revision={}, mode={}", id, revision, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get content: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @Override
    public String loadChannelsMetadata() {
        try {
            List<RepoItemMetadata> metadataList = getService().loadChannelsMetadata();
            return JsonUtils.toJson(metadataList);

        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected...");

        } catch (VersionHistoryApiException e) {
            // Already a VersionHistoryApiException, rethrow as-is
            throw e;

        } catch (Exception e) {
            logger.error("Unexpected error loading channels from repo", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to load channels from repository: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @Override
    public String commitAndPushChannel(Channel channel, String message, String userId) {
        // Validate channel
        if (channel == null) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Channel is required");
        }

        // Validate commit message
        if (message == null || message.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit message is required");
        }

        // Validate and get user
        if (userId == null || userId.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "User ID is required");
        }

        User user;
        try {
            user = userController.getUser(Integer.valueOf(userId), null);
            if (user == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
            }
        } catch (NumberFormatException e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid user ID format: " + userId);
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
        }

        // Call service
        try {
            return getService().saveChannelAndPush(channel, message, user);

        } catch (GitNotConnectedException e) {
            // Git not available - 503 Service Unavailable
            logger.error("Git not connected: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");

        } catch (GitPushFailedException e) {
            // Push failed - 409 Conflict
            logger.error("Push failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.PUSH_REJECTED, "Push rejected: " + e.getMessage());

        } catch (GitOperationException e) {
            // Other Git operations failed - 500 Internal Server Error
            logger.error("Git operation failed: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Git operation failed: " + e.getMessage());

        } catch (IllegalArgumentException e) {
            // Validation failed (from Service layer) - 400 Bad Request
            logger.error("Validation failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Validation failed: " + e.getMessage());

        } catch (Exception e) {
            // Unexpected error - 500 Internal Server Error
            logger.error("Unexpected error saving channel", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to save libraries: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @Override
    public String loadCodeTemplatesMetadata() {
        try {
            List<RepoItemMetadata> metadataList = getService().loadCodeTemplatesMetadata();
            return JsonUtils.toJson(metadataList);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected...");
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error loading code templates from repo", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to load code templates from repository: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @Override
    public String commitAndPushCodeTemplate(String codeTemplateId, String message, String userId) {
        // Validate code template ID
        if (codeTemplateId == null || codeTemplateId.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Code template ID is required");
        }

        // Validate commit message
        if (message == null || message.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit message is required");
        }

        // Validate and get user
        if (userId == null || userId.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "User ID is required");
        }

        User user;
        try {
            user = userController.getUser(Integer.valueOf(userId), null);
            if (user == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
            }
        } catch (NumberFormatException e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid user ID format: " + userId);
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
        }

        // Get code template
        CodeTemplate template;
        try {
            template = codeTemplateController.getCodeTemplateById(codeTemplateId);
            if (template == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "Code template not found: " + codeTemplateId);
            }
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "Code template not found: " + codeTemplateId);
        }

        // Call service
        try {
            return getService().saveCodeTemplateAndPush(template, message, user);

        } catch (GitNotConnectedException e) {
            // Git not available - 503 Service Unavailable
            logger.error("Git not connected: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");

        } catch (GitPushFailedException e) {
            // Push failed - 409 Conflict
            logger.error("Push failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.PUSH_REJECTED, "Push rejected: " + e.getMessage());

        } catch (GitOperationException e) {
            // Other Git operations failed - 500 Internal Server Error
            logger.error("Git operation failed: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Git operation failed: " + e.getMessage());

        } catch (IllegalArgumentException e) {
            // Validation failed (from Service layer) - 400 Bad Request
            logger.error("Validation failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Validation failed: " + e.getMessage());

        } catch (Exception e) {
            // Unexpected error - 500 Internal Server Error
            logger.error("Unexpected error saving code template", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to save libraries: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @Override
    public String saveLibraries(List<CodeTemplateLibrary> libraries, String message, String userId) {
        // Validate libraries
        if (libraries == null) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Channel is required");
        }

        // Validate commit message
        if (message == null || message.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit message is required");
        }

        // Validate and get user
        if (userId == null || userId.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "User ID is required");
        }

        User user;
        try {
            user = userController.getUser(Integer.valueOf(userId), null);
            if (user == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
            }
        } catch (NumberFormatException e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid user ID format: " + userId);
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
        }

        try {
            return getService().saveLibrariesAndPush(libraries, message, user);

        } catch (GitNotConnectedException e) {
            // Git not available - 503 Service Unavailable
            logger.error("Git not connected: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");

        } catch (GitPushFailedException e) {
            // Push failed - 409 Conflict
            logger.error("Push failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.PUSH_REJECTED, "Push rejected: " + e.getMessage());

        } catch (GitOperationException e) {
            // Other Git operations failed - 500 Internal Server Error
            logger.error("Git operation failed: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Git operation failed: " + e.getMessage());

        } catch (IllegalArgumentException e) {
            // Validation failed (from Service layer) - 400 Bad Request
            logger.error("Validation failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Validation failed: " + e.getMessage());

        } catch (Exception e) {
            // Unexpected error - 500 Internal Server Error
            logger.error("Unexpected error saving libraries", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to save libraries: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    private VersionHistoryService getService() {
        return GitRepositoryController.getInstance().getVersionHistoryService();
    }

    /**
     * Create structured error response from exception
     * Maps domain exceptions to user-friendly error codes and messages
     */
    private ClientException createErrorResponse(Exception e) {
        ErrorResponse error;

        // Map specific exceptions
        if (e instanceof GitNotConnectedException) {
            error = new ErrorResponse(VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "it repository is not connected...");

        } else if (e instanceof FileNotFoundException) {
            error = new ErrorResponse(VersionHistoryErrorCodes.FILE_NOT_FOUND, "File not found in repository: " + e.getMessage());

        } else if (e instanceof ClientException) {
            // Re-throw ClientException as-is (for validation errors)
            return (ClientException) e;

        } else {
            // Default error for unexpected exceptions
            error = new ErrorResponse(VersionHistoryErrorCodes.UNKNOWN_ERROR, e.getMessage() != null ? e.getMessage() : "An unexpected error occurred");
        }

        try {
            return new ClientException(JsonUtils.toJson(error), e);
        } catch (Exception jsonError) {
            // Fallback if JSON serialization fails
            logger.error("Failed to serialize error response", jsonError);
            return new ClientException(error.getMessage(), e);
        }
    }
}
