package com.innovarhealthcare.channelHistory.server.service;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.User;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-12-06 9:25 AM
 */

public class GitRepositoryService {
    private static final Logger logger = LoggerFactory.getLogger(GitRepositoryService.class);
    public static final String DATA_DIR = "InnovarHealthcare-version-control";
    public static final Charset CHARSET_UTF_8 = StandardCharsets.UTF_8;

    public Git git;

    public ObjectXMLSerializer serializer;
    public String serverId;
    public File dir;

    private boolean isGitConnected;
    private boolean enable;
    private boolean autoCommit;
    private String remoteRepoUrl;
    private String remoteRepoBranch;
    private byte[] sshKeyBytes = new byte[0];
    private SshSessionFactory sshSessionFactory;

    private ChannelService channelService;
    private CodeTemplateService codeTemplateService;

    public GitRepositoryService() {
    }

    public void init(Properties properties) {
        parseProperties(properties);
    }

    public void startGit() throws Exception {
        isGitConnected = false;

        channelService = new ChannelService(this);
        codeTemplateService = new CodeTemplateService(this);

        serializer = ObjectXMLSerializer.getInstance();
        serverId = Donkey.getInstance().getConfiguration().getServerId();
        dir = new File(Donkey.getInstance().getConfiguration().getAppData(), DATA_DIR);

        if (enable) {
            if (validateGitConnected(remoteRepoUrl, remoteRepoBranch, sshKeyBytes) == null) {
                initGitRepo(false);
            }
        }
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public boolean isEnable() {
        return enable;
    }

    public boolean isGitConnected() {
        return isGitConnected;
    }

    public void applySettings(Properties properties) throws Exception {
        parseProperties(properties);

        // close current git connected;
        closeGit();

        if (enable) {
            if (validateGitConnected(remoteRepoUrl, remoteRepoBranch, sshKeyBytes) == null) {
                initGitRepo(true);
            }
        }
    }

    public String validateSettings(Properties properties) throws Exception {
        byte[] ssh = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_SSH_KEY).getBytes(CHARSET_UTF_8);
        String url = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_REPO_URL);
        String branch = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_BRANCH);
        String ret = validateGitConnected(url, branch, ssh);
        if (ret == null) {
            return "Successfully connected to the remote repository. Remember to save your changes.";
        }

        return ret;
    }

    private String validateGitConnected(String url, String branch, byte[] ssh) {
        SshSessionFactory sshSession = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);
                defaultJSch.addIdentity("mirthVersionHistoryKey", ssh, null, null);
                return defaultJSch;
            }
        };

        File tempDir;
        try {
            tempDir = FileUtils.createTempDir("version_history_", "", new File(Donkey.getInstance().getConfiguration().getAppData(), "temp"));
        } catch (Exception e) {
            logger.warn("Failed to create temp directory. Error: " + e);
            return "Failed to create temp directory. Error: " + e;
        }

        CloneCommand cloneCommand = Git.cloneRepository();
        cloneCommand.setURI(url);
        cloneCommand.setDirectory(tempDir);
        cloneCommand.setBranch(branch);
        cloneCommand.setTransportConfigCallback(new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSession);
            }
        });
        cloneCommand.setNoCheckout(true);

        String ret = null;
        try {
            Git git = cloneCommand.call();
            git.close();
        } catch (Exception e) {
            ret = "Failed to connect to the remote repository. Error: " + e;
        }

        try {
            FileUtils.delete(tempDir, 13);
        } catch (Exception e) {
            logger.warn("Failed to remove temp directory. Error: " + e);
        }

        return ret;
    }

    public void initGitRepo(boolean force) throws Exception {
        try {
            // init ssh session
            sshSessionFactory = new JschConfigSessionFactory() {
                @Override
                protected void configure(OpenSshConfig.Host host, Session session) {
                    session.setConfig("StrictHostKeyChecking", "no");
                }

                @Override
                protected JSch createDefaultJSch(FS fs) throws JSchException {
                    JSch defaultJSch = super.createDefaultJSch(fs);
                    defaultJSch.addIdentity("mirthVersionHistoryKey", sshKeyBytes, null, null);
                    return defaultJSch;
                }
            };

            // init repo directory
            dir = new File(Donkey.getInstance().getConfiguration().getAppData(), DATA_DIR);

            if (!force) {
                try {
                    git = Git.open(new File(dir, ".git"));
                } catch (IOException ignored) {
                }
            }

            if (git != null) {
                pullRepo(git);
            } else {
                if (dir.exists()) {
                    FileUtils.delete(dir, 13);
                }
                git = cloneRepo();
            }

            isGitConnected = true;
        } catch (Exception e) {
            isGitConnected = false;
        }
    }

    public List<String> getHistory(String fileName, String mode) throws Exception {
        if (Objects.equals(mode, VersionControlConstants.MODE_CHANNEL)) {
            return channelService.getHistory(fileName);
        }

        if (Objects.equals(mode, VersionControlConstants.MODE_CODE_TEMPLATE)) {
            return codeTemplateService.getHistory(fileName);
        }

        throw new Exception("Mode (" + mode + ")" + "is not supported");
    }

    public String getContent(String fileName, String revision, String mode) throws Exception {
        if (Objects.equals(mode, VersionControlConstants.MODE_CHANNEL)) {
            return channelService.getContent(fileName, revision);
        }

        if (Objects.equals(mode, VersionControlConstants.MODE_CODE_TEMPLATE)) {
            return codeTemplateService.getContent(fileName, revision);
        }

        throw new Exception("mode (" + mode + ")" + "is not supported");
    }

    public List<String> loadChannelOnRepo() throws Exception {
        return channelService.load();
    }

    public String commitAndPushChannel(Channel channel, String message, User user) {
        PersonIdent committer = getCommitter(user); // get committer

        return channelService.commitAndPush(channel, message, committer);
    }

    public List<String> loadCodeTemplateOnRepo() throws Exception {
        return codeTemplateService.load();
    }

    public String commitAndPushCodeTemplate(CodeTemplate template, String message, User user) {
        PersonIdent committer = getCommitter(user); // get committer

        return codeTemplateService.commitAndPush(template, message, committer);
    }

    public void pushToRemoteRepo() throws Exception {
        // add remote repo:
        RemoteAddCommand remoteAddCommand = git.remoteAdd();
        remoteAddCommand.setName("origin");
        remoteAddCommand.setUri(new URIish(remoteRepoUrl));
        PushCommand pushCommand = git.push();
        pushCommand.setTransportConfigCallback(new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        });

        // you can add more settings here if needed
        remoteAddCommand.call();
        pushCommand.call();
    }

    private void pullRepo(Git git) throws Exception {
        PullCommand pullCommand = git.pull();
        pullCommand.setRemote("origin");
        pullCommand.setRemoteBranchName(remoteRepoBranch);
        pullCommand.setTransportConfigCallback(new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        });
        pullCommand.call();
    }

    private Git cloneRepo() throws Exception {
        CloneCommand cloneCommand = Git.cloneRepository();
        cloneCommand.setURI(remoteRepoUrl);
        cloneCommand.setDirectory(dir);
        cloneCommand.setBranch(remoteRepoBranch);
        cloneCommand.setTransportConfigCallback(new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        });

        return cloneCommand.call();
    }

    private void closeGit() {
        if (git != null) {
            git.close();

            git = null;
        }

        if (sshSessionFactory != null) {
            sshSessionFactory = null;
        }

        isGitConnected = false;
    }

    private PersonIdent getCommitter(User user) {
        if (user == null) {
            throw new RuntimeException("User is null");
        }

        try {
            String username = user.getUsername();
            String email = user.getEmail();
            if (email == null) {
                email = username + "@" + "local";
            }

            return new PersonIdent(username, email, System.currentTimeMillis(), 0); // UTC
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void parseProperties(Properties properties) {
        enable = Boolean.parseBoolean(properties.getProperty(VersionControlConstants.VERSION_HISTORY_ENABLE));
        autoCommit = Boolean.parseBoolean(properties.getProperty(VersionControlConstants.VERSION_HISTORY_AUTO_COMMIT_ENABLE));

        sshKeyBytes = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_SSH_KEY).getBytes(CHARSET_UTF_8);
        remoteRepoUrl = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_REPO_URL);
        remoteRepoBranch = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_BRANCH);
    }
}
