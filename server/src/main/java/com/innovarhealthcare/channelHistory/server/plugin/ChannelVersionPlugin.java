package com.innovarhealthcare.channelHistory.server.plugin;

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthServerClass;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ServerEventContext;
import com.mirth.connect.model.User;
import com.mirth.connect.plugins.ChannelPlugin;
import com.mirth.connect.server.controllers.ControllerFactory;

import org.apache.log4j.Logger;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-12-07 9:25 AM
 */

@MirthServerClass
public class ChannelVersionPlugin implements ChannelPlugin {
    private static Logger logger = Logger.getLogger(ChannelVersionPlugin.class);

    @Override
    public String getPluginPointName() {
        return VersionControlConstants.PLUGIN_POINTNAME;
    }

    @Override
    public void start() {
        GitRepositoryController.getInstance();
    }

    @Override
    public void stop() {
    }

    @Override
    public void save(Channel channel, ServerEventContext sec) {
        if (!GitRepositoryController.getInstance().isEnable()) {
            return;
        }

        if (!GitRepositoryController.getInstance().isAutoCommit()) {
            return;
        }

        User user = null;

        try {
            user = ControllerFactory.getFactory().createUserController().getUser(sec.getUserId(), null);
        } catch (ControllerException e) {
            throw new RuntimeException(e);
        }

        GitRepositoryController.getInstance().commitAndPushChannel(channel, VersionControlConstants.AUTO_COMMITTED_MSG, user);
    }

    @Override
    public void remove(Channel channel, ServerEventContext sec) {
        // Thai Tran: not implement yet
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
