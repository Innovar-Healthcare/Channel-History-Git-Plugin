package com.innovarhealthcare.channelHistory.server.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.innovarhealthcare.channelHistory.server.exception.GitFileNotFoundException;
import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Handles all Git operations for the version history plugin.
 * Provides methods for pull, commit, push, and status checking.
 */
public class GitOperations {

    private static final Logger logger = LogManager.getLogger(GitOperations.class);

    private final Git git;
    private final String branch;
    private final SshSessionFactory sshSessionFactory;

    /**
     * Creates a new GitOperations instance
     *
     * @param git               The JGit Git instance
     * @param branch            The branch name (e.g., "main", "master")
     * @param sshSessionFactory SSH session factory for authentication
     */
    public GitOperations(Git git, String branch, SshSessionFactory sshSessionFactory) {
        if (git == null) {
            throw new IllegalArgumentException("Git instance cannot be null");
        }
        if (branch == null || branch.trim().isEmpty()) {
            throw new IllegalArgumentException("Branch cannot be null or empty");
        }
        if (sshSessionFactory == null) {
            throw new IllegalArgumentException("SSH session factory cannot be null");
        }

        this.git = git;
        this.branch = branch;
        this.sshSessionFactory = sshSessionFactory;
    }

    /**
     * Reads all files from a directory in the latest commit
     *
     * @param directory Directory path (e.g., "libraries", "codetemplates")
     * @return List of committed files with content and metadata
     * @throws GitAPIException if git operation fails
     * @throws IOException     if I/O error occurs
     */
    public List<CommittedFile> readCommittedFiles(String directory) throws GitAPIException, IOException {

        logger.debug("Reading committed files from directory: {}", directory);

        List<CommittedFile> files = new ArrayList<>();
        Repository repo = git.getRepository();
        String path = directory + "/";

        // Get latest commit
        ObjectId lastCommitId = repo.resolve(Constants.HEAD);
        if (lastCommitId == null) {
            logger.warn("No commits in repository");
            return files;
        }

        RevWalk revWalk = new RevWalk(repo);
        RevCommit commit = revWalk.parseCommit(lastCommitId);
        RevTree tree = commit.getTree();

        // Walk through tree
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

                    // Read file content from Git object
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repo.open(objectId);
                    byte[] content = loader.getBytes();

                    // Get last commit for this file
                    Iterable<RevCommit> commits = git.log().addPath(filePath).call();
                    String commitId = commits.iterator().hasNext() ? commits.iterator().next().getName() : lastCommitId.getName();

                    files.add(new CommittedFile(fileName, filePath, content, commitId));

                } catch (Exception e) {
                    logger.error("Failed to read file: {}", treeWalk.getPathString(), e);
                }
            }
        }

        revWalk.close();
        treeWalk.close();

        logger.info("Read {} files from directory: {}", files.size(), directory);
        return files;
    }

    /**
     * Reads file content at a specific revision (commit)
     *
     * @param filePath Relative file path from repository root (e.g., "libraries/abc-123")
     * @param revision Commit SHA or ref (e.g., "HEAD", "main", commit hash)
     * @return File content as bytes
     * @throws GitFileNotFoundException if file not found at revision
     * @throws GitAPIException          if git operation fails
     * @throws IOException              if I/O error occurs
     */
    public byte[] readFileAtRevision(String filePath, String revision) throws GitFileNotFoundException, GitAPIException, IOException {

        logger.debug("Reading file '{}' at revision '{}'", filePath, revision);

        Repository repo = git.getRepository();

        try (TreeWalk treeWalk = new TreeWalk(repo)) {
            // Resolve revision
            ObjectId revisionId = repo.resolve(revision);
            if (revisionId == null) {
                throw new GitFileNotFoundException("Invalid revision: " + revision + " for file: " + filePath);
            }

            // Parse commit
            RevCommit commit = repo.parseCommit(revisionId);

            // Walk tree to find file
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));
            treeWalk.addTree(commit.getTree());

            // Check if file exists
            if (!treeWalk.next()) {
                throw new GitFileNotFoundException("File not found: " + filePath + " at revision: " + revision);
            }

            // Load file content
            ObjectLoader loader = repo.open(treeWalk.getObjectId(0));
            byte[] content = loader.getBytes();

            logger.debug("Successfully read {} bytes from file '{}'", content.length, filePath);
            return content;

        } catch (GitFileNotFoundException e) {
            throw e;

        } catch (IOException e) {
            throw new IOException("Failed to read file: " + filePath + " at revision: " + revision, e);
        }
    }

    /**
     * Gets commit history for a file
     *
     * @param filePath Relative file path from repository root (e.g., "libraries/abc-123")
     * @return List of commit metadata, ordered from newest to oldest
     * @throws GitAPIException if git operation fails
     * @throws IOException     if I/O error occurs
     */
    public List<CommitMetaData> getFileHistory(String filePath) throws GitAPIException, IOException {

        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        logger.debug("Getting commit history for file: {}", filePath);

        List<CommitMetaData> history = new ArrayList<>();
        Repository repo = git.getRepository();

        // Get commit history for specific file
        LogCommand logCommand = git.log().add(repo.resolve(Constants.HEAD)).addPath(filePath);

        Iterable<RevCommit> commits = logCommand.call();

        for (RevCommit commit : commits) {
            String hash = commit.getId().getName();
            String committer = commit.getCommitterIdent() != null ? commit.getCommitterIdent().getName() : "Unknown";
            long timestamp = commit.getCommitTime() * 1000L; // Convert to milliseconds
            String message = commit.getFullMessage() != null ? commit.getFullMessage() : "";

            history.add(new CommitMetaData(hash, committer, timestamp, message));
        }

        logger.info("Found {} commits for file: {}", history.size(), filePath);
        return history;
    }

    /**
     * Checks if the remote repository has changes that are not in local
     *
     * @return true if remote has changes, false otherwise
     * @throws GitAPIException if git operation fails
     * @throws IOException     if I/O error occurs
     */
    public boolean hasRemoteChanges() throws GitAPIException, IOException {
        logger.debug("Checking for remote changes on branch: {}", branch);

        // Fetch
        //@formatter:off
        git.fetch()
           .setRemote("origin")
           .setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/remotes/origin/" + branch))
           .setTransportConfigCallback(transport -> {
                if (transport instanceof SshTransport) {
                    ((SshTransport) transport).setSshSessionFactory(sshSessionFactory);
                }
            })
            .call();
        //@formatter:on

        // Get refs
        Ref localRef = git.getRepository().findRef(branch);
        Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);

        // Simple comparison
        if (localRef == null || remoteRef == null) {
            return false;
        }

        return !localRef.getObjectId().equals(remoteRef.getObjectId());
    }

    /**
     * Pulls changes from remote repository with hard reset (overwrites local changes)
     *
     * @return Pull result message
     * @throws GitAPIException if git operation fails
     * @throws IOException     if I/O error occurs
     */
    public String pullWithOverwrite() throws GitAPIException, IOException {
        logger.info("Pulling with overwrite from origin/{}", branch);

        StringBuilder result = new StringBuilder();

        // Check for local changes that will be lost
        Status status = git.status().call();
        if (!status.getModified().isEmpty() || !status.getUntracked().isEmpty()) {
            result.append("Warning: Local changes will be discarded:\n");
            result.append("  Modified: ").append(status.getModified()).append("\n");
            result.append("  Untracked: ").append(status.getUntracked()).append("\n");
            logger.warn("Local changes will be discarded: {}", status.getModified());
        }

        // Fetch from remote
        FetchCommand fetchCommand = git.fetch();
        fetchCommand.setRemote("origin");
        fetchCommand.setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/remotes/origin/" + branch));
        fetchCommand.setTransportConfigCallback(transport -> {
            if (transport instanceof SshTransport) {
                ((SshTransport) transport).setSshSessionFactory(sshSessionFactory);
            }
        });

        FetchResult fetchResult = fetchCommand.call();
        result.append("Fetch completed: ").append(fetchResult.getMessages()).append("\n");

        // Reset to remote branch
        Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
        if (remoteRef == null) {
            throw new GitAPIException("Remote branch origin/" + branch + " not found") {
            };
        }

        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remoteRef.getName()).call();

        result.append("Reset to origin/").append(branch).append(" completed\n");

        logger.info("Pull with overwrite completed successfully");
        return result.toString();
    }

    /**
     * Stages files for commit
     *
     * @param filePaths List of file paths to stage (relative to repo root)
     * @throws GitAPIException if git operation fails
     */
    public void stageFiles(List<String> filePaths) throws GitAPIException {
        if (filePaths == null || filePaths.isEmpty()) {
            logger.warn("No files to stage");
            return;
        }

        logger.debug("Staging {} files", filePaths.size());

        for (String path : filePaths) {
            git.add().addFilepattern(path).call();
            logger.debug("Staged file: {}", path);
        }

        logger.info("Successfully staged {} files", filePaths.size());
    }

    /**
     * Commits staged changes
     *
     * @param message   Commit message
     * @param committer Person making the commit
     * @return Commit SHA
     * @throws GitAPIException if git operation fails
     */
    public String commit(String message, PersonIdent committer) throws GitAPIException {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Commit message cannot be empty");
        }
        if (committer == null) {
            throw new IllegalArgumentException("Committer cannot be null");
        }

        logger.info("Creating commit with message: {}", message);

        org.eclipse.jgit.revwalk.RevCommit commit = git.commit().setCommitter(committer).setMessage(message).call();

        String commitSha = commit.getName();
        logger.info("Commit created successfully: {}", commitSha);

        return commitSha;
    }

    /**
     * Commits files with staging in one operation
     *
     * @param filePaths List of file paths to commit
     * @param message   Commit message
     * @param committer Person making the commit
     * @return Commit SHA
     * @throws GitAPIException if git operation fails
     */
    public String commitFiles(List<String> filePaths, String message, PersonIdent committer) throws GitAPIException {
        stageFiles(filePaths);
        return commit(message, committer);
    }

    /**
     * Pushes commits to remote repository
     *
     * @param forcePush If true, uses force push
     * @return Push result summary
     * @throws GitAPIException if git operation fails
     */
    public String push(boolean forcePush) throws GitAPIException, GitPushFailedException {
        logger.info("Pushing to origin/{} (force: {})", branch, forcePush);

        PushCommand pushCommand = git.push();
        pushCommand.setRemote("origin");
        pushCommand.setRefSpecs(new RefSpec("refs/heads/" + branch));
        pushCommand.setForce(forcePush);
        pushCommand.setTransportConfigCallback(transport -> {
            if (transport instanceof SshTransport) {
                ((SshTransport) transport).setSshSessionFactory(sshSessionFactory);
            }
        });

        Iterable<PushResult> results = pushCommand.call();

        StringBuilder resultMessage = new StringBuilder();
        boolean pushSuccessful = false;

        for (PushResult result : results) {
            resultMessage.append("Remote: ").append(result.getURI()).append("\n");

            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                resultMessage.append("  Ref: ").append(update.getRemoteName()).append(", Status: ").append(update.getStatus()).append("\n");

                if (update.getStatus() == RemoteRefUpdate.Status.OK) {
                    pushSuccessful = true;
                    logger.info("Push successful for ref: {}", update.getRemoteName());
                } else {
                    logger.warn("Push status for {}: {}", update.getRemoteName(), update.getStatus());
                    if (update.getMessage() != null) {
                        resultMessage.append("    Message: ").append(update.getMessage()).append("\n");
                    }
                }
            }
        }

        if (!pushSuccessful) {
            throw new GitPushFailedException("Push failed: " + resultMessage.toString()) {
            };
        }

        return resultMessage.toString();
    }

    /**
     * Gets the current branch name
     *
     * @return Current branch name
     * @throws IOException if I/O error occurs
     */
    public String getCurrentBranch() throws IOException {
        return git.getRepository().getBranch();
    }

    /**
     * Validates that the current branch matches the configured branch
     *
     * @throws GitOperationException if branches don't match
     */
    public void validateCurrentBranch() throws GitOperationException {
        try {
            String currentBranch = getCurrentBranch();
            if (!branch.equals(currentBranch)) {
                throw new GitOperationException("Current branch is " + currentBranch + ", expected " + branch);
            }
        } catch (IOException e) {
            throw new GitOperationException("Failed to get current branch", e);
        }
    }

    /**
     * Checks if repository has any commits
     *
     * @return true if repository has commits, false otherwise
     * @throws IOException if I/O error occurs
     */
    public boolean hasCommits() throws IOException {
        return git.getRepository().resolve("HEAD") != null;
    }

    /**
     * Gets the configured branch name
     *
     * @return Branch name
     */
    public String getBranch() {
        return branch;
    }

    /**
     * Represents a file from Git repository with its content and metadata
     */
    public static class CommittedFile {
        private final String fileName;
        private final String filePath;
        private final byte[] content;
        private final String lastCommitId;

        public CommittedFile(String fileName, String filePath, byte[] content, String lastCommitId) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.content = content;
            this.lastCommitId = lastCommitId;
        }

        public String getFileName() {
            return fileName;
        }

        public String getFilePath() {
            return filePath;
        }

        public byte[] getContent() {
            return content;
        }

        public String getContentAsString() {
            return new String(content, StandardCharsets.UTF_8);
        }

        public String getLastCommitId() {
            return lastCommitId;
        }
    }
}
