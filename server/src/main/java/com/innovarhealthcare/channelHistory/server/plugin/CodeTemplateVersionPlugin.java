package com.innovarhealthcare.channelHistory.server.plugin;

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.server.service.VersionHistoryService;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.kaurpalang.mirth.annotationsplugin.annotation.MirthServerClass;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.ServerEventContext;
import com.mirth.connect.model.User;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.plugins.CodeTemplateServerPlugin;
import com.mirth.connect.server.controllers.ControllerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-12-07 9:25 AM
 */

@MirthServerClass
public class CodeTemplateVersionPlugin implements CodeTemplateServerPlugin {
    private static final Logger logger = LogManager.getLogger(CodeTemplateVersionPlugin.class);

    @Override
    public String getPluginPointName() {
        return VersionControlConstants.PLUGIN_POINTNAME;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void remove(CodeTemplate ct, ServerEventContext sec) {
        VersionHistoryService service = GitRepositoryController.getInstance().getVersionHistoryService();

        if (!service.isEnableSyncDelete()) {
            logger.debug("Sync Delete is disabled.");
            return;
        }

        if (!service.isGitAvailable()) {
            logger.debug("Git not available: {}", service.getGitStatus().getMessage());
            return;
        }

        User user;

        try {
            user = ControllerFactory.getFactory().createUserController().getUser(sec.getUserId(), null);
            if (user == null) {
                logger.error("Failed to retrieve user for ID: {}", sec.getUserId());
                return;
            }
        } catch (ControllerException e) {
            logger.error("User not found: {}. Exception: {}", sec.getUserId(), e.getMessage());
            return;
        }

        try {
            service.deleteCodeTemplateAndPush(ct, "Remove Code Template", user);
        } catch (GitNotConnectedException e) {
            logger.warn("Git repository not connected", e);
        } catch (Exception e) {
            logger.error("Unexpected error during commit and push", e);
        }
    }

    @Override
    public void remove(CodeTemplateLibrary ctLib, ServerEventContext sec) {
    }

    @Override
    public void save(CodeTemplate ct, ServerEventContext sec) {
        // Check Git configuration
        VersionHistoryService service = GitRepositoryController.getInstance().getVersionHistoryService();

        if (!service.isAutoCommitEnabled()) {
            logger.debug("Auto-commit is disabled, skipping auto-commit.");
            return;
        }

        if (!service.isGitAvailable()) {
            logger.debug("Git not available: {}", service.getGitStatus().getMessage());
            return;
        }

        User user;

        try {
            user = ControllerFactory.getFactory().createUserController().getUser(sec.getUserId(), null);
            if (user == null) {
                logger.error("User not found: {}", sec.getUserId());
                return;
            }
        } catch (ControllerException e) {
            logger.error("User not found: {}. Exception: {}", sec.getUserId(), e.getMessage());
            return;
        }

        // Commit and push
        try {
            // Auto Commit: message = ""
            service.saveCodeTemplateAndPush(ct, "", user);
        } catch (GitNotConnectedException e) {
            // Git not available - 503 Service Unavailable
            logger.error("Git not connected: {}", e.getMessage());
        } catch (GitPushFailedException e) {
            // Push failed - 409 Conflict
            logger.error("Push failed: {}", e.getMessage());
        } catch (GitOperationException e) {
            // Other Git operations failed - 500 Internal Server Error
            logger.error("Git operation failed: {}", e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // Validation failed (from Service layer) - 400 Bad Request
            logger.error("Validation failed: {}", e.getMessage());
        } catch (Exception e) {
            // Unexpected error - 500 Internal Server Error
            logger.error("Unexpected error saving code template", e);
        }
    }

    @Override
    public void save(CodeTemplateLibrary ctLib, ServerEventContext sec) {
    }
}
