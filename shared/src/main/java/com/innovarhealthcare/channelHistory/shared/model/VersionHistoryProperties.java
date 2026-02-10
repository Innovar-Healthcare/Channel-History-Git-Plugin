package com.innovarhealthcare.channelHistory.shared.model;

import java.util.Properties;

public class VersionHistoryProperties {
    public static final String VERSION_HISTORY_ENABLE = "versionHistory.enable";
    public static final String VERSION_HISTORY_AUTO_COMMIT_ENABLE = "versionHistory.auto.commit.enable";
    public static final String VERSION_HISTORY_AUTO_COMMIT_PROMPT = "versionHistory.auto.commit.prompt";
    public static final String VERSION_HISTORY_AUTO_COMMIT_MSG = "versionHistory.auto.commit.message";
    public static final String VERSION_HISTORY_SYNC_DELETE = "versionHistory.syncDelete";

    public static final String VERSION_HISTORY_REMOTE_REPO_URL = "versionHistory.remote.url";
    public static final String VERSION_HISTORY_REMOTE_BRANCH = "versionHistory.remote.branch";
    public static final String VERSION_HISTORY_REMOTE_SSH_KEY = "versionHistory.remote.ssh.key";

    private boolean enableVersionHistory;
    private boolean enableAutoCommit;
    private boolean enableAutoCommitPrompt;
    private String autoCommitMsg;
    private boolean enableSyncDelete;
    private GitSettings gitSettings;

    public VersionHistoryProperties() {
        enableVersionHistory = false;
        enableAutoCommit = false;
        enableAutoCommitPrompt = false;
        autoCommitMsg = "";
        enableSyncDelete = false;
    }

    public VersionHistoryProperties(Properties properties) {
        fromProperties(properties);
    }

    public Properties toProperties() {
        Properties properties = new Properties();

        // Boolean properties - always safe
        properties.setProperty(VERSION_HISTORY_ENABLE, String.valueOf(enableVersionHistory));
        properties.setProperty(VERSION_HISTORY_AUTO_COMMIT_ENABLE, String.valueOf(enableAutoCommit));
        properties.setProperty(VERSION_HISTORY_AUTO_COMMIT_PROMPT, String.valueOf(enableAutoCommitPrompt));
        properties.setProperty(VERSION_HISTORY_SYNC_DELETE, String.valueOf(enableSyncDelete));

        // String property - handle null
        properties.setProperty(VERSION_HISTORY_AUTO_COMMIT_MSG, autoCommitMsg != null ? autoCommitMsg : "");

        // Git settings - handle null safely
        if (gitSettings != null) {
            properties.setProperty(VERSION_HISTORY_REMOTE_REPO_URL, gitSettings.getRemoteRepositoryUrl() != null ? gitSettings.getRemoteRepositoryUrl() : "");

            properties.setProperty(VERSION_HISTORY_REMOTE_BRANCH, gitSettings.getBranchName() != null ? gitSettings.getBranchName() : "");

            properties.setProperty(VERSION_HISTORY_REMOTE_SSH_KEY, gitSettings.getSshPrivateKey() != null ? gitSettings.getSshPrivateKey() : "");
        } else {
            // GitSettings is null - set empty defaults
            properties.setProperty(VERSION_HISTORY_REMOTE_REPO_URL, "");
            properties.setProperty(VERSION_HISTORY_REMOTE_BRANCH, "");
            properties.setProperty(VERSION_HISTORY_REMOTE_SSH_KEY, "");
        }

        return properties;
    }

    public void fromProperties(Properties properties) {

        // Boolean properties with defaults
        enableVersionHistory = getBooleanProperty(properties, VERSION_HISTORY_ENABLE, false);
        enableAutoCommit = getBooleanProperty(properties, VERSION_HISTORY_AUTO_COMMIT_ENABLE, false);
        enableAutoCommitPrompt = getBooleanProperty(properties, VERSION_HISTORY_AUTO_COMMIT_PROMPT, false);
        enableSyncDelete = getBooleanProperty(properties, VERSION_HISTORY_SYNC_DELETE, false);

        // String properties
        autoCommitMsg = getStringProperty(properties, VERSION_HISTORY_AUTO_COMMIT_MSG, "");
        String remoteRepositoryUrl = getStringProperty(properties, VERSION_HISTORY_REMOTE_REPO_URL, "");
        String branchName = getStringProperty(properties, VERSION_HISTORY_REMOTE_BRANCH, "");
        String sshPrivateKey = getStringProperty(properties, VERSION_HISTORY_REMOTE_SSH_KEY, "");

        // Create GitSettings
        gitSettings = new GitSettings(remoteRepositoryUrl, branchName, sshPrivateKey);
    }

    public boolean isEnableAutoCommit() {
        return enableAutoCommit;
    }

    public void setEnableAutoCommit(boolean enableAutoCommit) {
        this.enableAutoCommit = enableAutoCommit;
    }

    public boolean isEnableVersionHistory() {
        return enableVersionHistory;
    }

    public void setEnableVersionHistory(boolean enableVersionHistory) {
        this.enableVersionHistory = enableVersionHistory;
    }

    public GitSettings getGitSettings() {
        return gitSettings;
    }

    public boolean isEnableSyncDelete() {
        return enableSyncDelete;
    }

    public void setEnableSyncDelete(boolean enableSyncDelete) {
        this.enableSyncDelete = enableSyncDelete;
    }

    public boolean isEnableAutoCommitPrompt() {
        return enableAutoCommitPrompt;
    }

    public void setEnableAutoCommitPrompt(boolean enableAutoCommitPrompt) {
        this.enableAutoCommitPrompt = enableAutoCommitPrompt;
    }

    public String getAutoCommitMsg() {
        return autoCommitMsg;
    }

    public void setAutoCommitMsg(String autoCommitMsg) {
        this.autoCommitMsg = autoCommitMsg;
    }

    public void setGitSettings(GitSettings gitSettings) {
        this.gitSettings = gitSettings;
    }

    /**
     * Helper: Get boolean property with default
     */
    private boolean getBooleanProperty(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Helper: Get string property with default
     */
    private String getStringProperty(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}
