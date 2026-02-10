package com.innovarhealthcare.channelHistory.client.taskpanel;

import java.util.Set;

import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_HISTORY;
import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_IMPORT;
import static com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane.TASK_SAVE_LIBRARIES;

/**
 * Context for Code Template Edit view.
 * Shows: History, Import, Save Libraries tasks
 */
public class CodeTemplateEditContext extends BaseTaskPaneContext {

    private final CodeTemplateOperations operations;

    public CodeTemplateEditContext(CodeTemplateOperations operations) {
        this.operations = operations;
    }

    @Override
    public void onHistory() {
        operations.showHistory();
    }

    @Override
    public void onImport() {
        operations.importTemplate();
    }

    @Override
    public void onSaveLibraries() {
        operations.saveLibraries();
    }

    @Override
    public Set<String> getVisibleTasks() {
        return Set.of(TASK_HISTORY, TASK_IMPORT, TASK_SAVE_LIBRARIES);
    }
}