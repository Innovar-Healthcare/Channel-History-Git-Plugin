package com.innovarhealthcare.channelHistory.server.service;

import java.io.File;
import java.io.IOException;

import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.shared.model.GitSettings;
import org.apache.commons.io.FileUtils;
import com.innovarhealthcare.channelHistory.server.file.FileOperations;
import com.innovarhealthcare.channelHistory.server.git.GitOperations;
import com.innovarhealthcare.channelHistory.server.repository.ChannelRepository;
import com.innovarhealthcare.channelHistory.server.repository.CodeTemplateRepository;
import com.innovarhealthcare.channelHistory.server.repository.GlobalScriptRepository;
import com.innovarhealthcare.channelHistory.server.repository.LibraryRepository;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.controllers.ControllerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;

/**
 * Git Repository Infrastructure Service
 * <p>
 * Responsibilities:
 * - Manage Git connection and lifecycle
 * - Initialize repository (clone or open + pull)
 * - Configure SSH authentication
 * - Provide GitOperations and FileOperations
 * - Provide Repository instances (factory)
 * - Track Git availability status
 * <p>
 * Lifecycle:
 * 1. Create: new GitRepositoryService()
 * 2. Start: startGit(properties)
 * 3. Check: isGitAvailable()
 * 4. Use: getLibraryRepository(), getChannelRepository(), etc.
 * 5. Stop: stopGit()
 * <p>
 * Git Availability:
 * - If Git initialization fails, service continues running
 * - gitAvailable flag tracks whether Git is working
 * - Users see error when trying to use Git features
 * - Check getGitStatus() for current state
 * <p>
 * Thread Safety: All public methods are synchronized
 */
public class GitRepositoryService {

    private static final Logger logger = LogManager.getLogger(GitRepositoryService.class);
    private static final String DATA_DIR = "InnovarHealthcare-version-control";
    private static final String SSH_KEY_IDENTITY_NAME = "version-history-ssh-key";

    // ========== State ==========
    private boolean started;
    private boolean gitAvailable;
    private String gitUnavailableReason;

    // ========== Configuration ==========
    // Hold reference
    private VersionHistoryProperties versionHistoryProperties;

    private File repositoryDirectory;
    private String serverId;
    private ObjectXMLSerializer serializer;

    // ========== Infrastructure Components ==========
    private Git git;
    private SshSessionFactory sshSessionFactory;
    private GitOperations gitOperations;
    private FileOperations fileOperations;

    /**
     * Creates a new GitRepositoryService.
     * Must call startGit() before using.
     */
    public GitRepositoryService(VersionHistoryProperties versionHistoryProperties) {
        this.started = false;
        this.gitAvailable = false;
        this.gitUnavailableReason = "Git not initialized";
        this.versionHistoryProperties = versionHistoryProperties;
    }

    // ========== Lifecycle Methods ==========

    /**
     * Starts Git infrastructure with configuration.
     * Does NOT throw exception if Git unavailable - sets flag instead.
     *
     * @throws Exception only for critical initialization errors (not Git-specific)
     */
    public synchronized void startGit() throws Exception {
        logger.info("Starting Git infrastructure...");

        // Check if enabled
        if (!this.versionHistoryProperties.isEnableVersionHistory()) {
            logger.warn("Version history is disabled in configuration");
            this.gitAvailable = false;
            this.gitUnavailableReason = "Version history is disabled";
            this.started = true;
            return;
        }

        try {
            // Initialize components
            initializeBasicComponents();
            validateConfiguration();
            createSshSessionFactory();

            // Try to initialize Git repository
            initializeGitRepository();

            // Create operations
            createOperations();

            // Success!
            this.gitAvailable = true;
            this.gitUnavailableReason = null;
            this.started = true;

            logger.info("Git infrastructure started successfully");
            logger.info("Git is AVAILABLE and ready to use");

        } catch (Exception e) {
            // Git initialization failed - don't throw, just mark as unavailable
            logger.error("═══════════════════════════════════════════════════════");
            logger.error("Failed to initialize Git infrastructure: {}", e.getMessage());
            logger.error("Git features will be UNAVAILABLE");
            logger.error("Users will see error when trying to save/commit");
            logger.error("═══════════════════════════════════════════════════════");

            this.gitAvailable = false;
            this.gitUnavailableReason = e.getMessage();

            // Cleanup partial initialization
            cleanup();

            // Mark as started (but Git unavailable)
            this.started = true;
        }
    }

    /**
     * Stops Git infrastructure and releases resources.
     */
    public synchronized void stopGit() {
        logger.info("Stopping Git infrastructure...");

        cleanup();
        started = false;
        gitAvailable = false;
        gitUnavailableReason = "Git stopped";

        logger.info("Git infrastructure stopped");
    }

    /**
     * Checks if service is started.
     *
     * @return true if started
     */
    public boolean isStarted() {
        return started;
    }

    // ========== Git Status Methods ==========

    /**
     * Checks if Git is available and working.
     *
     * @return true if Git is available
     */
    public boolean isGitAvailable() {
        return gitAvailable;
    }

    /**
     * Gets the reason why Git is unavailable (if it is).
     *
     * @return reason string, or null if Git is available
     */
    public String getGitUnavailableReason() {
        return gitUnavailableReason;
    }

    /**
     * Gets Git status for display to user.
     *
     * @return GitStatus object with availability and message
     */
    public GitStatus getGitStatus() {
        if (!started) {
            return new GitStatus(false, "Service not started");
        }
        if (!gitAvailable) {
            return new GitStatus(false, gitUnavailableReason);
        }
        return new GitStatus(true, "Git is available and working");
    }

    // ========== Repository Factory Methods ==========

    /**
     * Gets LibraryRepository instance.
     *
     * @return LibraryRepository instance
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     */
    public LibraryRepository getLibraryRepository() {
        ensureStarted();
        ensureGitAvailable();
        return new LibraryRepository(gitOperations, fileOperations, serverId);
    }

    // Future repositories:
    public ChannelRepository getChannelRepository() {
        ensureStarted();
        ensureGitAvailable();
        return new ChannelRepository(gitOperations, fileOperations, serverId);
    }

    public CodeTemplateRepository getCodeTemplateRepository() {
        ensureStarted();
        ensureGitAvailable();
        return new CodeTemplateRepository(gitOperations, fileOperations, serverId);
    }

    public GlobalScriptRepository getGlobalScriptRepository() {
        ensureStarted();
        ensureGitAvailable();
        return new GlobalScriptRepository(gitOperations, fileOperations, serverId);
    }

    // ========== Direct Access Methods ==========

    /**
     * Gets GitOperations instance.
     *
     * @return GitOperations instance
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     */
    public GitOperations getGitOperations() {
        ensureStarted();
        ensureGitAvailable();
        return gitOperations;
    }

    /**
     * Gets FileOperations instance.
     *
     * @return FileOperations instance
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     */
    public FileOperations getFileOperations() {
        ensureStarted();
        ensureGitAvailable();
        return fileOperations;
    }

    // ========== Connection Validation ==========

    /**
     * Validates the SSH (or default) connection to the remote repository described by gitSettings.
     * Clones to a temporary directory with no checkout, then deletes the temp dir.
     * Never throws — returns null on success or an error message string on failure.
     *
     * @param gitSettings Settings to validate (URL, branch, SSH key or key path)
     * @return null on success; error message on failure
     */
    public String validateSSHConnection(GitSettings gitSettings) {
        if (gitSettings == null) {
            return "Git settings cannot be null";
        }

        String remoteUrl = gitSettings.getRemoteRepositoryUrl();
        String branch = gitSettings.getBranchName();

        if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
            return "Remote repository URL is not configured";
        }
        if (branch == null || branch.trim().isEmpty()) {
            return "Branch name is not configured";
        }

        File tempDir = null;
        Git tempGit = null;
        try {
            SshSessionFactory tempFactory = buildSshSessionFactory(gitSettings);
            tempDir = new File(Donkey.getInstance().getConfiguration().getAppData(), "version-control-validate-" + System.currentTimeMillis());

            tempGit = Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(tempDir)
                    .setBranch(branch)
                    .setNoCheckout(true)
                    .setTransportConfigCallback(transport -> {
                        if (transport instanceof SshTransport) {
                            ((SshTransport) transport).setSshSessionFactory(tempFactory);
                        }
                    })
                    .call();

            logger.info("Connection validation succeeded for: {}", remoteUrl);
            return null;

        } catch (Exception e) {
            logger.warn("Connection validation failed: {}", e.getMessage());
            return e.getMessage() != null ? e.getMessage() : "Unknown error during connection validation";

        } finally {
            if (tempGit != null) {
                tempGit.close();
            }
            if (tempDir != null && tempDir.exists()) {
                try {
                    FileUtils.deleteDirectory(tempDir);
                } catch (IOException deleteEx) {
                    logger.warn("Failed to delete temp validation directory: {}", tempDir.getAbsolutePath());
                }
            }
        }
    }

    // ========== Private Initialization Methods ==========

    /**
     * Initializes basic components (serializer, directories, server ID).
     */
    private void initializeBasicComponents() throws Exception {
        logger.debug("Initializing basic components...");

        // Get serializer
        serializer = ObjectXMLSerializer.getInstance();

        // Get server ID
        serverId = ControllerFactory.getFactory().createConfigurationController().getServerId();

        // Setup repository directory
        repositoryDirectory = new File(Donkey.getInstance().getConfiguration().getAppData(), DATA_DIR);

        logger.debug("Repository directory: {}", repositoryDirectory.getAbsolutePath());
        logger.debug("Server ID: {}", serverId);
    }

    /**
     * Validates configuration from properties.
     */
    private void validateConfiguration() {
        logger.debug("Validating configuration...");

        if (versionHistoryProperties.getGitSettings() == null) {
            throw new IllegalStateException("Git settings not configured");
        }

        String remoteUrl = versionHistoryProperties.getGitSettings().getRemoteRepositoryUrl();
        if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
            throw new IllegalStateException("Remote repository URL is not configured");
        }

        String branch = versionHistoryProperties.getGitSettings().getBranchName();
        if (branch == null || branch.trim().isEmpty()) {
            throw new IllegalStateException("Branch name is not configured");
        }

        logger.debug("Configuration validated successfully");
    }

    /**
     * Creates SSH session factory from configuration.
     */
    private void createSshSessionFactory() {
        logger.debug("Creating SSH session factory...");

        final String sshPrivateKey = versionHistoryProperties.getGitSettings().getSshPrivateKey();

        if (sshPrivateKey == null || sshPrivateKey.trim().isEmpty()) {
            logger.warn("No SSH private key configured, using default");
            sshSessionFactory = SshSessionFactory.getInstance();
            return;
        }

        sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host hc, Session session) {
                // Disable strict host key checking
                // In production, consider enabling with known_hosts management
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);

                try {
                    // Add SSH private key
                    byte[] keyBytes = sshPrivateKey.getBytes();
                    defaultJSch.addIdentity(SSH_KEY_IDENTITY_NAME, keyBytes, null, null);
                    logger.debug("SSH private key added as: {}", SSH_KEY_IDENTITY_NAME);
                } catch (JSchException e) {
                    logger.error("Failed to add SSH private key", e);
                    throw e;
                }

                return defaultJSch;
            }
        };

        logger.debug("SSH session factory created successfully");
    }

    /**
     * Builds a standalone SshSessionFactory from the given GitSettings.
     * Supports both inline key content (bytes) and file-path key.
     * Used by validateSSHConnection() so it can work independently of the live sshSessionFactory.
     */
    private SshSessionFactory buildSshSessionFactory(GitSettings gitSettings) {
        final String sshPrivateKey = gitSettings.getSshPrivateKey();
        final String sshPrivateKeyPath = gitSettings.getSshPrivateKeyPath();

        boolean hasInlineKey = sshPrivateKey != null && !sshPrivateKey.trim().isEmpty();
        boolean hasKeyPath = sshPrivateKeyPath != null && !sshPrivateKeyPath.trim().isEmpty();

        if (!hasInlineKey && !hasKeyPath) {
            logger.warn("No SSH private key configured for validation, using default session factory");
            return SshSessionFactory.getInstance();
        }

        return new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host hc, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch jsch = super.createDefaultJSch(fs);
                try {
                    if (hasInlineKey) {
                        jsch.addIdentity(SSH_KEY_IDENTITY_NAME, sshPrivateKey.getBytes(), null, null);
                        logger.debug("SSH private key added from inline content");
                    } else {
                        jsch.addIdentity(sshPrivateKeyPath.trim());
                        logger.debug("SSH private key loaded from path: {}", sshPrivateKeyPath);
                    }
                } catch (JSchException e) {
                    logger.error("Failed to add SSH private key", e);
                    throw e;
                }
                return jsch;
            }
        };
    }

    /**
     * Initializes or clones Git repository.
     * Throws exception if fails - will be caught by startGit().
     * <p>
     * Flow:
     * 1. Check if .git directory exists
     * 2. If exists: Open and pull latest changes
     * 3. If not exists: Clone from remote
     */
    private void initializeGitRepository() throws Exception {
        logger.debug("Initializing git repository...");

        // Create directory if needed
        if (!repositoryDirectory.exists()) {
            if (!repositoryDirectory.mkdirs()) {
                throw new IOException("Failed to create directory: " + repositoryDirectory.getAbsolutePath());
            }
            logger.info("Created repository directory: {}", repositoryDirectory.getAbsolutePath());
        }

        File gitDir = new File(repositoryDirectory, ".git");

        if (gitDir.exists()) {
            // Repository exists - open and pull
            openAndUpdate();
        } else {
            // No repository - clone from remote
            cloneFromRemote();
        }

        logger.info("Git repository initialized successfully");
    }

    /**
     * Opens existing repository and pulls latest changes.
     */
    private void openAndUpdate() throws Exception {
        logger.info("Opening existing repository...");

        git = Git.open(repositoryDirectory);
        logger.info("Repository opened at: {}", repositoryDirectory.getAbsolutePath());

        // Try to pull latest changes (non-fatal if fails)
        pullLatestChanges();
    }

    /**
     * Clones repository from remote.
     * Throws exception if clone fails.
     */
    private void cloneFromRemote() throws Exception {
        logger.info("No local repository found, cloning from remote...");

        String remoteUrl = versionHistoryProperties.getGitSettings().getRemoteRepositoryUrl();
        String branch = versionHistoryProperties.getGitSettings().getBranchName();

        logger.info("Cloning from: {}", remoteUrl);
        logger.info("Branch: {}", branch);

        git = Git.cloneRepository().setURI(remoteUrl).setDirectory(repositoryDirectory).setBranch(branch).setTransportConfigCallback(this::configureSsh).call();

        logger.info("Successfully cloned repository from: {}", remoteUrl);
    }

    /**
     * Pulls latest changes from remote.
     * Non-fatal: logs warning if fails but doesn't throw.
     */
    private void pullLatestChanges() {
        logger.info("Pulling latest changes from remote...");

        try {
            PullResult result = git.pull().setRemote("origin").setRemoteBranchName(versionHistoryProperties.getGitSettings().getBranchName()).setTransportConfigCallback(this::configureSsh).call();

            if (result.isSuccessful()) {
                logger.info("Successfully pulled latest changes");
            } else {
                logger.warn("Pull completed with issues: {}", result.getMergeResult());
            }

        } catch (Exception e) {
            logger.warn("Failed to pull from remote: {}", e.getMessage());
            logger.warn("Continuing with local repository");
            // Don't throw - can work with local repo
        }
    }

    /**
     * Creates operations instances.
     */
    private void createOperations() {
        logger.debug("Creating operations...");

        gitOperations = new GitOperations(git, versionHistoryProperties.getGitSettings().getBranchName(), sshSessionFactory);

        fileOperations = new FileOperations(repositoryDirectory, serializer);

        logger.debug("Operations created successfully");
    }

    /**
     * Cleans up resources.
     */
    private void cleanup() {
        if (git != null) {
            git.close();
            git = null;
        }

        gitOperations = null;
        fileOperations = null;
        sshSessionFactory = null;
    }

    /**
     * Ensures service is started before operations.
     *
     * @throws IllegalStateException if service not started
     */
    private void ensureStarted() {
        if (!started) {
            throw new IllegalStateException("GitRepositoryService is not started. Call startGit() first.");
        }
    }

    /**
     * Ensures Git is available before operations.
     *
     * @throws GitNotConnectedException if Git is not available
     */
    private void ensureGitAvailable() {
        if (!gitAvailable) {
            throw new GitNotConnectedException("Git repository is not connected. " + gitUnavailableReason);
        }
    }

    /**
     * Configures SSH for transport.
     *
     * @param transport Git transport
     */
    private void configureSsh(Transport transport) {
        if (transport instanceof SshTransport) {
            ((SshTransport) transport).setSshSessionFactory(sshSessionFactory);
        }
    }

    // ========== Status Classes ==========

    /**
     * Git status information for display/API
     */
    public static class GitStatus {
        private final boolean available;
        private final String message;

        public GitStatus(boolean available, String message) {
            this.available = available;
            this.message = message;
        }

        public boolean isAvailable() {
            return available;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "GitStatus{available=" + available + ", message='" + message + "'}";
        }
    }
}
