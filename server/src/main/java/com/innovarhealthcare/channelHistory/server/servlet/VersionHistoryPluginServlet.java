package com.innovarhealthcare.channelHistory.server.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Properties;

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryService;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
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
    private static final UserController userController = ControllerFactory.getFactory().createUserController();
    private static final CodeTemplateController codeTemplateController = ControllerFactory.getFactory().createCodeTemplateController();
    private static final Logger logger = LogManager.getLogger(VersionHistoryPluginServlet.class);

    public VersionHistoryPluginServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, VersionControlConstants.PLUGIN_POINTNAME);
    }

    @Override
    public String validateSetting(Properties properties) throws ClientException {
        try {
            return GitRepositoryController.getInstance().validate(properties);
        } catch (Exception e) {
            throw new ClientException(e);
        }
    }


    @Override
    public List<String> getHistory(String fileName, String mode) throws ClientException {
        try {
            return GitRepositoryController.getInstance().getHistory(fileName, mode);
        } catch (Exception e) {
            logger.warn("failed to get the history of file " + fileName, e);
            throw new ClientException(e);
        }
    }

    @Override
    public String getFileContentFromRepo(String fileName, String revision, String mode) throws ClientException {
        // Validate inputs
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new ClientException("File name is required");
        }
        if (mode == null || mode.trim().isEmpty()) {
            throw new ClientException("Mode is required");
        }

        try {
            return getService().getFileContentFromRepo(fileName, revision, mode);
        } catch (GitNotConnectedException e) {
            logger.warn("Git repository not connected", e);
            throw new ClientException("Git is not configured. Please go to Settings → Version History to set up your repository.");
        } catch (Exception e) {
            logger.error("Failed to get file content from repo: {} at revision: {} with mode: {}", fileName, revision, mode, e);
            throw new ClientException("An unexpected error occurred: " + e.getMessage(), e);
        }
    }

    @Override
    public String loadChannelOnRepo() throws ClientException {
        // Call service
        try {
            List<RepoItemMetadata> metadataList = getService().loadChannelOnRepo();
            return JsonUtils.toJson(metadataList);
        } catch (GitNotConnectedException e) {
            logger.warn("Git repository not connected", e);
            throw new ClientException("Git is not configured. Please go to Settings → Version History to set up your repository.");
        } catch (Exception e) {
            logger.error("Unexpected error during load channels on repo", e);
            throw new ClientException("An unexpected error occurred: " + e.getMessage(), e);
        }
    }

    @Override
    public String commitAndPushChannel(Channel channel, String message, String userId) throws ClientException {
        // Validate channel
        if (channel == null) {
            throw new ClientException("Channel is not found");
        }

        // Validate and get user
        if (userId == null || userId.trim().isEmpty()) {
            throw new ClientException("User ID is required");
        }

        User user;
        try {
            user = userController.getUser(Integer.valueOf(userId), null);
            if (user == null) {
                throw new ClientException("User not found");
            }
        } catch (NumberFormatException e) {
            throw new ClientException("Invalid user ID format");
        } catch (ControllerException e) {
            throw new ClientException("User is not found");
        }

        // Call service
        try {
            return getService().commitAndPushChannel(channel, message, user);
        } catch (GitNotConnectedException e) {
            logger.warn("Git repository not connected", e);
            throw new ClientException("Git is not configured. Please go to Settings → Version History to set up your repository.");
        } catch (Exception e) {
            logger.error("Unexpected error during commit and push", e);
            throw new ClientException("An unexpected error occurred: " + e.getMessage(), e);
        }
    }

    @Override
    public String loadCodeTemplateOnRepo() throws ClientException {
        try {
            List<RepoItemMetadata> metadataList = getService().loadCodeTemplateOnRepo();
            return JsonUtils.toJson(metadataList);
        } catch (GitNotConnectedException e) {
            logger.warn("Git repository not connected", e);
            throw new ClientException("Git is not configured. Please go to Settings → Version History to set up your repository.");
        } catch (Exception e) {
            logger.error("Unexpected error during load code templates on repo", e);
            throw new ClientException("An unexpected error occurred: " + e.getMessage(), e);
        }
    }

    @Override
    public String commitAndPushCodeTemplate(String codeTemplateId, String message, String userId) throws ClientException {
        // Validate codeTemplateId
        if (codeTemplateId == null || codeTemplateId.trim().isEmpty()) {
            throw new ClientException("Code Template ID is required");
        }

        // Validate userId
        if (userId == null || userId.trim().isEmpty()) {
            throw new ClientException("User ID is required");
        }

        // Get user
        User user;
        try {
            user = userController.getUser(Integer.valueOf(userId), null);
            if (user == null) {
                throw new ClientException("User not found");
            }
        } catch (NumberFormatException e) {
            throw new ClientException("Invalid user ID format");
        } catch (ControllerException e) {
            throw new ClientException("User is not found");
        }

        // Get code template
        CodeTemplate template;
        try {
            template = codeTemplateController.getCodeTemplateById(codeTemplateId);
            if (template == null) {
                throw new ClientException("Code Template not found");
            }
        } catch (ControllerException e) {
            throw new ClientException("Code Template is not found");
        }

        // Call service
        try {
            return getService().commitAndPushCodeTemplate(template, message, user);
        } catch (GitNotConnectedException e) {
            logger.warn("Git repository not connected", e);
            throw new ClientException("Git is not configured. Please go to Settings → Version History to set up your repository.");
        } catch (Exception e) {
            logger.error("Unexpected error during commit and push code template", e);
            throw new ClientException("An unexpected error occurred: " + e.getMessage(), e);
        }
    }

    private GitRepositoryService getService() {
        GitRepositoryController ctrl = GitRepositoryController.getInstance();
        return ctrl.getService();
    }
}
