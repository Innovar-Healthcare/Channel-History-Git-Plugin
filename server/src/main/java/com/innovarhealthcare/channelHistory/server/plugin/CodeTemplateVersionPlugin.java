package com.innovarhealthcare.channelHistory.server.plugin;

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthServerClass;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.ServerEventContext;
import com.mirth.connect.model.User;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.plugins.CodeTemplateServerPlugin;
import com.mirth.connect.server.controllers.ControllerFactory;

import org.apache.log4j.Logger;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-12-07 9:25 AM
 */

@MirthServerClass
public class CodeTemplateVersionPlugin implements CodeTemplateServerPlugin {
    private static Logger logger = Logger.getLogger(CodeTemplateVersionPlugin.class);

    @Override
    public String getPluginPointName() {
        return VersionControlConstants.PLUGIN_POINTNAME;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void remove(CodeTemplate ct, ServerEventContext sec) {
        // Thai Tran: not implement yet
    }

    @Override
    public void remove(CodeTemplateLibrary ctLib, ServerEventContext sec) {
    }

    @Override
    public void save(CodeTemplate ct, ServerEventContext sec) {
        if (!GitRepositoryController.getInstance().isEnable()) {
            return;
        }

        if (!GitRepositoryController.getInstance().isGitConnected()) {
            return;
        }

        if (!GitRepositoryController.getInstance().isAutoCommit()) {
            return;
        }

        User user = null;

        try {
            user = ControllerFactory.getFactory().createUserController().getUser(sec.getUserId(), null);
        } catch (ControllerException e) {
            throw new RuntimeException(e);
        }

        try {
            GitRepositoryController.getInstance().commitAndPushCodeTemplate(ct, VersionControlConstants.AUTO_COMMITTED_MSG, user);
        } catch (Exception ignored) {
            
        }
    }

    @Override
    public void save(CodeTemplateLibrary ctLib, ServerEventContext sec) {
    }
}
