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

    public final ObjectXMLSerializer serializer;
    public final String serverId;
    public final File dir;

    private boolean enable;
    private boolean autoCommit;
    private String remoteRepoUrl;
    private String remoteRepoBranch;
    private byte[] sshKeyBytes = new byte[0];
    private SshSessionFactory sshSessionFactory;

    private final ChannelService channelService;
    private final CodeTemplateService codeTemplateService;

    public GitRepositoryService() {
        serializer = ObjectXMLSerializer.getInstance();
        serverId = Donkey.getInstance().getConfiguration().getServerId();
        dir = new File(Donkey.getInstance().getConfiguration().getAppData(), DATA_DIR);

        channelService = new ChannelService(this);
        codeTemplateService = new CodeTemplateService(this);
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public boolean isEnable() {
        return enable;
    }

    public void applySettings(Properties properties) throws Exception {
        enable = Boolean.parseBoolean(properties.getProperty(VersionControlConstants.VERSION_HISTORY_ENABLE));
        if (!enable) {
            // close service
            close();

            return;
        }

        autoCommit = Boolean.parseBoolean(properties.getProperty(VersionControlConstants.VERSION_HISTORY_AUTO_COMMIT_ENABLE));

        sshKeyBytes = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_SSH_KEY).getBytes(CHARSET_UTF_8);
        remoteRepoUrl = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_REPO_URL);
        remoteRepoBranch = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_BRANCH);

        // init ssh session
        sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);
                defaultJSch.addIdentity("mirthVersionHistoryKey", sshKeyBytes, (byte[]) null, (byte[]) null);
                return defaultJSch;
            }
        };

        // init git repo
        initGitRepo();
    }

    public String validateSettings(Properties properties) throws Exception {
        byte[] ssh = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_SSH_KEY).getBytes(CHARSET_UTF_8);
        String url = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_REPO_URL);
        String branch = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_BRANCH);
        SshSessionFactory sshSession = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);
                defaultJSch.addIdentity("mirthVersionHistoryKey", ssh, (byte[]) null, (byte[]) null);
                return defaultJSch;
            }
        };

        String tempPath = DATA_DIR + "-temp-" + System.currentTimeMillis();
        File tempDir = new File(Donkey.getInstance().getConfiguration().getAppData(), tempPath);

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

        String ret = "";
        try {
            cloneCommand.call();
            ret = "Successfully connected to the remote repository. Remember to save your changes.";
        } catch (Exception e) {
            ret = "Failed to connect to the remote repository";
        } finally {
            FileUtils.delete(tempDir, 13);
        }

        return ret;
    }

    public void initGitRepo() throws Exception {
        if (dir.exists()) {
            //if repo folder exist on local, do a pull to get latest commit
            pullLatestRepo();
        } else {
            //if repo folder does not exist, do a clone to build the repo
            cloneRepo();
        }

        git = Git.init().setDirectory(dir).call();
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

    private void pullLatestRepo() throws Exception {
        if (Files.exists(new File(dir, ".git").toPath())) {
            Git git = Git.open(new File(dir, ".git"));
            if (git.getRepository().getBranch().equals(remoteRepoBranch)) {
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
            } else {
                FileUtils.delete(dir, 13);
                cloneRepo();
            }
        } else {
            throw new Exception("The folder " + dir + " is not a Git repo");
        }
    }

    private void cloneRepo() throws Exception {
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
        cloneCommand.call();
    }

    private void close() {
        if (git != null) {
            git.close();

            git = null;
        }

        if (sshSessionFactory != null) {
            sshSessionFactory = null;
        }
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
}
