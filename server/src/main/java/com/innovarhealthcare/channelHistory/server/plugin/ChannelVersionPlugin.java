package com.innovarhealthcare.channelHistory.server.plugin;

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryService;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.innovarhealthcare.channelHistory.shared.util.ResponseUtil;
import com.kaurpalang.mirth.annotationsplugin.annotation.MirthServerClass;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ServerEventContext;
import com.mirth.connect.model.User;
import com.mirth.connect.plugins.ChannelPlugin;
import com.mirth.connect.server.controllers.ControllerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-12-07 9:25 AM
 */

@MirthServerClass
public class ChannelVersionPlugin implements ChannelPlugin {
    private static final Logger logger = LogManager.getLogger(ChannelVersionPlugin.class);

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
    public void save(Channel channel, ServerEventContext sec) {
    }

    @Override
    public void remove(Channel channel, ServerEventContext sec) {
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

        try {
            String response = gitService.removeChannel(channel, "Remove Channel", user);
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
    public void deploy(Channel channel, ServerEventContext arg1) {
    }

    @Override
    public void deploy(ServerEventContext sec) {
    }

    @Override
    public void undeploy(ServerEventContext sec) {
    }

    @Override
    public void undeploy(String id, ServerEventContext sec) {
    }
}
