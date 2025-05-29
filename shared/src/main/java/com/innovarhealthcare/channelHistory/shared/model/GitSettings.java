package com.innovarhealthcare.channelHistory.shared.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class GitSettings {
    private String remoteRepositoryUrl;
    private String branchName;
    private String sshPrivateKey;

    public GitSettings(String remoteRepositoryUrl, String branchName, String sshPrivateKey) {
        this.remoteRepositoryUrl = remoteRepositoryUrl != null ? remoteRepositoryUrl : "";
        this.branchName = branchName != null ? branchName : "";
        this.sshPrivateKey = sshPrivateKey != null ? sshPrivateKey : "";
    }

    public String getRemoteRepositoryUrl() {
        return remoteRepositoryUrl;
    }

    public void setRemoteRepositoryUrl(String remoteRepositoryUrl) {
        this.remoteRepositoryUrl = remoteRepositoryUrl;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getSshPrivateKey() {
        return sshPrivateKey;
    }

    public void setSshPrivateKey(String sshPrivateKey) {
        this.sshPrivateKey = sshPrivateKey;
    }

    public boolean validate() {
        return !StringUtils.isBlank(remoteRepositoryUrl) &&
                !StringUtils.isBlank(branchName) &&
                !StringUtils.isBlank(sshPrivateKey);
    }
}
