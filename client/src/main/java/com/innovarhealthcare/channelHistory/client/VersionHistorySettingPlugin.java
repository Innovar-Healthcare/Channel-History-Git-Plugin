/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
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
        super(name);
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
        return VersionControlConstants.SETTING_PLUGIN_POINTNAME;
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