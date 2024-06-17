/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.innovarhealthcare.channelHistory.client;


import com.kaurpalang.mirth.annotationsplugin.annotation.MirthClientClass;
import com.mirth.connect.client.ui.Frame;
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
        String func = "doViewCodeTemplateHistory";
        String taskName = "View History";
        String description = "View the previous versions of this code template.";

        ImageIcon img = new ImageIcon(com.mirth.connect.client.ui.Frame.class.getResource("images/wrench.png"));

        BoundAction action;
        (action = ActionFactory.createBoundAction(func, taskName, "")).putValue("SmallIcon", img);
        action.putValue("ShortDescription", description);
        action.registerCallback(this, func);
        this.parent.codeTemplatePanel.addAction(action, Collections.singleton("onlySingleCodeTemplates"), func);

    }
    public void doViewCodeTemplateHistory() {
        if (!this.parent.codeTemplatePanel.changesHaveBeenMade() || this.parent.codeTemplatePanel.promptSave(true)) {
            String codeTemplateId;
            if ((codeTemplateId = this.parent.codeTemplatePanel.getCurrentSelectedId()) != null) {
                new CodeTemplateHistoryDialog(parent, codeTemplateId);
            }

        }
    }
    @Override
    public void stop() {

    }

    @Override
    public void reset() {

    }
}