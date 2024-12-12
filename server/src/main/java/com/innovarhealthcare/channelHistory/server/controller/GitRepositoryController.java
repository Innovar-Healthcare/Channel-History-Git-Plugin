package com.innovarhealthcare.channelHistory.server.controller;

import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.User;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

import java.util.List;
import java.util.Properties;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public abstract class GitRepositoryController {
    private static GitRepositoryController instance;

    public static GitRepositoryController getInstance() {
        synchronized (GitRepositoryController.class) {
            if (instance == null) {
                instance = new DefaultGitRepositoryController();

                ExtensionController ec = ControllerFactory.getFactory().createExtensionController();
                Properties properties = null;
                try {
                    properties = ec.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
                } catch (ControllerException e) {
                    throw new RuntimeException(e);
                }

                try {
                    instance.init(properties);
                } catch (GitRepositoryException e) {
                    throw new RuntimeException(e);
                }
            }

            return instance;
        }
    }

    public abstract void init(Properties properties) throws GitRepositoryException;

    public abstract boolean isEnable();

    public abstract boolean isAutoCommit();

    public abstract String validate(Properties properties) throws GitRepositoryException;

    public abstract void update(Properties properties) throws GitRepositoryException;

    public abstract List<String> getHistory(String fileName, String mode) throws GitRepositoryException;

    public abstract String getContent(String fileName, String revision, String mode) throws GitRepositoryException;

    public abstract List<String> loadChannelOnRepo() throws GitRepositoryException;

    public abstract String commitAndPushChannel(Channel channel, String message, User user);

    public abstract List<String> loadCodeTemplateOnRepo() throws GitRepositoryException;

    public abstract String commitAndPushCodeTemplate(CodeTemplate template, String message, User user);

}
