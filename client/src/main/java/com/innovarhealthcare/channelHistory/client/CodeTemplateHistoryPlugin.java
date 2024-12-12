package com.innovarhealthcare.channelHistory.client;


import com.innovarhealthcare.channelHistory.client.util.VersionControlUtil;
import com.kaurpalang.mirth.annotationsplugin.annotation.MirthClientClass;

import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.plugins.ClientPlugin;

import org.jdesktop.swingx.action.ActionFactory;
import org.jdesktop.swingx.action.BoundAction;

import javax.swing.*;
import java.util.Collections;

/**
 * @author Jim(Zi Min) Weng
 * @create 2024-05-02 9:02 AM
 */
@MirthClientClass
public class CodeTemplateHistoryPlugin extends ClientPlugin {
    public CodeTemplateHistoryPlugin(String pluginName) {
        super(pluginName);
    }

    @Override
    public String getPluginPointName() {
        return null;
    }

    @Override
    public void start() {
        addViewHistoryAction();

        addImportCodeTemplateAction();
    }

    public void doViewCodeTemplateHistory() {
        if (VersionControlUtil.isDisableVersionControl(parent.mirthClient)) {
            PlatformUI.MIRTH_FRAME.alertError(parent, VersionControlUtil.getAlertText());
        } else {
            if (!this.parent.codeTemplatePanel.changesHaveBeenMade() || this.parent.codeTemplatePanel.promptSave(true)) {
                String codeTemplateId;
                if ((codeTemplateId = this.parent.codeTemplatePanel.getCurrentSelectedId()) != null) {
                    new CodeTemplateHistoryDialog(parent, codeTemplateId);
                }

            }
        }
    }

    @Override
    public void stop() {

    }

    @Override
    public void reset() {

    }

    public void importCodeTemplateFromRepo() {
        if (VersionControlUtil.isDisableVersionControl(parent.mirthClient)) {
            PlatformUI.MIRTH_FRAME.alertError(parent, VersionControlUtil.getAlertText());
        } else {
            new ImportCodeTemplateDialog(parent);
        }
    }

    private void addViewHistoryAction() {
        String callback = "doViewCodeTemplateHistory";
        String taskName = "View History";
        String description = "View the previous versions of this code template.";

        ImageIcon img = new ImageIcon(com.mirth.connect.client.ui.Frame.class.getResource("images/wrench.png"));

        BoundAction action;
        (action = ActionFactory.createBoundAction(callback, taskName, "")).putValue("SmallIcon", img);
        action.putValue("ShortDescription", description);
        action.registerCallback(this, callback);
        parent.codeTemplatePanel.addAction(action, Collections.singleton("onlySingleCodeTemplates"), callback);
    }

    private void addImportCodeTemplateAction() {
        String callback = "importCodeTemplateFromRepo";
        String taskName = "Import Code Template From Repo";
        String description = "Import list of code templates from repo.";

        ImageIcon img = new ImageIcon(com.mirth.connect.client.ui.Frame.class.getResource("images/report_go.png"));

        BoundAction action;
        action = ActionFactory.createBoundAction(callback, taskName, "");
        action.putValue("SmallIcon", img);
        action.putValue("ShortDescription", description);
        action.registerCallback(this, callback);

        parent.codeTemplatePanel.addAction(action, Collections.singleton("onlySingleLibraries"), callback);
    }
}