package com.innovarhealthcare.channelHistory.client.taskpanel;

import com.innovarhealthcare.channelHistory.client.dialog.CodeTemplateHistoryDialogWithTaskPane;
import com.innovarhealthcare.channelHistory.client.dialog.ImportCodeTemplateDialog;
import com.innovarhealthcare.channelHistory.client.dialog.SaveLibrariesDialog;
import com.mirth.connect.client.ui.Frame;

/**
 * Business operations for Code Template context.
 * Encapsulates logic extracted from plugin.
 */
public class CodeTemplateOperations {

    private final Frame parent;

    public CodeTemplateOperations(Frame parent) {
        this.parent = parent;
    }

    /**
     * Shows code template history dialog
     */
    public void showHistory() {
        if (parent.isSaveEnabled()) {
            parent.alertWarning(parent, "Please save your changes before viewing history.");
            return;
        }

        String codeTemplateId = parent.codeTemplatePanel.getCurrentSelectedId();

        if (codeTemplateId == null) {
            parent.alertError(parent, "No library/code template selected.");
            return;
        }

        new CodeTemplateHistoryDialogWithTaskPane(parent, codeTemplateId);
    }

    /**
     * Shows import code template dialog
     */
    public void importTemplate() {
        if (parent.isSaveEnabled()) {
            parent.alertWarning(parent, "Please save your changes before importing.");
            return;
        }

        new ImportCodeTemplateDialog(parent);
    }

    /**
     * Saves libraries to repository
     */
    public void saveLibraries() {
        // Validate - ensure no unsaved changes
        if (parent.isSaveEnabled()) {
            parent.alertWarning(parent, "Please save your changes before commit libraries to remote repository.");
            return;
        }

        // Show dialog - it handles everything (input, processing, result)
        new SaveLibrariesDialog(parent);
    }
}