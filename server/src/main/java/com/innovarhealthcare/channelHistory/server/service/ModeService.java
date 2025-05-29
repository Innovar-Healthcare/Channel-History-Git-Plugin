package com.innovarhealthcare.channelHistory.server.service;

import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.util.ResponseUtil;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import org.apache.commons.lang3.StringUtils;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;

import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.FetchResult;

import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Abstract service for managing versioned objects in a Git repository.
 */
public abstract class ModeService {
    private static final Logger logger = LoggerFactory.getLogger(ModeService.class);
    protected final GitRepositoryService gitService;

    public abstract String getDirectory();

    public ModeService(GitRepositoryService gitService) {
        this.gitService = gitService;
    }

    /**
     * Commits a VersionedObject to the local Git repository and pushes to the remote.
     * Checks if the remote branch differs from the local branch and overwrites if needed,
     * then stages and commits the object if changed, and pushes (optionally with force).
     *
     * @param object         The VersionedObject to serialize and commit
     * @param message        The commit message
     * @param committer      The PersonIdent for the commit author
     * @param allowForcePush If true, force pushes to overwrite the remote branch
     * @return JSON string with operation result (validate: success/fail, body: message)
     * @throws IllegalArgumentException If inputs are invalid
     */
    public String commitAndPush(Object object, String message, PersonIdent committer, boolean allowForcePush) {
        Git git = this.gitService.git;
        File dir = this.gitService.dir;
        String serverId = this.gitService.serverId;
        ObjectXMLSerializer serializer = this.gitService.serializer;
        String remoteRepoUrl = this.gitService.getRemoteRepoUrl();
        String branch = this.gitService.getRemoteRepoBranch();
        SshSessionFactory sshSessionFactory = this.gitService.getSshSessionFactory();

        JSONObject result = new JSONObject();
        StringBuilder response = new StringBuilder();

        // Validate inputs
        if (object == null || getObjectId(object) == null || getObjectName(object) == null) {
            return responseResultFail(response, "Object or its ID/name cannot be null.");
        }

        if (message == null) {
            message = ""; // Default to empty message
        }

        if (committer == null) {
            return responseResultFail(response, "Committer cannot be empty.");
        }

        if (branch == null || branch.trim().isEmpty()) {
            result.put("validate", "fail");
            result.put("body", "Branch cannot be empty.");
            return result.toString();
        }

        if (remoteRepoUrl == null || remoteRepoUrl.trim().isEmpty()) {
            return responseResultFail(response, "Remote repository URL cannot be empty.");
        }

        if (sshSessionFactory == null) {
            return responseResultFail(response, "SSH session factory cannot be null.");
        }

        if (serializer == null) {
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
                // Check for local changes (to warn if discarded)
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
                        SshTransport sshTransport = (SshTransport) transport;
                        sshTransport.setSshSessionFactory(sshSessionFactory);
                    }
                });
                FetchResult fetchResult = fetchCommand.call();
                response.append("  Fetch: ").append(fetchResult.getMessages()).append(System.lineSeparator());

                Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
                if (remoteRef == null) {
                    result.put("validate", "fail");
                    result.put("body", response.toString() + "Failed: Remote branch origin/" + branch + " not found.");
                    return result.toString();
                }
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remoteRef.getName()).call();
                response.append("  Reset: Local branch reset to origin/" + branch).append(System.lineSeparator());
            } else {
                response.append("  Skipped: No pull needed, local and remote branches are in sync").append(System.lineSeparator());
            }

            // Check if object has changed
            if (isNotChanged(object)) {
                return responseResultFail(response, "Object unchanged, no commit needed.");
            }

            // Create directory if it doesn't exist
            File newDirectory = new File(dir, getDirectory());
            if (!newDirectory.exists() && !newDirectory.mkdirs()) {
                return responseResultFail(response, "Failed to create directory: " + newDirectory.getPath());
            }

            // Write object to local repo
            String id = getObjectId(object);
            String path = getDirectory() + "/" + id;
            String xml = serializer.serialize(object);

            File file = new File(dir, path);
            try (FileOutputStream fOut = new FileOutputStream(file)) {
                fOut.write(xml.getBytes(StandardCharsets.UTF_8));
            }

            // Stage and commit
            String objectType = object instanceof Channel ? "Channel" : object instanceof CodeTemplate ? "Code Template" : "Object";
            String commentMsg = objectType + " name: " + getObjectName(object) + ". Message: " + message + ". Server Id: " + serverId;
            git.add().addFilepattern(path).call();
            RevCommit rc = git.commit().setCommitter(committer).setMessage(commentMsg).call();
            response.append("Commit: Staged and committed " + objectType.toLowerCase() + " " + id).append(System.lineSeparator());

            // Allow subclasses to perform post-commit actions
            postCommit(id, rc.getName());
            response.append("Post-commit: Processed for " + objectType.toLowerCase() + " " + id).append(System.lineSeparator());

            // Configure remote if not already set
            StoredConfig config = git.getRepository().getConfig();
            String remoteUrl = config.getString("remote", "origin", "url");
            if (remoteUrl == null || !remoteUrl.equals(remoteRepoUrl)) {
                RemoteAddCommand remoteAddCommand = git.remoteAdd();
                remoteAddCommand.setName("origin");
                remoteAddCommand.setUri(new URIish(remoteRepoUrl));
                remoteAddCommand.call();
                response.append("Configured remote origin: " + remoteRepoUrl).append(System.lineSeparator());
            }

            // Push to remote repo
            PushCommand pushCommand = git.push();
            pushCommand.setRemote("origin");
            pushCommand.setRefSpecs(new RefSpec("refs/heads/" + branch));
            pushCommand.setForce(allowForcePush);
            pushCommand.setTransportConfigCallback(transport -> {
                if (transport instanceof SshTransport) {
                    SshTransport sshTransport = (SshTransport) transport;
                    sshTransport.setSshSessionFactory(sshSessionFactory);
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
                response.append("  Ref: ").append(update.getRemoteName())
                        .append(", Status: ").append(update.getStatus())
                        .append(", New ObjectId: ").append(update.getNewObjectId() != null ? update.getNewObjectId().name() : "none")
                        .append("\n");

                if (update.getStatus() == RemoteRefUpdate.Status.OK) {
                    response.append("    Success: ").append((allowForcePush ? "Force push" : "Push")).append(" completed successfully").append(System.lineSeparator());
                    pushSuccessful = true;
                } else if (update.getStatus() == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                    response.append("    Failed: Non-fast-forward update, possibly due to new remote changes").append(System.lineSeparator());
                } else if (update.getStatus() == RemoteRefUpdate.Status.REJECTED_OTHER_REASON) {
                    response.append("    Failed: ").append(update.getMessage()).append(System.lineSeparator());
                } else {
                    response.append("    Status: ").append(update.getStatus()).append(System.lineSeparator());
                }
            }

            // Include push messages
            String messages = pushResult.getMessages();
            if (messages != null && !messages.isEmpty()) {
                response.append("  Messages: ").append(messages).append(System.lineSeparator());
            }

            // Warn if additional PushResult objects exist
            if (iterator.hasNext()) {
                logger.warn("Additional PushResult objects found but ignored.");
            }

            // Set JSON result
            if (pushSuccessful) {
                return responseResultSuccess(response, objectType);
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
     * Removes an object from the local Git repository and pushes the deletion to the remote.
     * Synchronizes with the remote using resetToRemote, deletes the object's file, and stages/commits/pushes using stageCommitAndPush.
     *
     * @param object         The object to remove (Channel or CodeTemplate)
     * @param message        The commit message
     * @param committer      The PersonIdent for the commit author
     * @param allowForcePush If true, force pushes to overwrite the remote branch
     * @return JSON string with operation result (status: true/false, message, operationDetails)
     */
    public String remove(Object object, String message, PersonIdent committer, boolean allowForcePush) {
        StringBuilder operationDetails = new StringBuilder();
        ResponseUtil responseUtil = new ResponseUtil();
        String objectType = object instanceof Channel ? "Channel" : object instanceof CodeTemplate ? "Code Template" : "Object";

        // Validate inputs
        if (object == null || getObjectId(object) == null || getObjectName(object) == null) {
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

            // Check if object exists in repository directory
            String id = getObjectId(object);
            String path = getDirectory() + "/" + id;
            File file = new File(this.gitService.dir, path);
            if (!file.exists()) {
                operationDetails.append("File does not exist in repository for ").append(objectType).append(" ID: ").append(id).append(System.lineSeparator());
                return responseUtil.success(operationDetails, objectType + " with ID " + id + " does not exist in repository.").toJsonString();
            }

            // Remove the file
            if (!file.delete()) {
                operationDetails.append("Failed to delete file ").append(path).append(" for ").append(objectType).append(" removal").append(System.lineSeparator());
                return responseUtil.fail(operationDetails, "Failed to delete " + objectType.toLowerCase() + " file.").toJsonString();
            }
            operationDetails.append("Deleted file ").append(path).append(" for ").append(objectType).append(" removal").append(System.lineSeparator());

            // Stage, commit, and push the deletion
            String commitMessage = objectType + " name: " + getObjectName(object) + ". Message: " + message + ". Server Id: " + this.gitService.serverId;
            List<String> filesToStage = new ArrayList<>();
            filesToStage.add(path);
            ResponseUtil stageResponse = gitService.stageCommitAndPush(filesToStage, commitMessage, committer, allowForcePush, true);
            operationDetails.append(stageResponse.getOperationDetails());

            if (!stageResponse.isSuccess()) {
                return responseUtil.fail(operationDetails, stageResponse.getMessage()).toJsonString();
            }

            return responseUtil.success(operationDetails, objectType + " removed successfully!").toJsonString();
        } catch (Exception e) {
            operationDetails.append("Unexpected error: ").append(e.getMessage()).append(System.lineSeparator());
            return responseUtil.fail(operationDetails, "Unexpected error occurred during " + objectType.toLowerCase() + " removal.").toJsonString();
        }
    }

    /**
     * Checks if the object has changed compared to its stored version in the repository's HEAD.
     * Uses Git blob hashes for efficient comparison.
     *
     * @param object The object to check (Channel or CodeTemplate)
     * @return true if the object has changed or the file is missing, false otherwise
     * @throws IllegalArgumentException If object or its ID is null
     * @throws IOException              If Git object operations fail
     */
    protected boolean isChanged(Object object) throws IOException {
        if (object == null || getObjectId(object) == null) {
            throw new IllegalArgumentException("Object or its ID cannot be null");
        }
        if (this.gitService.serializer == null) {
            throw new IllegalArgumentException("Serializer cannot be null");
        }

        Git git = this.gitService.git;
        String path = getDirectory() + "/" + getObjectId(object);

        // Serialize object to XML
        String xml;
        try {
            xml = this.gitService.serializer.serialize(object);
        } catch (Exception e) {
            logger.error("Failed to serialize object with ID " + getObjectId(object), e);
            throw new IOException("Serialization failed", e);
        }
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);

        // Compute hash of the new object content
        try (ObjectInserter inserter = git.getRepository().newObjectInserter()) {
            ObjectId newBlobId = inserter.insert(Constants.OBJ_BLOB, xmlBytes);
            inserter.flush();

            // Get hash of the file in HEAD (if it exists)
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

            // If file doesn't exist in HEAD, it's a change
            if (headBlobId == null) {
                return true;
            }

            // Compare blob hashes
            return !newBlobId.equals(headBlobId);
        } catch (IOException e) {
            logger.error("Failed to compare blob hashes for object with ID " + getObjectId(object), e);
            throw e;
        }
    }

    /**
     * Checks if the VersionedObject has not changed compared to its stored version.
     *
     * @param object The VersionedObject to check
     * @return true if the object is unchanged, false otherwise
     * @throws IOException If file or object operations fail
     */
    protected boolean isNotChanged(Object object) throws IOException {
        return !isChanged(object);
    }

    /**
     * Hook for subclasses to perform actions after committing.
     *
     * @param id       The ID of the committed object
     * @param commitId The commit ID
     */
    protected void postCommit(String id, String commitId) {
        // Default implementation does nothing
    }

    /**
     * Retrieves the commit history for a specific file from the local repository after synchronizing with the remote.
     * Checks for remote changes and performs a pull with reset if needed, mirroring commitAndPush behavior.
     *
     * @param fileName The name of the file (e.g., object ID for Channel or CodeTemplate)
     * @return List of JSON strings containing commit details
     * @throws GitAPIException If Git operations fail
     * @throws IOException     If IO operations fail
     */
    public List<String> getHistory(String fileName) {
        List<String> lst = new ArrayList<>();

        // Validate input
        if (StringUtils.isBlank(fileName) || fileName.contains("..") || fileName.contains("/")) {
            logger.error("Invalid fileName: {}", fileName);
            return lst;
        }

        // Synchronize with remote
        ResponseUtil resetResponse = gitService.resetToRemote();
        if (!resetResponse.isSuccess()) {
            logger.error(resetResponse.getOperationDetails());
            return lst;
        }

        // Retrieve commit history from local HEAD
        String path = getDirectory() + "/" + fileName;
        try {
            Repository repo = this.gitService.git.getRepository();
            LogCommand logCommand = this.gitService.git.log().add(repo.resolve("HEAD")).addPath(path);
            Iterator<RevCommit> rcItr = logCommand.call().iterator();
            while (rcItr.hasNext()) {
                RevCommit rc = rcItr.next();
                CommitMetaData metaData = new CommitMetaData(rc);
                lst.add(metaData.toJson());
            }
        } catch (GitAPIException | IOException e) {
            logger.error("Failed to retrieve local commit history for file: {}", fileName, e);
            return lst;
        }

        return lst;
    }

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
                    ObjectStream stream = objLoader.openStream();
                    byte[] buf = new byte[1024];
                    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                    while (true) {
                        int len = stream.read(buf);
                        if (len <= 0) {
                            break;
                        }
                        byteOut.write(buf, 0, len);
                    }
                    stream.close();

                    content = new String(byteOut.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            // logger.debug("commit " + revision + " not found for file " + fileName, e);
        }

        return content;
    }

    public List<String> load() throws Exception {
        List<String> lst = new ArrayList<>();
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
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repo.open(objectId);
                String content = new String(loader.getBytes(), StandardCharsets.UTF_8);

                // Convert to JSON Object
                JSONObject obj = new JSONObject();
                obj.put("content", content);

                Iterable<RevCommit> commits = git.log().addPath(treeWalk.getPathString()).call();
                obj.put("lastCommitId", commits.iterator().next().getName());

                lst.add(obj.toString());
            }
        }

        return lst;
    }

    private String getObjectId(Object object) {
        if (object instanceof Channel) {
            return ((Channel) object).getId();
        } else if (object instanceof CodeTemplate) {
            return ((CodeTemplate) object).getId();
        }
        throw new IllegalArgumentException("Object must be a Channel or CodeTemplate");
    }

    private String getObjectName(Object object) {
        if (object instanceof Channel) {
            return ((Channel) object).getName();
        } else if (object instanceof CodeTemplate) {
            return ((CodeTemplate) object).getName();
        }
        throw new IllegalArgumentException("Object must be a Channel or CodeTemplate");
    }

    /**
     * Creates a JSON result for a successful operation.
     *
     * @param response   The StringBuilder containing operation details
     * @param objectType The type of object (e.g., "Channel", "Code Template")
     * @return JSON string with validate: success and body message
     */
    protected String responseResultSuccess(StringBuilder response, String objectType) {
        JSONObject result = new JSONObject();
        result.put("validate", "success");
//        result.put("body", response.toString() + "Commit and push " + objectType.toLowerCase() + " to the remote repo successfully!");
        result.put("body", "Commit and push " + objectType.toLowerCase() + " to the remote repo successfully!");

        return result.toString();
    }

    /**
     * Creates a JSON result for a failed operation.
     *
     * @param response     The StringBuilder containing operation details
     * @param errorMessage The specific error message
     * @return JSON string with validate: fail and body message
     */
    protected String responseResultFail(StringBuilder response, String errorMessage) {
        JSONObject result = new JSONObject();
        result.put("validate", "fail");
        result.put("body", response.toString() + errorMessage);
        return result.toString();
    }
}