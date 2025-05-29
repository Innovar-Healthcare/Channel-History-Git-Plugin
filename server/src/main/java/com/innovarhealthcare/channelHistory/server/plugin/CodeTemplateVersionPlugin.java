package com.innovarhealthcare.channelHistory.server.plugin;

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryService;
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

import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-12-07 9:25 AM
 */

@MirthServerClass
public class CodeTemplateVersionPlugin implements CodeTemplateServerPlugin {
    private static Logger logger = Logger.getLogger(CodeTemplateVersionPlugin.class);

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
        GitRepositoryService gitService = controller.getService();
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

        String response = gitService.removeCodeTemplate(ct, "Remove Code Template", user);
        ResponseUtil responseUtil = new ResponseUtil(response);
        if (!responseUtil.isSuccess()) {
            logger.error(responseUtil.getOperationDetails());
        }
    }

    @Override
    public void remove(CodeTemplateLibrary ctLib, ServerEventContext sec) {
    }

    @Override
    public void save(CodeTemplate ct, ServerEventContext sec) {
        // Check Git configuration
        GitRepositoryController controller = GitRepositoryController.getInstance();
        GitRepositoryService gitService = controller.getService();
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
            String result = controller.commitAndPushCodeTemplate(ct, message, user);
            JSONObject jsonResult = new JSONObject(result);
            if (!"success".equals(jsonResult.getString("validate"))) {
                logger.error("Failed to commit and push CodeTemplate ID: " + ct.getId() + ". Error: " + jsonResult.getString("body"));
            } else {
                logger.debug("Successfully committed and pushed CodeTemplate ID: " + ct.getId());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while committing and pushing CodeTemplate ID: " + ct.getId() + ". Error: " + e.getMessage());
        }
    }

    @Override
    public void save(CodeTemplateLibrary ctLib, ServerEventContext sec) {
    }
}
