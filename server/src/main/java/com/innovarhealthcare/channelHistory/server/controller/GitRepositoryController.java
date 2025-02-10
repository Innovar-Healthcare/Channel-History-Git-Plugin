package com.innovarhealthcare.channelHistory.server.controller;

import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;

import com.mirth.connect.server.ExtensionLoader;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.User;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Properties;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public abstract class GitRepositoryController {
    private static final Logger logger = LogManager.getLogger(GitRepositoryController.class);
    private static GitRepositoryController instance;

    public static GitRepositoryController getInstance() {
        synchronized (GitRepositoryController.class) {
            if (instance == null) {
                instance = ExtensionLoader.getInstance().getControllerInstance(GitRepositoryController.class);

                if (instance == null) {
                    instance = new DefaultGitRepositoryController();
                }
            }

            return instance;
        }
    }

    public abstract void init(Properties properties) throws GitRepositoryException;

    public abstract void start() throws GitRepositoryException;

    public abstract boolean isEnable();

    public abstract boolean isGitConnected();

    public abstract boolean isAutoCommit();

    public abstract String validate(Properties properties) throws GitRepositoryException;

    public abstract void update(Properties properties) throws GitRepositoryException;

    public abstract List<String> getHistory(String fileName, String mode) throws GitRepositoryException;

    public abstract String getContent(String fileName, String revision, String mode) throws GitRepositoryException;

    public abstract List<String> loadChannelOnRepo() throws GitRepositoryException;

    public abstract String commitAndPushChannel(Channel channel, String message, User user) throws GitRepositoryException;

    public abstract List<String> loadCodeTemplateOnRepo() throws GitRepositoryException;

    public abstract String commitAndPushCodeTemplate(CodeTemplate template, String message, User user) throws GitRepositoryException;

}
