package com.innovarhealthcare.channelHistory.server.controller;

import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryService;

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

public class DefaultGitRepositoryController extends GitRepositoryController {
    private Logger logger = LogManager.getLogger(this.getClass());
    private GitRepositoryService service = new GitRepositoryService();

    @Override
    public void init(Properties properties) throws GitRepositoryException {
        try {
            applySettings(properties);
        } catch (Exception e) {
            throw new GitRepositoryException(e);
        }
    }

    public boolean isEnable() {
        return service.isEnable();
    }

    public boolean isAutoCommit() {
        return service.isAutoCommit();
    }

    @Override
    public String validate(Properties properties) throws GitRepositoryException {
        try {
            return service.validateSettings(properties);
        } catch (Exception e) {
            throw new GitRepositoryException(e);
        }
    }

    @Override
    public void update(Properties properties) throws GitRepositoryException {
        try {
            applySettings(properties);
        } catch (Exception e) {
            throw new GitRepositoryException(e);
        }
    }

    @Override
    public List<String> getHistory(String fileName, String mode) throws GitRepositoryException {
        try {
            return service.getHistory(fileName, mode);
        } catch (Exception e) {
            logger.error("Failed to get history on repo", e);
            throw new GitRepositoryException(e);
        }
    }

    @Override
    public String getContent(String fileName, String revision, String mode) throws GitRepositoryException {
        try {
            return service.getContent(fileName, revision, mode);
        } catch (Exception e) {
            logger.error("Failed to get content on repo", e);
            throw new GitRepositoryException(e);
        }
    }

    @Override
    public List<String> loadChannelOnRepo() throws GitRepositoryException {
        try {
            return service.loadChannelOnRepo();
        } catch (Exception e) {
            logger.error("Failed to load channels on repo", e);
            throw new GitRepositoryException(e);
        }
    }

    @Override
    public String commitAndPushChannel(Channel channel, String message, User user) {
        return service.commitAndPushChannel(channel, message, user);
    }

    @Override
    public List<String> loadCodeTemplateOnRepo() throws GitRepositoryException {
        try {
            return service.loadCodeTemplateOnRepo();
        } catch (Exception e) {
            logger.error("Failed to load code templates on repo", e);
            throw new GitRepositoryException(e);
        }
    }

    @Override
    public String commitAndPushCodeTemplate(CodeTemplate template, String message, User user) {
        return service.commitAndPushCodeTemplate(template, message, user);
    }

    private void applySettings(Properties properties) throws GitRepositoryException {
        try {
            service.applySettings(properties);
        } catch (Exception e) {
            throw new GitRepositoryException(e);
        }
    }
}
