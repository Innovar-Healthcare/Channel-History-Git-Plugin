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
    public static final String VERSION_HISTORY_TRANSPORT_TYPE = "versionHistory.remote.transportType";
    public static final String VERSION_HISTORY_HTTPS_USERNAME = "versionHistory.remote.https.username";
    public static final String VERSION_HISTORY_HTTPS_PAT = "versionHistory.remote.https.pat";

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
        gitSettings = new GitSettings("", "", "");
    }

    public VersionHistoryProperties(Properties properties) {
        fromProperties(properties);
    }

    public Properties toProperties() {
        Properties properties = new Properties();

        properties.setProperty(VERSION_HISTORY_ENABLE, String.valueOf(enableVersionHistory));
        properties.setProperty(VERSION_HISTORY_AUTO_COMMIT_ENABLE, String.valueOf(enableAutoCommit));
        properties.setProperty(VERSION_HISTORY_AUTO_COMMIT_PROMPT, String.valueOf(enableAutoCommitPrompt));
        properties.setProperty(VERSION_HISTORY_AUTO_COMMIT_MSG, autoCommitMsg);
        properties.setProperty(VERSION_HISTORY_SYNC_DELETE, String.valueOf(enableSyncDelete));

        properties.setProperty(VERSION_HISTORY_REMOTE_REPO_URL, gitSettings.getRemoteRepositoryUrl());
        properties.setProperty(VERSION_HISTORY_REMOTE_BRANCH, gitSettings.getBranchName());
        properties.setProperty(VERSION_HISTORY_REMOTE_SSH_KEY, gitSettings.getSshPrivateKey());
        properties.setProperty(VERSION_HISTORY_TRANSPORT_TYPE, gitSettings.getTransportType().name());
        properties.setProperty(VERSION_HISTORY_HTTPS_USERNAME, gitSettings.getHttpsUsername());
        properties.setProperty(VERSION_HISTORY_HTTPS_PAT, gitSettings.getHttpsPersonalAccessToken());

        return properties;
    }

    public void fromProperties(Properties properties) {
        enableVersionHistory = false;
        if (properties.getProperty(VERSION_HISTORY_ENABLE) != null && !properties.getProperty(VERSION_HISTORY_ENABLE).equals("")) {
            enableVersionHistory = Boolean.parseBoolean(properties.getProperty(VERSION_HISTORY_ENABLE));
        }

        enableAutoCommit = false;
        if (properties.getProperty(VERSION_HISTORY_AUTO_COMMIT_ENABLE) != null && !properties.getProperty(VERSION_HISTORY_AUTO_COMMIT_ENABLE).equals("")) {
            enableAutoCommit = Boolean.parseBoolean(properties.getProperty(VERSION_HISTORY_AUTO_COMMIT_ENABLE));
        }

        enableAutoCommitPrompt = false;
        if (properties.getProperty(VERSION_HISTORY_AUTO_COMMIT_PROMPT) != null && !properties.getProperty(VERSION_HISTORY_AUTO_COMMIT_PROMPT).equals("")) {
            enableAutoCommitPrompt = Boolean.parseBoolean(properties.getProperty(VERSION_HISTORY_AUTO_COMMIT_PROMPT));
        }

        enableSyncDelete = false;
        if (properties.getProperty(VERSION_HISTORY_SYNC_DELETE) != null && !properties.getProperty(VERSION_HISTORY_SYNC_DELETE).equals("")) {
            enableSyncDelete = Boolean.parseBoolean(properties.getProperty(VERSION_HISTORY_SYNC_DELETE));
        }

        autoCommitMsg = "";
        if (properties.getProperty(VERSION_HISTORY_AUTO_COMMIT_MSG) != null && !properties.getProperty(VERSION_HISTORY_AUTO_COMMIT_MSG).equals("")) {
            autoCommitMsg = properties.getProperty(VERSION_HISTORY_AUTO_COMMIT_MSG);
        }

        String remoteRepositoryUrl = "";
        if (properties.getProperty(VERSION_HISTORY_REMOTE_REPO_URL) != null && !properties.getProperty(VERSION_HISTORY_REMOTE_REPO_URL).equals("")) {
            remoteRepositoryUrl = properties.getProperty(VERSION_HISTORY_REMOTE_REPO_URL);
        }

        String branchName = "";
        if (properties.getProperty(VERSION_HISTORY_REMOTE_BRANCH) != null && !properties.getProperty(VERSION_HISTORY_REMOTE_BRANCH).equals("")) {
            branchName = properties.getProperty(VERSION_HISTORY_REMOTE_BRANCH);
        }

        String sshPrivateKey = "";
        if (properties.getProperty(VERSION_HISTORY_REMOTE_SSH_KEY) != null && !properties.getProperty(VERSION_HISTORY_REMOTE_SSH_KEY).equals("")) {
            sshPrivateKey = properties.getProperty(VERSION_HISTORY_REMOTE_SSH_KEY);
        }

        TransportType transportType = TransportType.SSH;
        if (properties.getProperty(VERSION_HISTORY_TRANSPORT_TYPE) != null && !properties.getProperty(VERSION_HISTORY_TRANSPORT_TYPE).equals("")) {
            transportType = TransportType.fromString(properties.getProperty(VERSION_HISTORY_TRANSPORT_TYPE));
        }

        String httpsUsername = "";
        if (properties.getProperty(VERSION_HISTORY_HTTPS_USERNAME) != null && !properties.getProperty(VERSION_HISTORY_HTTPS_USERNAME).equals("")) {
            httpsUsername = properties.getProperty(VERSION_HISTORY_HTTPS_USERNAME);
        }

        String httpsPersonalAccessToken = "";
        if (properties.getProperty(VERSION_HISTORY_HTTPS_PAT) != null && !properties.getProperty(VERSION_HISTORY_HTTPS_PAT).equals("")) {
            httpsPersonalAccessToken = properties.getProperty(VERSION_HISTORY_HTTPS_PAT);
        }

        gitSettings = new GitSettings(remoteRepositoryUrl, branchName, transportType, sshPrivateKey, httpsUsername, httpsPersonalAccessToken);
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
}
