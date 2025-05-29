package com.innovarhealthcare.channelHistory.client;


import com.innovarhealthcare.channelHistory.client.dialog.CodeTemplateHistoryDialog;
import com.innovarhealthcare.channelHistory.client.dialog.ImportCodeTemplateDialog;
import com.innovarhealthcare.channelHistory.client.util.VersionControlUtil;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.kaurpalang.mirth.annotationsplugin.annotation.MirthClientClass;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.plugins.ClientPlugin;

import org.jdesktop.swingx.action.ActionFactory;
import org.jdesktop.swingx.action.BoundAction;

import javax.swing.ImageIcon;
import java.util.Collections;
import java.util.Properties;

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

    @Override
    public void stop() {

    }

    @Override
    public void reset() {

    }

    public void doViewCodeTemplateHistory() {
        VersionHistoryProperties versionHistoryProperties = loadVersionHistoryProperties();

        if (!versionHistoryProperties.isEnableVersionHistory()) {
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

    public void importCodeTemplateFromRepo() {
        VersionHistoryProperties versionHistoryProperties = loadVersionHistoryProperties();

        if (!versionHistoryProperties.isEnableVersionHistory()) {
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

    private VersionHistoryProperties loadVersionHistoryProperties() {
        Properties properties;
        try {
            Client client = parent.mirthClient;
            properties = client.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
        } catch (ClientException e) {
            properties = new Properties();
        }

        VersionHistoryProperties versionHistoryProperties = new VersionHistoryProperties();
        versionHistoryProperties.fromProperties(properties);

        return versionHistoryProperties;
    }
}