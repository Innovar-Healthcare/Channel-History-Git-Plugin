package com.innovarhealthcare.channelHistory.server.repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.server.file.FileOperations;
import com.innovarhealthcare.channelHistory.server.git.GitOperations;
import com.innovarhealthcare.channelHistory.shared.util.CommitMessageUtil;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.server.controllers.ControllerFactory;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Repository for managing CodeTemplateLibrary entities.
 * Extends BaseRepository with library-specific batch operations.
 */
public class LibraryRepository extends BaseRepository<CodeTemplateLibrary> {

    private static final String DIRECTORY = "libraries";
    private static final String TYPE_NAME = "Library";

    public LibraryRepository(GitOperations gitOps, FileOperations fileOps, String serverId) {
        super(gitOps, fileOps, serverId);
    }

    @Override
    protected CodeTemplateLibrary deserializeAndVerify(String content, String filePath) {
        try {
            CodeTemplateLibrary library = fileOps.deserializeXml(content, CodeTemplateLibrary.class);

            if (library == null) {
                logger.warn("Deserialized code template library is null: {}", filePath);
                return null;
            }

            // ✅ CodeTemplateLibrary specific: no special validation needed
            return library;

        } catch (Exception e) {
            logger.warn("Failed to deserialize code template library from: {}", filePath, e);
            return null;
        }
    }

    @Override
    protected String extractId(CodeTemplateLibrary library) {
        return library.getId();
    }

    @Override
    protected String extractName(CodeTemplateLibrary library) {
        return library.getName();
    }

    @Override
    protected Class<CodeTemplateLibrary> getEntityClass() {
        return CodeTemplateLibrary.class;
    }

    @Override
    public String getDirectory() {
        return DIRECTORY;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    /**
     * Saves multiple libraries and commits/pushes to git.
     * All libraries are committed in a single commit for clean git history.
     *
     * @param libraries List of libraries to save
     * @param message   Commit message
     * @param committer Person making the commit
     * @param forcePush Whether to force push
     * @return Result message with commit/push status
     * @throws GitPushFailedException, GitOperationException if save, commit, or push fails
     */
    public String saveAllAndPush(List<CodeTemplateLibrary> libraries, String message, PersonIdent committer, boolean forcePush) throws GitPushFailedException, GitOperationException {
        if (libraries == null || libraries.isEmpty()) {
            throw new IllegalArgumentException("Libraries list cannot be null or empty");
        }
        if (committer == null) {
            throw new IllegalArgumentException("Committer cannot be null");
        }

        logger.info("Saving and pushing {} libraries", libraries.size());

        StringBuilder result = new StringBuilder();

        try {
            // Validate git state
            gitOps.validateCurrentBranch();

            if (!gitOps.hasCommits()) {
                throw new GitOperationException("No commits in repository, cannot pull or push");
            }

            // Check for remote changes
            result.append("Remote Check Result:\n");
            boolean remoteHasChanges = gitOps.hasRemoteChanges();
            result.append("  Remote Changes: ").append(remoteHasChanges ? "Detected" : "None").append("\n");

            if (remoteHasChanges) {
                result.append("Pull Overwrite Result:\n");
                String pullResult = gitOps.pullWithOverwrite();
                result.append(pullResult);
            } else {
                result.append("  Skipped: No pull needed, local and remote branches are in sync\n");
            }

            // Save all libraries and get paths
            List<String> paths = saveAll(libraries);

            // Build commit message using CommitMessageUtil
            String commitMessage = buildBatchCommitMessage(libraries, message, committer);

            // Commit all files in one commit
            String commitSha = gitOps.commitFiles(paths, commitMessage, committer);

            result.append("Commit: Staged and committed ").append(libraries.size()).append(" libraries (").append(commitSha.substring(0, 7)).append(")\n");

            // List committed libraries
            for (CodeTemplateLibrary library : libraries) {
                result.append("  - ").append(library.getName()).append("\n");
            }

            // Push
            result.append("Push Result:\n");
            String pushResult = gitOps.push(forcePush);
            result.append(pushResult);

            result.append("\nSuccess: Committed and pushed ").append(libraries.size()).append(" libraries to remote repository");

            logger.info("Successfully saved and pushed {} libraries", libraries.size());

            return result.toString();
        } catch (GitPushFailedException e) {
            logger.error("Push failed: {}", e.getMessage());
            throw e;
        } catch (GitAPIException e) {
            throw new GitOperationException("Git operation failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new GitOperationException("I/O error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new GitOperationException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Saves multiple libraries to files (no git operations)
     *
     * @param libraries List of libraries to save
     * @return List of file paths for saved libraries
     * @throws IOException if file write fails
     */
    private List<String> saveAll(List<CodeTemplateLibrary> libraries) throws IOException {
        if (libraries == null || libraries.isEmpty()) {
            throw new IllegalArgumentException("Libraries list cannot be null or empty");
        }

        logger.info("Saving {} libraries to repository", libraries.size());

        List<String> paths = new ArrayList<>();

        for (CodeTemplateLibrary library : libraries) {
            String path = save(library);
            paths.add(path);
        }

        logger.info("Successfully saved {} libraries", paths.size());

        return paths;
    }

    /**
     * Builds commit message for batch library save
     */
    private String buildBatchCommitMessage(List<CodeTemplateLibrary> libraries, String userMessage, PersonIdent committer) {
        // Build comma-separated library names
        String libraryNames = buildLibraryNames(libraries);

        // Get server name
        String serverName = ControllerFactory.getFactory().createConfigurationController().getServerName();

        // Create batch object
        CommitMessageUtil.BatchLibraries batchLibraries = new CommitMessageUtil.BatchLibraries(libraryNames);

        // Use CommitMessageUtil to format message
        return CommitMessageUtil.create(batchLibraries, userMessage, serverId, serverName);
    }

    /**
     * Builds comma-separated list of library names.
     * Limits to reasonable length to avoid too-long commit messages.
     *
     * @param libraries List of libraries
     * @return Comma-separated names, truncated if too long
     */
    private String buildLibraryNames(List<CodeTemplateLibrary> libraries) {
        if (libraries.isEmpty()) {
            return "Empty";
        }

        if (libraries.size() == 1) {
            return libraries.get(0).getName();
        }

        StringBuilder names = new StringBuilder();
        int maxLength = 200; // Reasonable limit for commit message

        for (int i = 0; i < libraries.size(); i++) {
            if (i > 0) {
                names.append(", ");
            }

            names.append(libraries.get(i).getName());

            // Check if we're approaching the limit
            if (names.length() > maxLength) {
                int remaining = libraries.size() - i - 1;
                if (remaining > 0) {
                    names.append(", and ").append(remaining).append(" more");
                }
                break;
            }
        }

        return names.toString();
    }
}