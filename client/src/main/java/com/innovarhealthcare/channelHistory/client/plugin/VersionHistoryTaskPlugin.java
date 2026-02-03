package com.innovarhealthcare.channelHistory.client.plugin;

import com.innovarhealthcare.channelHistory.client.dialog.ImportChannelDialog;
import com.innovarhealthcare.channelHistory.client.panel.VersionHistoryTaskPane;
import com.kaurpalang.mirth.annotationsplugin.annotation.MirthClientClass;
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
}
