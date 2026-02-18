package com.innovarhealthcare.channelHistory.client.taskpanel;

import java.util.Set;

import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_COMMIT_PUSH;
import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_DIFF;
import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_HISTORY;

/**
 * Context for Global Script history operations in the left task panel.
 * Extends BaseTaskPaneContext to provide diff, commit/push, and history actions.
 */
public class GlobalScriptEditContext extends BaseTaskPaneContext {

    private final GlobalScriptOperations operations;

    /**
     * Creates context with global script history operations
     *
     * @param operations Operations to perform when tasks are clicked
     */
    public GlobalScriptEditContext(GlobalScriptOperations operations) {
        this.operations = operations;
    }

    /**
     * Handles diff task click - shows current MC scripts vs Git comparison
     */
    @Override
    public void onDiff() {
        operations.showDiff();
    }

    /**
     * Handles commit/push task click - saves current MC scripts to Git
     */
    @Override
    public void onCommitPush() {
        operations.commitAndPush();
    }

    /**
     * Handles history task click - opens history dialog with commit list
     */
    @Override
    public void onHistory() {
        operations.showHistory();
    }

    /**
     * Defines which tasks are visible in the left panel for Global Scripts
     * Order: Diff → Commit & Push → History
     *
     * @return Set of visible task names
     */
    @Override
    public Set<String> getVisibleTasks() {
        return Set.of(TASK_DIFF, TASK_COMMIT_PUSH, TASK_HISTORY);
    }
}
