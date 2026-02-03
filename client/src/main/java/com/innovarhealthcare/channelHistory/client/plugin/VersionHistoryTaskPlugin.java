package com.innovarhealthcare.channelHistory.client.plugin;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import com.innovarhealthcare.channelHistory.client.dialog.CodeTemplateHistoryDialogWithTaskPane;
import com.innovarhealthcare.channelHistory.client.dialog.ImportChannelDialog;
import com.innovarhealthcare.channelHistory.client.dialog.ImportCodeTemplateDialog;
import com.innovarhealthcare.channelHistory.client.panel.VersionHistoryTaskPane;
import com.kaurpalang.mirth.annotationsplugin.annotation.MirthClientClass;
import com.mirth.connect.client.core.TaskConstants;
import com.mirth.connect.client.ui.components.MirthTreeTable;
import com.mirth.connect.plugins.TaskPlugin;
import org.jdesktop.swingx.JXTaskPane;

@MirthClientClass
public class VersionHistoryTaskPlugin extends TaskPlugin {
    public VersionHistoryTaskPlugin(String name) {
        super(name);
    }

    @Override
    public String getPluginPointName() {
        return "";
    }

    @Override
    public void start() {
        VersionHistoryTaskPane.getInstance().createAndAdd();

        SwingUtilities.invokeLater(this::setupAllTaskPaneListeners);
    }

    @Override
    public void stop() {

    }

    @Override
    public void reset() {

    }

    public void onRowSelected(MirthTreeTable channelTable) {

    }

    public void onRowDeselected() {

    }

    public JXTaskPane getTaskPane() {
        VersionHistoryTaskPane.getInstance().showForChannelPanel(() -> {
            new ImportChannelDialog(parent);
        });

        return VersionHistoryTaskPane.getInstance().getTaskPane();
    }

    /**
     * Sets up component listeners for task panes to detect view changes
     * and manage Version History task visibility accordingly.
     */
    private void setupAllTaskPaneListeners() {
        JXTaskPane codeTemplateTasks = null;
        JXTaskPane globalScriptTasks = null;

        // Find relevant task panes by name
        for (Component comp : parent.taskPaneContainer.getComponents()) {
            if (!(comp instanceof JXTaskPane)) {
                continue;
            }

            JXTaskPane pane = (JXTaskPane) comp;
            String name = pane.getName();

            if (TaskConstants.CODE_TEMPLATE_KEY.equals(name)) {
                codeTemplateTasks = pane;
            } else if (TaskConstants.GLOBAL_SCRIPT_KEY.equals(name)) {
                globalScriptTasks = pane;
            }

            // Break early if found both
            if (codeTemplateTasks != null && globalScriptTasks != null) {
                break;
            }
        }

        // Setup Code Template task pane listener
        if (codeTemplateTasks != null) {
            setupCodeTemplateListener(codeTemplateTasks);
        }

        // Setup Global Script task pane listener
        if (globalScriptTasks != null) {
            setupGlobalScriptListener(globalScriptTasks);
        }
    }

    /**
     * Sets up listener for Code Template task pane
     */
    private void setupCodeTemplateListener(JXTaskPane taskPane) {
        taskPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                System.out.println("Code Template view shown");
                VersionHistoryTaskPane.getInstance().showForCodeTemplateEdit(VersionHistoryTaskPlugin.this::showCodeTemplateHistory, VersionHistoryTaskPlugin.this::importCodeTemplate);
            }
        });
    }

    /**
     * Sets up listener for Global Script task pane
     */
    private void setupGlobalScriptListener(JXTaskPane taskPane) {
        taskPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                System.out.println("Global Script view shown");
            }
        });
    }

    /**
     * Shows code template history dialog
     */
    private void showCodeTemplateHistory() {
        // Check if there are unsaved changes
        if (!parent.codeTemplatePanel.changesHaveBeenMade() || parent.codeTemplatePanel.promptSave(true)) {
            String codeTemplateId = parent.codeTemplatePanel.getCurrentSelectedId();

            if (codeTemplateId != null) {
//                new CodeTemplateHistoryDialog(parent, codeTemplateId);
                new CodeTemplateHistoryDialogWithTaskPane(parent, codeTemplateId);
            } else {
                parent.alertError(parent, "No library/code template selected");
            }
        }
    }

    /**
     * Shows import code template dialog
     */
    private void importCodeTemplate() {
        new ImportCodeTemplateDialog(parent);
    }
}
