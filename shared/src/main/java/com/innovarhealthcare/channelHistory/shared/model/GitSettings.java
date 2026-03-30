package com.innovarhealthcare.channelHistory.shared.model;

import org.apache.commons.lang3.StringUtils;

public class GitSettings {
    private String remoteRepositoryUrl;
    private String branchName;
    private TransportType transportType;
    private String sshPrivateKey;
    private String httpsUsername;
    private String httpsPersonalAccessToken;

    public GitSettings(String remoteRepositoryUrl, String branchName, String sshPrivateKey) {
        this(remoteRepositoryUrl, branchName, TransportType.SSH, sshPrivateKey, "", "");
    }

    public GitSettings(String remoteRepositoryUrl, String branchName, TransportType transportType,
                       String sshPrivateKey, String httpsUsername, String httpsPersonalAccessToken) {
        this.remoteRepositoryUrl = remoteRepositoryUrl != null ? remoteRepositoryUrl : "";
        this.branchName = branchName != null ? branchName : "";
        this.transportType = transportType != null ? transportType : TransportType.SSH;
        this.sshPrivateKey = sshPrivateKey != null ? sshPrivateKey : "";
        this.httpsUsername = httpsUsername != null ? httpsUsername : "";
        this.httpsPersonalAccessToken = httpsPersonalAccessToken != null ? httpsPersonalAccessToken : "";
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

    public TransportType getTransportType() {
        return transportType;
    }

    public void setTransportType(TransportType transportType) {
        this.transportType = transportType;
    }

    public String getSshPrivateKey() {
        return sshPrivateKey;
    }

    public void setSshPrivateKey(String sshPrivateKey) {
        this.sshPrivateKey = sshPrivateKey;
    }

    public String getHttpsUsername() {
        return httpsUsername;
    }

    public void setHttpsUsername(String httpsUsername) {
        this.httpsUsername = httpsUsername;
    }

    public String getHttpsPersonalAccessToken() {
        return httpsPersonalAccessToken;
    }

    public void setHttpsPersonalAccessToken(String httpsPersonalAccessToken) {
        this.httpsPersonalAccessToken = httpsPersonalAccessToken;
    }

    public boolean validate() {
        if (StringUtils.isBlank(remoteRepositoryUrl) || StringUtils.isBlank(branchName)) {
            return false;
        }

        if (transportType == TransportType.HTTPS) {
            return !StringUtils.isBlank(httpsUsername) && !StringUtils.isBlank(httpsPersonalAccessToken);
        } else {
            return !StringUtils.isBlank(sshPrivateKey);
        }
    }
}
