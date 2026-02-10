package com.innovarhealthcare.channelHistory.server.plugin;

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryServiceLegacy;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.innovarhealthcare.channelHistory.shared.util.ResponseUtil;
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
    private static Logger logger = LogManager.getLogger(CodeTemplateVersionPlugin.class);

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
        GitRepositoryController controller = GitRepositoryController.getInstance();
        GitRepositoryServiceLegacy gitService = controller.getService();
        VersionHistoryProperties versionHistoryProperties = gitService.getVersionHistoryProperties();

        if (!controller.isEnable()) {
            logger.debug("Git repository is disabled, skipping remove.");
            return;
        }

        if (!controller.isGitConnected()) {
            logger.debug("Git repository is not connected, skipping remove.");
            return;
        }

        if (!versionHistoryProperties.isEnableSyncDelete()) {
            logger.debug("Sync Delete is disabled.");
            return;
        }

        User user;

        try {
            user = ControllerFactory.getFactory().createUserController().getUser(sec.getUserId(), null);
            if (user == null) {
                logger.error("Failed to retrieve user for ID: " + sec.getUserId());
                return;
            }
        } catch (ControllerException e) {
            logger.error("Failed to retrieve user for ID: " + sec.getUserId() + ". Error: " + e.getMessage());
            return;
        }

        try {
            String response = gitService.removeCodeTemplate(ct, "Remove Code Template", user);
            ResponseUtil responseUtil = new ResponseUtil(response);
            if (!responseUtil.isSuccess()) {
                logger.error(responseUtil.getOperationDetails());
            }
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
        GitRepositoryController controller = GitRepositoryController.getInstance();
        GitRepositoryServiceLegacy gitService = controller.getService();
        VersionHistoryProperties versionHistoryProperties = gitService.getVersionHistoryProperties();

        if (!controller.isEnable()) {
            logger.debug("Git repository is disabled, skipping auto-commit.");
            return;
        }

        if (!controller.isGitConnected()) {
            logger.debug("Git repository is not connected, skipping auto-commit.");
            return;
        }

        if (!controller.isAutoCommit()) {
            logger.debug("Auto-commit is disabled, skipping auto-commit.");
            return;
        }

        User user;

        try {
            user = ControllerFactory.getFactory().createUserController().getUser(sec.getUserId(), null);
            if (user == null) {
                logger.error("Failed to retrieve user for ID: " + sec.getUserId());
                return;
            }
        } catch (ControllerException e) {
            logger.error("Failed to retrieve user for ID: " + sec.getUserId() + ". Error: " + e.getMessage());
            return;
        }

        // Commit and push
        try {
            String message = versionHistoryProperties.getAutoCommitMsg();
            String result = gitService.commitAndPushCodeTemplate(ct, message, user);

            ResponseUtil responseUtil = new ResponseUtil(result);
            if (!responseUtil.isSuccess()) {
                logger.error(responseUtil.getOperationDetails());
            }
        } catch (GitNotConnectedException e) {
            logger.warn("Git repository not connected", e);
        } catch (Exception e) {
            logger.error("Unexpected error while committing and pushing CodeTemplate ID: {}. Error: {}", ct.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void save(CodeTemplateLibrary ctLib, ServerEventContext sec) {
    }
}
