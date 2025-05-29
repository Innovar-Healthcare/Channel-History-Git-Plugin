package com.innovarhealthcare.channelHistory.server.service;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;

import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.innovarhealthcare.channelHistory.shared.util.ResponseUtil;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.User;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.List;
import java.util.Objects;
import java.util.Iterator;


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
    VersionHistoryProperties versionHistoryProperties;

    public GitRepositoryService() {
        versionHistoryProperties = new VersionHistoryProperties();
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

    public String getRemoteRepoUrl() {
        return remoteRepoUrl;
    }

    public String getRemoteRepoBranch() {
        return remoteRepoBranch;
    }

    public SshSessionFactory getSshSessionFactory() {
        return sshSessionFactory;
    }

    public VersionHistoryProperties getVersionHistoryProperties() {
        return versionHistoryProperties;
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
        VersionHistoryProperties props = new VersionHistoryProperties(properties);

        byte[] ssh = props.getGitSettings().getSshPrivateKey().getBytes(CHARSET_UTF_8);
        String url = props.getGitSettings().getRemoteRepositoryUrl();
        String branch = props.getGitSettings().getBranchName();

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

        return channelService.commitAndPush(channel, message, committer, true);
    }

    public String removeChannel(Channel channel, String message, User user) {
        PersonIdent committer = getCommitter(user); // get committer

        return channelService.remove(channel, message, committer, true);
    }

    public List<String> loadCodeTemplateOnRepo() throws Exception {
        return codeTemplateService.load();
    }

    public String commitAndPushCodeTemplate(CodeTemplate template, String message, User user) {
        PersonIdent committer = getCommitter(user); // get committer

        return codeTemplateService.commitAndPush(template, message, committer, true);
    }

    public String removeCodeTemplate(CodeTemplate template, String message, User user) {
        PersonIdent committer = getCommitter(user); // get committer

        return codeTemplateService.remove(template, message, committer, true);
    }

    /**
     * Synchronizes the local repository with the remote by fetching and resetting if remote changes exist.
     *
     * @return ResponseUtil instance containing the operation result
     */
    public ResponseUtil resetToRemote() {
        String branch = getRemoteRepoBranch();
        StringBuilder operationDetails = new StringBuilder();
        ResponseUtil responseUtil = new ResponseUtil();

        if (branch == null || branch.trim().isEmpty()) {
            return responseUtil.fail(operationDetails, "Branch cannot be empty.");
        }

        if (remoteRepoUrl == null || remoteRepoUrl.trim().isEmpty()) {
            return responseUtil.fail(operationDetails, "Remote repository URL cannot be empty.");
        }

        if (sshSessionFactory == null) {
            return responseUtil.fail(operationDetails, "SSH session factory cannot be null.");
        }

        try {
            // Verify current branch
            String currentBranch = git.getRepository().getBranch();
            if (!branch.equals(currentBranch)) {
                operationDetails.append("Current branch is ").append(currentBranch).append(", expected ").append(branch).append(System.lineSeparator());
                return responseUtil.fail(operationDetails, "Current branch is " + currentBranch + ", expected " + branch);
            }

            // Check repository state
            if (git.getRepository().resolve("HEAD") == null) {
                operationDetails.append("No commits in repository, cannot pull or push.").append(System.lineSeparator());
                return responseUtil.fail(operationDetails, "No commits in repository, cannot pull or push.");
            }

            // Check for remote changes
            operationDetails.append("Remote Check Result:").append(System.lineSeparator());
            boolean remoteHasChanges = hasRemoteRepoChanges();
            operationDetails.append("  Remote Changes: ").append(remoteHasChanges ? "Detected" : "None").append(System.lineSeparator());

            if (remoteHasChanges) {
                // Check for local changes (to warn if discarded)
                Status status = git.status().call();
                if (!status.getModified().isEmpty() || !status.getUncommittedChanges().isEmpty() || !status.getUntracked().isEmpty()) {
                    operationDetails.append("Warning: Local changes will be discarded due to overwrite pull:").append(System.lineSeparator());
                    operationDetails.append("  Modified: ").append(status.getModified()).append(System.lineSeparator());
                    operationDetails.append("  Uncommitted: ").append(status.getUncommittedChanges()).append(System.lineSeparator());
                    operationDetails.append("  Untracked: ").append(status.getUntracked()).append(System.lineSeparator());
                }

                // Fetch and reset
                operationDetails.append("Pull Overwrite Result:").append(System.lineSeparator());
                FetchCommand fetchCommand = git.fetch();
                fetchCommand.setRemote("origin");
                fetchCommand.setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/remotes/origin/" + branch));
                fetchCommand.setTransportConfigCallback(transport -> {
                    if (transport instanceof SshTransport) {
                        SshTransport sshTransport = (SshTransport) transport;
                        sshTransport.setSshSessionFactory(sshSessionFactory);
                    }
                });
                FetchResult fetchResult = fetchCommand.call();
                operationDetails.append("  Fetch: ").append(fetchResult.getMessages()).append(System.lineSeparator());

                Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
                if (remoteRef == null) {
                    operationDetails.append("Failed: Remote branch origin/").append(branch).append(" not found.").append(System.lineSeparator());
                    return responseUtil.fail(operationDetails, "Remote branch origin/" + branch + " not found.");
                }
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remoteRef.getName()).call();
                operationDetails.append("  Reset: Local branch reset to origin/").append(branch).append(System.lineSeparator());
            } else {
                operationDetails.append("  Skipped: No pull needed, local and remote branches are in sync").append(System.lineSeparator());
            }

            return responseUtil.success(operationDetails, "Repository synchronized with remote successfully!");
        } catch (GitAPIException e) {
            operationDetails.append("Git error: ").append(e.getMessage()).append(System.lineSeparator());
            return responseUtil.fail(operationDetails, "Failed to synchronize repository: Git error occurred.");
        } catch (IOException e) {
            operationDetails.append("IO error: ").append(e.getMessage()).append(System.lineSeparator());
            return responseUtil.fail(operationDetails, "Failed to synchronize repository: IO error occurred.");
        } catch (Exception e) {
            operationDetails.append("Unexpected error: ").append(e.getMessage()).append(System.lineSeparator());
            return responseUtil.fail(operationDetails, "Failed to synchronize repository: Unexpected error occurred.");
        }
    }

    /**
     * Stages specified files, commits with the given message, and pushes to the remote repository.
     * Synchronizes with the remote using hasRemoteRepoChanges and reset if needed.
     *
     * @param filesToStage   List of file patterns to stage (e.g., "channels/abc123" for add, "templates/def456" for rm)
     * @param commitMessage  The commit message
     * @param committer      The PersonIdent for the commit author
     * @param allowForcePush If true, force pushes to overwrite the remote branch
     * @param isDeletion     If true, stages files as deletions (git rm); if false, stages as additions (git add)
     * @return ResponseUtil instance containing the operation result
     */
    protected ResponseUtil stageCommitAndPush(List<String> filesToStage, String commitMessage, PersonIdent committer, boolean allowForcePush, boolean isDeletion) {
        StringBuilder operationDetails = new StringBuilder();
        ResponseUtil responseUtil = new ResponseUtil();
        String branch = getRemoteRepoBranch();

        // Validate inputs
        if (filesToStage == null || filesToStage.isEmpty()) {
            return responseUtil.fail(operationDetails, "No files to stage for commit and push.");
        }
        if (commitMessage == null) {
            commitMessage = "";
        }
        if (committer == null) {
            return responseUtil.fail(operationDetails, "Committer cannot be null.");
        }
        if (remoteRepoUrl == null || remoteRepoUrl.trim().isEmpty()) {
            return responseUtil.fail(operationDetails, "Remote repository URL cannot be empty.");
        }
        if (branch == null || branch.trim().isEmpty()) {
            return responseUtil.fail(operationDetails, "Branch cannot be empty.");
        }
        if (sshSessionFactory == null) {
            return responseUtil.fail(operationDetails, "SSH session factory cannot be null.");
        }

        try {
            // Stage files
            operationDetails.append("Stage Result:").append(System.lineSeparator());
            if (isDeletion) {
                RmCommand rmCommand = git.rm();
                for (String file : filesToStage) {
                    rmCommand.addFilepattern(file);
                    operationDetails.append("  Staged deletion: ").append(file).append(System.lineSeparator());
                }
                rmCommand.call();
            } else {
                AddCommand addCommand = git.add();
                for (String file : filesToStage) {
                    addCommand.addFilepattern(file);
                    operationDetails.append("  Staged addition: ").append(file).append(System.lineSeparator());
                }
                addCommand.call();
            }

            // Commit changes
            operationDetails.append("Commit Result:").append(System.lineSeparator());
            RevCommit rc = git.commit().setCommitter(committer).setMessage(commitMessage).call();
            operationDetails.append("  Commit: Committed with message: ").append(commitMessage).append(System.lineSeparator());

            // Ensure remote configuration is correct
            StoredConfig config = git.getRepository().getConfig();
            String configuredUrl = config.getString("remote", "origin", "url");
            if (!remoteRepoUrl.equals(configuredUrl)) {
                RemoteAddCommand remoteAddCommand = git.remoteAdd();
                remoteAddCommand.setName("origin");
                remoteAddCommand.setUri(new URIish(remoteRepoUrl));
                remoteAddCommand.call();
                config.setString("remote", "origin", "url", remoteRepoUrl);
                config.save();
            }

            // Push to remote repository
            operationDetails.append("Push Result:").append(System.lineSeparator());
            PushCommand pushCommand = git.push();
            pushCommand.setRemote("origin");
            pushCommand.setRefSpecs(new RefSpec("refs/heads/" + branch));
            pushCommand.setForce(allowForcePush);
            pushCommand.setTransportConfigCallback(transport -> {
                if (transport instanceof SshTransport) {
                    ((SshTransport) transport).setSshSessionFactory(sshSessionFactory);
                }
            });

            Iterable<PushResult> pushResults = pushCommand.call();
            Iterator<PushResult> iterator = pushResults.iterator();
            if (!iterator.hasNext()) {
                operationDetails.append("No push results returned.").append(System.lineSeparator());
                return responseUtil.fail(operationDetails, "No push results returned.");
            }

            PushResult pushResult = iterator.next();
            operationDetails.append("  Remote: ").append(pushResult.getURI()).append(System.lineSeparator());

            boolean pushSuccessful = false;
            for (RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
                operationDetails.append("  Ref: ").append(update.getRemoteName())
                        .append(", Status: ").append(update.getStatus())
                        .append(", New ObjectId: ").append(update.getNewObjectId() != null ? update.getNewObjectId().name() : "none")
                        .append(System.lineSeparator());

                if (update.getStatus() == RemoteRefUpdate.Status.OK) {
                    operationDetails.append("    Success: ").append(allowForcePush ? "Force push" : "Push").append(" completed successfully").append(System.lineSeparator());
                    pushSuccessful = true;
                } else if (update.getStatus() == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                    operationDetails.append("    Failed: Non-fast-forward update, possibly due to new remote changes").append(System.lineSeparator());
                } else if (update.getStatus() == RemoteRefUpdate.Status.REJECTED_OTHER_REASON) {
                    operationDetails.append("    Failed: ").append(update.getMessage()).append(System.lineSeparator());
                } else {
                    operationDetails.append("    Status: ").append(update.getStatus()).append(System.lineSeparator());
                }
            }

            String messages = pushResult.getMessages();
            if (messages != null && !messages.isEmpty()) {
                operationDetails.append("  Messages: ").append(messages).append(System.lineSeparator());
            }

            if (iterator.hasNext()) {
                logger.warn("Additional PushResult objects found but ignored for commit and push");
            }

            if (!pushSuccessful) {
                operationDetails.append("Push failed, commit not applied to remote.").append(System.lineSeparator());
                return responseUtil.fail(operationDetails, "Push failed, commit not applied to remote.");
            }

            return responseUtil.success(operationDetails, "Changes committed and pushed successfully!");
        } catch (GitAPIException e) {
            operationDetails.append("Git error: ").append(e.getMessage()).append(System.lineSeparator());
            return responseUtil.fail(operationDetails, "Failed to commit and push: Git error occurred.");
        } catch (IOException e) {
            operationDetails.append("IO error: ").append(e.getMessage()).append(System.lineSeparator());
            return responseUtil.fail(operationDetails, "Failed to commit and push: IO error occurred.");
        } catch (Exception e) {
            operationDetails.append("Unexpected error: ").append(e.getMessage()).append(System.lineSeparator());
            return responseUtil.fail(operationDetails, "Failed to commit and push: Unexpected error occurred.");
        }
    }

    /**
     * Checks if the remote branch's HEAD differs from the local branch's HEAD.
     * Fetches the remote branch and compares commit trees.
     *
     * @return true if the remote branch has changes not in the local branch, false otherwise
     * @throws GitAPIException If Git operations fail
     * @throws IOException     If repository operations fail
     */
    protected boolean hasRemoteRepoChanges() throws GitAPIException, IOException {
        String branch = getRemoteRepoBranch(); // Assume provided

        // Fetch remote branch
        FetchCommand fetchCommand = git.fetch();
        fetchCommand.setRemote("origin");
        fetchCommand.setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/remotes/origin/" + branch));
        fetchCommand.setTransportConfigCallback(transport -> {
            if (transport instanceof SshTransport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        });
        FetchResult fetchResult = fetchCommand.call();

        // Get remote and local branch refs
        Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
        Ref localRef = git.getRepository().findRef("refs/heads/" + branch);

        if (remoteRef == null || localRef == null) {
            // Remote or local branch missing; assume changes to be safe
            return true;
        }

        ObjectId remoteCommitId = remoteRef.getObjectId();
        ObjectId localCommitId = localRef.getObjectId();

        if (remoteCommitId == null || localCommitId == null) {
            return true;
        }

        // Compare commit trees
        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            RevCommit remoteCommit = revWalk.parseCommit(remoteCommitId);
            RevCommit localCommit = revWalk.parseCommit(localCommitId);

            // If commits are the same, no changes
            if (remoteCommitId.equals(localCommitId)) {
                return false;
            }

            // Compare trees for differences
            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.addTree(remoteCommit.getTree());
                treeWalk.addTree(localCommit.getTree());
                treeWalk.setRecursive(true);
                treeWalk.setFilter(TreeFilter.ANY_DIFF);
                return treeWalk.next(); // True if any differences exist
            }
        }
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
        versionHistoryProperties.fromProperties(properties);

        enable = versionHistoryProperties.isEnableVersionHistory();
        autoCommit = versionHistoryProperties.isEnableAutoCommit();

        sshKeyBytes = versionHistoryProperties.getGitSettings().getSshPrivateKey().getBytes(CHARSET_UTF_8);
        remoteRepoUrl = versionHistoryProperties.getGitSettings().getRemoteRepositoryUrl();
        remoteRepoBranch = versionHistoryProperties.getGitSettings().getBranchName();
    }
}
