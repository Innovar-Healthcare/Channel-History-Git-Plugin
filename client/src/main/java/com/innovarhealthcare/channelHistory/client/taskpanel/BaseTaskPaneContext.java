package com.innovarhealthcare.channelHistory.client.taskpanel;

import java.util.Set;

/**
 * Base implementation providing no-op defaults.
 * Concrete contexts only override methods they need.
 */
public abstract class BaseTaskPaneContext implements TaskPaneContext {

    @Override
    public void onDiff() {
        // No-op by default
    }

    @Override
    public void onCommitPush() {
        // No-op by default
    }

    @Override
    public void onPull() {
        // No-op by default
    }

    @Override
    public void onRevert() {
        // No-op by default
    }

    @Override
    public void onHistory() {
        // No-op by default
    }

    @Override
    public void onImport() {
        // No-op by default
    }

    @Override
    public void onSaveLibraries() {
        // No-op by default
    }

    @Override
    public abstract Set<String> getVisibleTasks();
}