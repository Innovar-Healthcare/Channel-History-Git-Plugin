package com.innovarhealthcare.channelHistory.client.taskpanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.innovarhealthcare.channelHistory.client.dialog.CodeTemplateHistoryDialogWithTaskPane;
import com.innovarhealthcare.channelHistory.client.dialog.ImportCodeTemplateDialog;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;

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
        Map<String, CodeTemplateLibrary> codeTemplateLibraries = parent.codeTemplatePanel.getCachedCodeTemplateLibraries();
        List<CodeTemplateLibrary> libraries = new ArrayList<>(codeTemplateLibraries.values());

        // TODO: Implement actual save logic
        parent.alertError(parent, "Not implemented yet");
    }
}