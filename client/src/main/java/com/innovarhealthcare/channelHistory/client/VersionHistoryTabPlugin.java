package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.kaurpalang.mirth.annotationsplugin.annotation.MirthClientClass;
import com.mirth.connect.client.ui.AbstractChannelTabPanel;
import com.mirth.connect.plugins.ChannelTabPlugin;
import lombok.SneakyThrows;

/**
 * @author Kiran Ayyagari (kayyagari@apache.org)
 */
@MirthClientClass
public class VersionHistoryTabPlugin extends ChannelTabPlugin {

    private VersionHistoryTabPanel tabPanel;

    public VersionHistoryTabPlugin(String name) {
        super("Innovar Channel History");
    }

    @SneakyThrows
    @Override
    public void start() {
        tabPanel = new VersionHistoryTabPanel(parent);
    }

    @Override
    public AbstractChannelTabPanel getChannelTabPanel() {
        return tabPanel;
    }

    @Override
    public String getPluginPointName() {
        return "Innovar Channel History";
    }

}
