package com.innovarhealthcare.channelHistory.client.panel;

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Container;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;

public class VersionHistoryTaskPane {

    private static VersionHistoryTaskPane instance;

    private JXTaskPane taskPane;
    private JPopupMenu popupMenu;

    Runnable diffHandler;
    Runnable commitPushHandler;
    Runnable pullHandler;
    Runnable importHandler;
    Runnable revertHandler;
    Runnable historyHandler;

    // Store task indices
    private int diffTaskIndex = -1;
    private int commitPushTaskIndex = -1;
    private int pullTaskIndex = -1;
    private int importTaskIndex = -1;
    private int revertTaskIndex = -1;
    private int historyTaskIndex = -1;

    // Task action constants
    public static final String TASK_DIFF = "doVersionHistoryDiff";
    public static final String TASK_COMMIT_PUSH = "doVersionHistoryCommitPush";
    public static final String TASK_PULL = "doVersionHistoryPull";
    public static final String TASK_IMPORT = "doVersionHistoryImport";
    public static final String TASK_REVERT = "doVersionHistoryRevert";
    public static final String TASK_HISTORY = "doVersionHistoryHistory";

    /**
     * Private constructor for singleton pattern
     */
    private VersionHistoryTaskPane() {
    }

    /**
     * Gets or creates the singleton instance
     *
     * @return The singleton instance
     */
    public static synchronized VersionHistoryTaskPane getInstance() {
        if (instance == null) {
            instance = new VersionHistoryTaskPane();
        }
        return instance;
    }

    /**
     * Checks if the singleton has been initialized
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Gets the parent Frame instance
     *
     * @return The Frame instance
     */
    private Frame getFrame() {
        return PlatformUI.MIRTH_FRAME;
    }

    /**
     * Creates and initializes the Version History task pane
     *
     * @return The created JXTaskPane
     */
    public JXTaskPane create() {
        if (taskPane != null) {
            return taskPane;
        }

        taskPane = new JXTaskPane();
        popupMenu = new JPopupMenu();

        taskPane.setTitle("Version History");
        taskPane.setName("Version History");
        taskPane.setFocusable(false);

        addTasks();

        return taskPane;
    }

    /**
     * Adds all tasks to the task pane
     */
    private void addTasks() {
        Frame parent = getFrame();

        // Diff task
        diffTaskIndex = parent.addTask(TASK_DIFF, "Diff", "Compare current channel with repository version", "", new ImageIcon(Frame.class.getResource("images/application_view_detail.png")), taskPane, popupMenu, this);

        // Commit & Push task
        commitPushTaskIndex = parent.addTask(TASK_COMMIT_PUSH, "Commit & Push", "Save changes and push to repository", "", new ImageIcon(Frame.class.getResource("images/accept.png")), taskPane, popupMenu, this);

        // Pull task
        pullTaskIndex = parent.addTask(TASK_PULL, "Pull", "Pull latest changes from repository", "", new ImageIcon(Frame.class.getResource("images/arrow_refresh.png")), taskPane, popupMenu, this);

        // Revert task
        revertTaskIndex = parent.addTask(TASK_REVERT, "Revert", "Revert channel to selected version", "", new ImageIcon(Frame.class.getResource("images/arrow_undo.png")), taskPane, popupMenu, this);

        // History task
        historyTaskIndex = parent.addTask(TASK_HISTORY, "History", "View channel version history", "", new ImageIcon(Frame.class.getResource("images/arrow_refresh.png")), taskPane, popupMenu, this);

        // Import from Repo task
        importTaskIndex = parent.addTask(TASK_IMPORT, "Import", "Import channel from repository", "", new ImageIcon(Frame.class.getResource("images/report_go.png")), taskPane, popupMenu, this);
    }

    /**
     * Adds the task pane to the container, positioning it after channelEditTasks
     *
     * @return true if successfully added after channelEditTasks, false if added to end
     */
    public boolean addToContainer() {
        if (taskPane == null) {
            throw new IllegalStateException("Task pane not created. Call create() first.");
        }

        // Don't add if already in container
        if (taskPane.getParent() != null) {
            return true;
        }

        Frame parent = getFrame();
        JXTaskPaneContainer container = parent.taskPaneContainer;
        Component[] components = container.getComponents();

        // Try to insert after channelEditTasks
        for (int i = 0; i < components.length; i++) {
            if (components[i] == parent.channelEditTasks) {
                container.add(taskPane, i + 1);
                return true;
            }
        }

        // Fallback: add to end
        container.add(taskPane);

        return false;
    }

    /**
     * Convenience method to create and add in one call
     *
     * @return The created JXTaskPane
     */
    public JXTaskPane createAndAdd() {
        create();
        addToContainer();
        return taskPane;
    }

    /**
     * Shows the task pane
     */
    public void show() {
        if (taskPane != null) {
            taskPane.setVisible(true);
        }
    }

    /**
     * Hides the task pane
     */
    public void hide() {
        if (taskPane != null) {
            taskPane.setVisible(false);
        }
    }

    /**
     * Checks if the task pane is currently visible
     *
     * @return true if visible
     */
    public boolean isVisible() {
        return taskPane != null && taskPane.isVisible();
    }

    /**
     * Gets the task pane instance
     *
     * @return The JXTaskPane instance
     */
    public JXTaskPane getTaskPane() {
        return taskPane;
    }

    /**
     * Gets the popup menu instance
     *
     * @return The JPopupMenu instance
     */
    public JPopupMenu getPopupMenu() {
        return popupMenu;
    }

    /**
     * Enables or disables all tasks in the pane
     *
     * @param enabled true to enable, false to disable
     */
    public void setTasksEnabled(boolean enabled) {
        if (taskPane != null) {
            Component[] components = taskPane.getContentPane().getComponents();
            for (Component comp : components) {
                comp.setEnabled(enabled);
            }
        }
    }

    // ========== MC Core Callback Methods ==========

    /**
     * Called by MC Core when Diff task is clicked
     * Delegates to the registered diff handler
     */
    public void doVersionHistoryDiff() {
        if (diffHandler != null) {
            diffHandler.run();
        }
    }

    /**
     * Called by MC Core when Commit & Push task is clicked
     * Delegates to the registered commit/push handler
     */
    public void doVersionHistoryCommitPush() {
        if (commitPushHandler != null) {
            commitPushHandler.run();
        }
    }

    /**
     * Called by MC Core when Pull task is clicked
     * Delegates to the registered pull handler
     */
    public void doVersionHistoryPull() {
        if (pullHandler != null) {
            pullHandler.run();
        }
    }

    /**
     * Called by MC Core when Revert task is clicked
     * Delegates to the registered revert handler
     */
    public void doVersionHistoryRevert() {
        if (revertHandler != null) {
            revertHandler.run();
        }
    }

    /**
     * Called by MC Core when History task is clicked
     * Delegates to the registered history handler
     */
    public void doVersionHistoryHistory() {
        if (historyHandler != null) {
            historyHandler.run();
        }
    }

    /**
     * Called by MC Core when Import task is clicked
     * Delegates to the registered import handler
     */
    public void doVersionHistoryImport() {
        if (importHandler != null) {
            importHandler.run();
        }
    }

    // ========== Handler Setters ==========

    /**
     * Set handler for Diff action
     */
    public void setDiffHandler(Runnable handler) {
        this.diffHandler = handler;
    }

    /**
     * Set handler for Commit & Push action
     */
    public void setCommitPushHandler(Runnable handler) {
        this.commitPushHandler = handler;
    }

    /**
     * Set handler for Pull action
     */
    public void setPullHandler(Runnable handler) {
        this.pullHandler = handler;
    }

    /**
     * Set handler for Revert action
     */
    public void setRevertHandler(Runnable handler) {
        this.revertHandler = handler;
    }

    /**
     * Set handler for History action
     */
    public void setHistoryHandler(Runnable handler) {
        this.historyHandler = handler;
    }

    /**
     * Set handler for Import action
     */
    public void setImportHandler(Runnable handler) {
        this.importHandler = handler;
    }

    /**
     * Clear all handlers
     */
    public void clearHandlers() {
        this.diffHandler = null;
        this.commitPushHandler = null;
        this.pullHandler = null;
        this.revertHandler = null;
        this.historyHandler = null;
        this.importHandler = null;
    }

    // ========== View-based visibility methods ==========

    /**
     * Shows tasks for Channel Panel view (viewing channels list)
     * Only shows Import task - allows importing channels from repository
     *
     * @param importHandler Handler for import action
     */
    public void showForChannelPanel(Runnable importHandler) {
        if (importHandler == null) {
            throw new IllegalArgumentException("Import handler cannot be null");
        }

        // Set the import handler
        this.importHandler = importHandler;

        // Hide all tasks first
        setTaskVisible(diffTaskIndex, false);
        setTaskVisible(commitPushTaskIndex, false);
        setTaskVisible(pullTaskIndex, false);
        setTaskVisible(revertTaskIndex, false);
        setTaskVisible(historyTaskIndex, false);

        // Show only Import
        setTaskVisible(importTaskIndex, true);
    }

    /**
     * Shows tasks for Channel Edit view (editing a channel)
     * Shows Diff, Commit & Push, Pull, Revert, and History tasks for channel version control
     *
     * @param diffHandler       Handler for diff action
     * @param commitPushHandler Handler for commit & push action
     * @param pullHandler       Handler for pull action
     * @param revertHandler     Handler for revert action
     */
    public void showForChannelEdit(Runnable diffHandler, Runnable commitPushHandler, Runnable pullHandler, Runnable revertHandler) {
        if (diffHandler == null || commitPushHandler == null || pullHandler == null || revertHandler == null) {
            throw new IllegalArgumentException("All handlers must be non-null");
        }

        // Set all handlers
        this.diffHandler = diffHandler;
        this.commitPushHandler = commitPushHandler;
        this.pullHandler = pullHandler;
        this.revertHandler = revertHandler;

        // Show Diff, Commit & Push, Pull, Revert, History
        setTaskVisible(diffTaskIndex, true);
        setTaskVisible(commitPushTaskIndex, true);
        setTaskVisible(pullTaskIndex, true);
        setTaskVisible(revertTaskIndex, true);

        // Hide Import
        setTaskVisible(historyTaskIndex, false);
        setTaskVisible(importTaskIndex, false);
    }

    /**
     * Shows tasks for Code Template Edit view (editing code templates)
     * Shows History and Import tasks for code template version control
     *
     * @param historyHandler Handler for history action
     * @param importHandler  Handler for import action
     */
    public void showForCodeTemplateEdit(Runnable historyHandler, Runnable importHandler) {
        if (historyHandler == null || importHandler == null) {
            throw new IllegalArgumentException("All handlers must be non-null");
        }

        // Set handlers
        this.historyHandler = historyHandler;
        this.importHandler = importHandler;

        // Hide most tasks
        setTaskVisible(diffTaskIndex, false);
        setTaskVisible(commitPushTaskIndex, false);
        setTaskVisible(pullTaskIndex, false);
        setTaskVisible(revertTaskIndex, false);

        // Show History and Import
        setTaskVisible(historyTaskIndex, true);
        setTaskVisible(importTaskIndex, true);

        show();
    }

    /**
     * Helper to set visibility of a single task
     *
     * @param taskIndex The index of the task
     * @param visible   True to show, false to hide
     */
    private void setTaskVisible(int taskIndex, boolean visible) {
        if (taskIndex == -1) {
            return;
        }
        Frame parent = getFrame();
        parent.setVisibleTasks(taskPane, popupMenu, taskIndex, taskIndex, visible);
    }

    /**
     * Resets the singleton instance (cleanup)
     * Should be called when plugin is stopped
     */
    public static synchronized void reset() {
        if (instance != null) {
            instance.remove();
            instance = null;
        }
    }

    /**
     * Removes the task pane from its container
     */
    public void remove() {
        if (taskPane != null && taskPane.getParent() != null) {
            Container parent = taskPane.getParent();
            parent.remove(taskPane);
            parent.revalidate();
            parent.repaint();
        }
    }
}
