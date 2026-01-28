package com.innovarhealthcare.channelHistory.server.controller;

import java.util.Properties;

import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class GitRepositoryController {
    private static final Logger logger = LogManager.getLogger(GitRepositoryController.class);
    private final GitRepositoryService service = new GitRepositoryService();

    private static GitRepositoryController instance;

    public static GitRepositoryController getInstance() {
        synchronized (GitRepositoryController.class) {
            if (instance == null) {
                instance = new GitRepositoryController();
            }

            return instance;
        }
    }

    public void init(Properties properties) throws GitRepositoryException {
        try {
            service.init(properties);
        } catch (Exception e) {
            throw new GitRepositoryException(e);
        }
    }

    public void start() throws GitRepositoryException {
        try {
            service.startGit();
        } catch (Exception e) {
            throw new GitRepositoryException(e);
        }
    }

    public boolean isEnable() {
        return service.isEnable();
    }

    public boolean isGitConnected() {
        return service.isGitConnected();
    }

    public boolean isAutoCommit() {
        return service.isAutoCommit();
    }

    public GitRepositoryService getService() {
        return service;
    }

    public String validate(Properties properties) throws GitRepositoryException {
        try {
            return service.validateSettings(properties);
        } catch (Exception e) {
            throw new GitRepositoryException(e);
        }
    }

    public void update(Properties properties) throws GitRepositoryException {
        try {
            service.applySettings(properties);
        } catch (Exception e) {
            throw new GitRepositoryException(e);
        }
    }
}
