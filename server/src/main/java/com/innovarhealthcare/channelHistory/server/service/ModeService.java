package com.innovarhealthcare.channelHistory.server.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.util.CommitMessageUtil;
import com.innovarhealthcare.channelHistory.shared.util.ResponseUtil;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract service for managing versioned objects in a Git repository.
 *
 * @param <T> The type of object (Channel, CodeTemplate, etc.)
 */
public abstract class ModeService<T> {
    private static final Logger logger = LoggerFactory.getLogger(ModeService.class);
    private static final ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
    protected final GitRepositoryService gitService;

    public ModeService(GitRepositoryService gitService) {
        this.gitService = gitService;
    }

    // ========== Abstract methods for subclasses ==========

    /**
     * Get directory path for this mode (e.g., "channels", "codetemplates")
     */
    public abstract String getDirectory();

    /**
     * Get human-readable type name (e.g., "Channel", "Code Template")
     */
    protected abstract String getTypeName();

    /**
     * Deserialize and verify content
     *
     * @param content  XML content
     * @param filePath File path for logging
     * @return Deserialized object or null if invalid
     */
    protected abstract T deserializeAndVerify(String content, String filePath);

    /**
     * Extract ID from object
     */
    protected abstract String extractId(T object);

    /**
     * Extract name from object
     */
    protected abstract String extractName(T object);

    /**
     * Hook for subclasses to perform actions after committing.
     *
     * @param id       The ID of the committed object
     * @param commitId The commit ID
     */
    protected void postCommit(String id, String commitId) {
        // Default implementation does nothing
    }

    // ========== Shared implementation ==========

    /**
     * Load metadata list from repository
     */
    public List<RepoItemMetadata> loadMetadata() throws Exception {
        List<RepoItemMetadata> lst = new ArrayList<>();
        Git git = this.gitService.git;
        Repository repo = this.gitService.git.getRepository();
        String path = getDirectory() + "/";

        ObjectId lastCommitId = repo.resolve(Constants.HEAD);
        RevWalk revWalk = new RevWalk(repo);
        RevCommit commit = revWalk.parseCommit(lastCommitId);
        RevTree tree = commit.getTree();

        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(false);
        treeWalk.setFilter(PathFilter.create(path));

        while (treeWalk.next()) {
            if (treeWalk.isSubtree()) {
                treeWalk.enterSubtree();
            } else {
                try {
                    String fileName = treeWalk.getNameString();
                    String filePath = treeWalk.getPathString();
                    String expectedId = fileName;

                    // Validate UUID
                    if (!isValidUUID(expectedId)) {
                        logger.debug("Skipping non-UUID filename: {}", filePath);
                        continue;
                    }

                    // Load content
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repo.open(objectId);
                    String content = new String(loader.getBytes(), StandardCharsets.UTF_8);

                    // Deserialize using subclass implementation
                    T obj = deserializeAndVerify(content, filePath);
                    if (obj == null) {
                        continue;
                    }

                    // Extract ID and name using subclass implementation
                    String itemId = extractId(obj);
                    String itemName = extractName(obj);

                    // Validate
                    if (itemId == null || itemId.isEmpty()) {
                        logger.warn("Skipping {} with null/empty ID: {}", getTypeName(), filePath);
                        continue;
                    }

                    if (!itemId.equals(expectedId)) {
                        logger.warn("{} ID mismatch: filename='{}' but id='{}' in path: {}", getTypeName(), expectedId, itemId, filePath);
                        continue;
                    }

                    // Get commit
                    Iterable<RevCommit> commits = git.log().addPath(filePath).call();
                    String commitId = commits.iterator().next().getName();

                    // Create metadata
                    lst.add(new RepoItemMetadata(itemId, itemName != null ? itemName : itemId, filePath, commitId));

                } catch (Exception e) {
                    logger.error("Failed to process file: {}", treeWalk.getPathString(), e);
                }
            }
        }

        return lst;
    }

    /**
     * Commits an object to the local Git repository and pushes to the remote.
     */
    public String commitAndPush(T object, String message, PersonIdent committer, boolean allowForcePush) {
        Git git = this.gitService.git;
        File dir = this.gitService.dir;
        String serverId = this.gitService.serverId;
        String serverName = configurationController.getServerName();
        String remoteRepoUrl = this.gitService.getRemoteRepoUrl();
        String branch = this.gitService.getRemoteRepoBranch();
        SshSessionFactory sshSessionFactory = this.gitService.getSshSessionFactory();

        StringBuilder response = new StringBuilder();

        // Validate inputs
        if (object == null || extractId(object) == null || extractName(object) == null) {
            return responseResultFail(response, "Object or its ID/name cannot be null.");
        }

        if (message == null) {
            message = "";
        }

        if (committer == null) {
            return responseResultFail(response, "Committer cannot be empty.");
        }

        if (branch == null || branch.trim().isEmpty()) {
            return responseResultFail(response, "Branch cannot be empty.");
        }

        if (remoteRepoUrl == null || remoteRepoUrl.trim().isEmpty()) {
            return responseResultFail(response, "Remote repository URL cannot be empty.");
        }

        if (sshSessionFactory == null) {
            return responseResultFail(response, "SSH session factory cannot be null.");
        }

        if (this.gitService.serializer == null) {
            return responseResultFail(response, "Serializer cannot be null.");
        }

        try {
            // Verify current branch
            String currentBranch = git.getRepository().getBranch();
            if (!branch.equals(currentBranch)) {
                return responseResultFail(response, "Current branch is " + currentBranch + ", expected " + branch);
            }

            // Check repository state
            if (git.getRepository().resolve("HEAD") == null) {
                return responseResultFail(response, "No commits in repository, cannot pull or push.");
            }

            // Check for remote changes
            response.append("Remote Check Result:").append(System.lineSeparator());

            boolean remoteHasChanges = this.gitService.hasRemoteRepoChanges();
            response.append("  Remote Changes: ").append(remoteHasChanges ? "Detected" : "None").append(System.lineSeparator());

            if (remoteHasChanges) {
                // Check for local changes
                Status status = git.status().call();
                if (!status.getModified().isEmpty() || !status.getUncommittedChanges().isEmpty() || !status.getUntracked().isEmpty()) {
                    response.append("Warning: Local changes will be discarded due to overwrite pull:").append(System.lineSeparator());
                    response.append("  Modified: ").append(status.getModified()).append(System.lineSeparator());
                    response.append("  Uncommitted: ").append(status.getUncommittedChanges()).append(System.lineSeparator());
                    response.append("  Untracked: ").append(status.getUntracked()).append(System.lineSeparator());
                }

                // Fetch and reset
                response.append("Pull Overwrite Result:").append(System.lineSeparator());

                FetchCommand fetchCommand = git.fetch();
                fetchCommand.setRemote("origin");
                fetchCommand.setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/remotes/origin/" + branch));
                fetchCommand.setTransportConfigCallback(transport -> {
                    if (transport instanceof SshTransport) {
                        ((SshTransport) transport).setSshSessionFactory(sshSessionFactory);
                    }
                });
                FetchResult fetchResult = fetchCommand.call();
                response.append("  Fetch: ").append(fetchResult.getMessages()).append(System.lineSeparator());

                Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
                if (remoteRef == null) {
                    return responseResultFail(response, "Failed: Remote branch origin/" + branch + " not found.");
                }
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remoteRef.getName()).call();
                response.append("  Reset: Local branch reset to origin/").append(branch).append(System.lineSeparator());
            } else {
                response.append("  Skipped: No pull needed, local and remote branches are in sync").append(System.lineSeparator());
            }

            // Check if object has changed
            if (isNotChanged(object)) {
                return responseResultFail(response, "Object unchanged, no commit needed.");
            }

            // Create directory if needed
            File newDirectory = new File(dir, getDirectory());
            if (!newDirectory.exists() && !newDirectory.mkdirs()) {
                return responseResultFail(response, "Failed to create directory: " + newDirectory.getPath());
            }

            // Write object to local repo
            String id = extractId(object);
            String name = extractName(object);
            String typeName = getTypeName();
            String path = getDirectory() + "/" + id;
            String xml = this.gitService.serializer.serialize(object);

            File file = new File(dir, path);
            try (FileOutputStream fOut = new FileOutputStream(file)) {
                fOut.write(xml.getBytes(StandardCharsets.UTF_8));
            }

            // Stage and commit
            String commitMessage = CommitMessageUtil.create(object, message, serverId, serverName);
            git.add().addFilepattern(path).call();
            RevCommit rc = git.commit().setCommitter(committer).setMessage(commitMessage).call();
            response.append("Commit: Staged and committed ").append(typeName.toLowerCase()).append(" ").append(id).append(System.lineSeparator());

            // Post-commit hook
            postCommit(id, rc.getName());
            response.append("Post-commit: Processed for ").append(typeName.toLowerCase()).append(" ").append(id).append(System.lineSeparator());

            // Configure remote if needed
            StoredConfig config = git.getRepository().getConfig();
            String remoteUrl = config.getString("remote", "origin", "url");
            if (remoteUrl == null || !remoteUrl.equals(remoteRepoUrl)) {
                git.remoteAdd().setName("origin").setUri(new URIish(remoteRepoUrl)).call();
                response.append("Configured remote origin: ").append(remoteRepoUrl).append(System.lineSeparator());
            }

            // Push
            PushCommand pushCommand = git.push();
            pushCommand.setRemote("origin");
            pushCommand.setRefSpecs(new RefSpec("refs/heads/" + branch));
            pushCommand.setForce(allowForcePush);
            pushCommand.setTransportConfigCallback(transport -> {
                if (transport instanceof SshTransport) {
                    ((SshTransport) transport).setSshSessionFactory(sshSessionFactory);
                }
            });

            response.append("Push Result:").append(System.lineSeparator());
            Iterable<PushResult> pushResults = pushCommand.call();
            Iterator<PushResult> iterator = pushResults.iterator();
            if (!iterator.hasNext()) {
                return responseResultFail(response, "No push results returned.");
            }

            PushResult pushResult = iterator.next();
            response.append("  Remote: ").append(pushResult.getURI()).append(System.lineSeparator());

            boolean pushSuccessful = false;
            for (RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
                response.append("  Ref: ").append(update.getRemoteName()).append(", Status: ").append(update.getStatus()).append(", New ObjectId: ").append(update.getNewObjectId() != null ? update.getNewObjectId().name() : "none").append("\n");

                if (update.getStatus() == RemoteRefUpdate.Status.OK) {
                    response.append("    Success: ").append((allowForcePush ? "Force push" : "Push")).append(" completed successfully").append(System.lineSeparator());
                    pushSuccessful = true;
                } else if (update.getStatus() == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                    response.append("    Failed: Non-fast-forward update").append(System.lineSeparator());
                } else if (update.getStatus() == RemoteRefUpdate.Status.REJECTED_OTHER_REASON) {
                    response.append("    Failed: ").append(update.getMessage()).append(System.lineSeparator());
                } else {
                    response.append("    Status: ").append(update.getStatus()).append(System.lineSeparator());
                }
            }

            String messages = pushResult.getMessages();
            if (messages != null && !messages.isEmpty()) {
                response.append("  Messages: ").append(messages).append(System.lineSeparator());
            }

            if (iterator.hasNext()) {
                logger.warn("Additional PushResult objects found but ignored.");
            }

            if (pushSuccessful) {
                String successMessage = "Commit and push " + typeName.toLowerCase() + " to the remote repo successfully!";
                return responseResultSuccess(response, successMessage);
            } else {
                return responseResultFail(response, "");
            }

        } catch (GitAPIException e) {
            return responseResultFail(response, "Git error: " + e.getMessage());
        } catch (IOException e) {
            return responseResultFail(response, "IO error: " + e.getMessage());
        } catch (Exception e) {
            return responseResultFail(response, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Removes an object from the repository
     */
    public String remove(T object, String message, PersonIdent committer, boolean allowForcePush) {
        StringBuilder operationDetails = new StringBuilder();
        ResponseUtil responseUtil = new ResponseUtil();
        String typeName = getTypeName();

        if (object == null || extractId(object) == null || extractName(object) == null) {
            return responseUtil.fail(operationDetails, "Object or its ID/name cannot be null.").toJsonString();
        }
        if (message == null) {
            message = "";
        }
        if (committer == null) {
            return responseUtil.fail(operationDetails, "Committer cannot be empty.").toJsonString();
        }
        if (this.gitService == null || this.gitService.git == null) {
            return responseUtil.fail(operationDetails, "Git service is not initialized.").toJsonString();
        }

        try {
            // Synchronize with remote
            ResponseUtil resetResponse = gitService.resetToRemote();
            operationDetails.append(resetResponse.getOperationDetails());
            if (!resetResponse.isSuccess()) {
                return responseUtil.fail(operationDetails, resetResponse.getMessage()).toJsonString();
            }

            // Check if exists
            String id = extractId(object);
            String name = extractName(object);
            String path = getDirectory() + "/" + id;
            File file = new File(this.gitService.dir, path);

            if (!file.exists()) {
                operationDetails.append("File does not exist in repository for ").append(typeName).append(" ID: ").append(id).append(System.lineSeparator());
                return responseUtil.success(operationDetails, typeName + " with ID " + id + " does not exist in repository.").toJsonString();
            }

            // Delete file
            if (!file.delete()) {
                operationDetails.append("Failed to delete file ").append(path).append(" for ").append(typeName).append(" removal").append(System.lineSeparator());
                return responseUtil.fail(operationDetails, "Failed to delete " + typeName.toLowerCase() + " file.").toJsonString();
            }
            operationDetails.append("Deleted file ").append(path).append(" for ").append(typeName).append(" removal").append(System.lineSeparator());

            // Stage, commit, push
            String commitMessage = typeName + " name: " + name + ". Message: " + message + ". Server Id: " + this.gitService.serverId;
            List<String> filesToStage = new ArrayList<>();
            filesToStage.add(path);

            ResponseUtil stageResponse = gitService.stageCommitAndPush(filesToStage, commitMessage, committer, allowForcePush, true);
            operationDetails.append(stageResponse.getOperationDetails());

            if (!stageResponse.isSuccess()) {
                return responseUtil.fail(operationDetails, stageResponse.getMessage()).toJsonString();
            }

            return responseUtil.success(operationDetails, typeName + " removed successfully!").toJsonString();

        } catch (Exception e) {
            operationDetails.append("Unexpected error: ").append(e.getMessage()).append(System.lineSeparator());
            return responseUtil.fail(operationDetails, "Unexpected error occurred during " + typeName.toLowerCase() + " removal.").toJsonString();
        }
    }

    /**
     * Check if object has changed
     */
    protected boolean isChanged(T object) throws IOException {
        if (object == null || extractId(object) == null) {
            throw new IllegalArgumentException("Object or its ID cannot be null");
        }
        if (this.gitService.serializer == null) {
            throw new IllegalArgumentException("Serializer cannot be null");
        }

        Git git = this.gitService.git;
        String path = getDirectory() + "/" + extractId(object);

        String xml;
        try {
            xml = this.gitService.serializer.serialize(object);
        } catch (Exception e) {
            logger.error("Failed to serialize object with ID " + extractId(object), e);
            throw new IOException("Serialization failed", e);
        }
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);

        try (ObjectInserter inserter = git.getRepository().newObjectInserter()) {
            ObjectId newBlobId = inserter.insert(Constants.OBJ_BLOB, xmlBytes);
            inserter.flush();

            ObjectId headBlobId = null;
            ObjectId headId = git.getRepository().resolve("HEAD");
            if (headId != null) {
                try (RevWalk revWalk = new RevWalk(git.getRepository())) {
                    RevCommit headCommit = revWalk.parseCommit(headId);
                    try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                        treeWalk.addTree(headCommit.getTree());
                        treeWalk.setRecursive(true);
                        treeWalk.setFilter(PathFilter.create(path));
                        if (treeWalk.next()) {
                            headBlobId = treeWalk.getObjectId(0);
                        }
                    }
                }
            }

            if (headBlobId == null) {
                return true;
            }

            return !newBlobId.equals(headBlobId);
        } catch (IOException e) {
            logger.error("Failed to compare blob hashes for object with ID " + extractId(object), e);
            throw e;
        }
    }

    protected boolean isNotChanged(T object) throws IOException {
        return !isChanged(object);
    }

    /**
     * Get commit history for a file
     */
    public List<CommitMetaData> getHistory(String fileName) {
        List<CommitMetaData> lst = new ArrayList<>();

        if (StringUtils.isBlank(fileName) || fileName.contains("..") || fileName.contains("/")) {
            logger.error("Invalid fileName: {}", fileName);
            return lst;
        }

        ResponseUtil resetResponse = gitService.resetToRemote();
        if (!resetResponse.isSuccess()) {
            logger.error(resetResponse.getOperationDetails());
            return lst;
        }

        String path = getDirectory() + "/" + fileName;
        try {
            Repository repo = this.gitService.git.getRepository();
            LogCommand logCommand = this.gitService.git.log().add(repo.resolve("HEAD")).addPath(path);
            Iterator<RevCommit> rcItr = logCommand.call().iterator();
            while (rcItr.hasNext()) {
                RevCommit rc = rcItr.next();

                // create CommitMetaData
                String hash = rc.getId().getName();
                String committer = rc.getCommitterIdent() != null ? rc.getCommitterIdent().getName() : "Unknown";
                long timestamp = rc.getCommitTime() * 1000L; // Convert seconds to milliseconds
                String message = rc.getFullMessage() != null ? rc.getFullMessage() : "";

                lst.add(new CommitMetaData(hash, committer, timestamp, message));
            }
        } catch (GitAPIException | IOException e) {
            logger.error("Failed to retrieve commit history for file: {}", fileName, e);
            return lst;
        }

        return lst;
    }

    /**
     * Get content of a file at specific revision
     */
    public String getContent(String fileName, String revision) throws Exception {
        String content = null;
        if (StringUtils.isBlank(fileName) || StringUtils.isBlank(revision)) {
            return content;
        }

        Repository repo = this.gitService.git.getRepository();
        String path = getDirectory() + "/" + fileName;

        try (TreeWalk tw = new TreeWalk(repo)) {
            ObjectId rcid = repo.resolve(revision);
            if (rcid != null) {
                RevCommit rc = repo.parseCommit(rcid);

                tw.setRecursive(true);
                tw.setFilter(PathFilter.create(path));
                tw.addTree(rc.getTree());

                if (tw.next()) {
                    ObjectLoader objLoader = repo.open(tw.getObjectId(0));
                    byte[] bytes = objLoader.getBytes();
                    content = new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to get content for file: {}, revision: {}", fileName, revision, e);
        }

        return content;
    }

    // ========== Helper methods ==========

    // Simple and clean
    protected String responseResultSuccess(StringBuilder response, String successMessage) {
        return new ResponseUtil().success(response, successMessage).toJsonString();
    }

    protected String responseResultFail(StringBuilder response, String errorMessage) {
        return new ResponseUtil().fail(response, errorMessage).toJsonString();
    }

    private boolean isValidUUID(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}