package com.innovarhealthcare.channelHistory.client.taskpanel;

import com.innovarhealthcare.channelHistory.client.dialog.CommitPushGlobalScriptsDialog;
import com.innovarhealthcare.channelHistory.client.dialog.GlobalScriptsHistoryDialog;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;

/**
 * Operations for Global Script history management in the left task panel.
 * Provides actions for diff, commit/push, and history viewing.
 */
public class GlobalScriptOperations {

    private final Frame parent;

    public GlobalScriptOperations(Frame parent) {
        this.parent = parent;
    }

    /**
     * Shows global script template history dialog
     */
    public void showHistory() {
//        if (parent.isSaveEnabled()) {
//            parent.alertWarning(parent, "Please save your changes before viewing history.");
//            return;
//        }

        new GlobalScriptsHistoryDialog(parent);
    }

    /**
     * Shows diff window comparing current MC global scripts with latest Git version.
     * Displays tree view with expandable nodes for each script type (Deploy, Undeploy, Preprocessor, Postprocessor).
     */
    public void showDiff() {
        PlatformUI.MIRTH_FRAME.alertInformation(parent, "Diff functionality coming soon - will show tree comparison between two versions");
    }

    /**
     * Commits current MC global scripts and pushes to Git repository.
     * Saves all 4 script types as single commit to maintain MC behavior consistency.
     */
    public void commitAndPush() {
        if (parent.isSaveEnabled()) {
            parent.alertWarning(parent, "Please save your changes before commit global scripts to remote repository.");
            return;
        }

        new CommitPushGlobalScriptsDialog(parent);
    }


}
