package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthClientClass;
import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.plugins.SettingsPanelPlugin;

/**
 * @author Jim(Zi Min) Weng
 * @create 2024-04-19 12:21 PM
 */
@MirthClientClass
public class VersionHistorySettingPlugin extends SettingsPanelPlugin {
    private VersionHistorySettingPanel settingPanel;

    public VersionHistorySettingPlugin(String name) {
        super("Version History Plugin");
        try {
            this.settingPanel = new VersionHistorySettingPanel("Version History", this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AbstractSettingsPanel getSettingsPanel() {
        return settingPanel;
    }

    @Override
    public String getPluginPointName() {
        return "Version History Plugin";
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void reset() {

    }
}