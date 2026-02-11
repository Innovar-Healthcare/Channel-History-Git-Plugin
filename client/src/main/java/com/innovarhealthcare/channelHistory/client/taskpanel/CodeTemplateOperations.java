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
        // Check if there are unsaved changes
        if (!parent.codeTemplatePanel.changesHaveBeenMade() || parent.codeTemplatePanel.promptSave(true)) {

            String codeTemplateId = parent.codeTemplatePanel.getCurrentSelectedId();

            if (codeTemplateId != null) {
                new CodeTemplateHistoryDialogWithTaskPane(parent, codeTemplateId);
            } else {
                parent.alertError(parent, "No library/code template selected");
            }
        }
    }

    /**
     * Shows import code template dialog
     */
    public void importTemplate() {
        new ImportCodeTemplateDialog(parent);
    }

    /**
     * Saves libraries to repository
     */
    public void saveLibraries() {
        // Validate - ensure no unsaved changes
        if (parent.isSaveEnabled()) {
            parent.alertWarning(parent, "The libraries/code templates have been modified.\n You must save them before you can commit to remote repository.");
            return;
        }

        // Show dialog - it handles everything (input, processing, result)
        new SaveLibrariesDialog(parent);
    }
}