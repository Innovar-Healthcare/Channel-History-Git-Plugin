package com.innovarhealthcare.channelHistory.server.controller;

import java.util.Properties;

import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryService;
import com.innovarhealthcare.channelHistory.server.service.GitRepositoryServiceLegacy;
import com.innovarhealthcare.channelHistory.server.service.VersionHistoryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class GitRepositoryController {
    private static final Logger logger = LogManager.getLogger(GitRepositoryController.class);
    private final GitRepositoryServiceLegacy service = new GitRepositoryServiceLegacy();

    private GitRepositoryService gitService;
    private VersionHistoryService versionHistoryService;
    private Properties properties;

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
        logger.info("Initializing Version History Plugin...");

        if (properties == null) {
            throw new GitRepositoryException("Properties cannot be null");
        }

        try {
            // Store config
            this.properties = properties;

            // Create service objects
            this.gitService = new GitRepositoryService();
            this.versionHistoryService = new VersionHistoryService(gitService);

            logger.info("Plugin initialized (services created)");

        } catch (Exception e) {
            logger.error("Initialization failed", e);
            throw new GitRepositoryException("Init failed", e);
        }
    }

    public void start() throws GitRepositoryException {
        logger.info("Starting Version History Plugin...");

        if (gitService == null) {
            throw new GitRepositoryException("Not initialized. Call init() first.");
        }

        try {
            // Start Git infrastructure (may take time)
            gitService.startGit(properties);

            // Log Git status
            if (gitService.isGitAvailable()) {
                logger.info("✅ Git is AVAILABLE and ready");
            } else {
                logger.warn("⚠️  Git is UNAVAILABLE: {}", gitService.getGitUnavailableReason());
            }

            logger.info("Plugin started successfully");

        } catch (Exception e) {
            logger.error("Start failed", e);
            throw new GitRepositoryException("Start failed", e);
        }
    }

    /**
     * Updates plugin configuration and restarts Git infrastructure.
     *
     * @param newProperties New configuration properties
     * @throws GitRepositoryException if update fails
     */
    public void update(Properties newProperties) throws GitRepositoryException {
        logger.info("Updating Version History Plugin configuration...");

        if (newProperties == null) {
            throw new GitRepositoryException("Properties cannot be null");
        }

        if (gitService == null) {
            throw new GitRepositoryException("Plugin not initialized. Call init() first.");
        }

        try {
            gitService.stopGit();

            this.properties = newProperties;

            gitService.startGit(newProperties);

            if (gitService.isGitAvailable()) {
                logger.info("✅ Git started successfully and is AVAILABLE");
            } else {
                logger.warn("⚠️  Git started but is UNAVAILABLE: {}", gitService.getGitUnavailableReason());
            }

        } catch (Exception e) {
            logger.error("Configuration update FAILED: {}", e.getMessage());

            throw new GitRepositoryException("Configuration update failed: " + e.getMessage(), e);
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

    public GitRepositoryServiceLegacy getService() {
        return service;
    }

    public VersionHistoryService getVersionHistoryService() {
        return versionHistoryService;
    }

    public String validate(Properties properties) throws GitRepositoryException {
        try {
            return service.validateSettings(properties);
        } catch (Exception e) {
            throw new GitRepositoryException(e);
        }
    }


}
