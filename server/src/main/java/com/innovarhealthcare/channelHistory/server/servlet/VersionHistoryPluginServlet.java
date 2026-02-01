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
import com.innovarhealthcare.channelHistory.server.exception.VersionHistoryApiException;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryService;
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

    @Override
    public String getHistory(String fileName, String mode) {
        // Validate inputs
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "File name is required.");
        }
        if (mode == null || mode.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Mode is required.");
        }

        try {
            List<CommitMetaData> commitList;
            try {
                commitList = getService().getHistory(fileName, mode);
            } catch (GitNotConnectedException e) {
                throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected...");
            }

            return JsonUtils.toJson(commitList);
        } catch (VersionHistoryApiException e) {
            // Rethrow directly if it's already our custom API exception
            throw e;

        } catch (Exception e) {
            logger.warn("Failed to get the history of file {}", fileName, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get file history: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @Override
    public String getFileContentFromRepo(String fileName, String revision, String mode) {
        // Validate inputs
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "File name is required");
        }
        if (revision == null || revision.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Revision is required");
        }
        if (mode == null || mode.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Mode is required");
        }

        try {
            return getService().getFileContentFromRepo(fileName, revision, mode);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected...");
        } catch (GitFileNotFoundException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.FILE_NOT_FOUND, "File not found in repository: " + fileName);
        } catch (IllegalArgumentException e) {
            // Invalid mode from service
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, e.getMessage());
        } catch (GitOperationException e) {
            logger.error("Git operation failed: fileName={}, revision={}, mode={}", fileName, revision, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_NETWORK_ERROR, "Failed to get file content: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting file content: fileName={}, revision={}, mode={}", fileName, revision, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get file content: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @Override
    public String loadChannelOnRepo() {
        try {
            List<RepoItemMetadata> metadataList = getService().loadChannelOnRepo();
            return JsonUtils.toJson(metadataList);

        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected...");

        } catch (GitOperationException e) {
            logger.error("Git operation failed while loading channels", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_NETWORK_ERROR, "Failed to load channels from repository: " + e.getMessage());

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
            return getService().commitAndPushChannel(channel, message, user);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected...");
        } catch (GitOperationException e) {
            logger.error("Git operation failed: channelId={}, userId={}", channel.getId(), userId, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_NETWORK_ERROR, "Git operation failed: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error committing channel: channelId={}, userId={}", channel.getId(), userId, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to commit and push channel: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @Override
    public String loadCodeTemplateOnRepo() {
        try {
            List<RepoItemMetadata> metadataList = getService().loadCodeTemplateOnRepo();
            return JsonUtils.toJson(metadataList);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected...");
        } catch (GitOperationException e) {
            logger.error("Git operation failed while loading code templates", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_NETWORK_ERROR, "Failed to load code templates from repository: " + e.getMessage());
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
            return getService().commitAndPushCodeTemplate(template, message, user);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "it repository is not connected...");
        } catch (GitOperationException e) {
            logger.error("Git operation failed: templateId={}, userId={}", codeTemplateId, userId, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_NETWORK_ERROR, "Git operation failed: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error committing code template: templateId={}, userId={}", codeTemplateId, userId, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to commit and push code template: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    private GitRepositoryService getService() {
        GitRepositoryController ctrl = GitRepositoryController.getInstance();
        return ctrl.getService();
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
