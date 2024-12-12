package com.innovarhealthcare.channelHistory.server.servlet;

/**
 * @author Jim(Zi Min) Weng
 * @create 2023-10-20 3:31 PM
 */

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.interfaces.channelHistoryServletInterface;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthApiProvider;
import com.kaurpalang.mirth.annotationsplugin.type.ApiProviderType;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.ControllerException;

import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.User;

import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.*;

import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Properties;

@MirthApiProvider(type = ApiProviderType.SERVER_CLASS)
public class channelHistoryPluginServlet extends MirthServlet implements channelHistoryServletInterface {
    private static final ChannelController channelController = ControllerFactory.getFactory().createChannelController();
    private static final UserController userController = ControllerFactory.getFactory().createUserController();
    private static final CodeTemplateController codeTemplateController = ControllerFactory.getFactory().createCodeTemplateController();

    private static final Logger logger = Logger.getLogger(channelHistoryPluginServlet.class);

    public channelHistoryPluginServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, VersionControlConstants.PLUGIN_POINTNAME);
    }

    @Override
    public List<String> getHistory(String fileName, String mode) throws ClientException {
        try {
            return GitRepositoryController.getInstance().getHistory(fileName, mode);
        } catch (Exception e) {
            logger.warn("failed to get the history of file " + fileName, e);
            throw new ClientException(e);
        }
    }

    @Override
    public String getContent(String fileName, String revision, String mode) throws ClientException {
        try {
            return GitRepositoryController.getInstance().getContent(fileName, revision, mode);
        } catch (Exception e) {
            logger.warn("failed to get the content of file " + fileName + " at revision " + revision, e);
            throw new ClientException(e);
        }
    }

    @Override
    public String updateSetting() {
        try {
            ExtensionController ec = ControllerFactory.getFactory().createExtensionController();
            Properties properties = null;
            try {
                properties = ec.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
            } catch (ControllerException e) {
                throw new RuntimeException(e);
            }

            GitRepositoryController.getInstance().update(properties);

            return "";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String validateSetting(Properties properties) throws ClientException {
        try {
            return GitRepositoryController.getInstance().validate(properties);
        } catch (Exception e) {
            throw new ClientException(e);
        }
    }

    @Override
    public List<String> loadChannelOnRepo() throws ClientException {
        try {
            return GitRepositoryController.getInstance().loadChannelOnRepo();
        } catch (Exception e) {
            logger.warn("Failed to load channels on repo", e);
            throw new ClientException(e);
        }
    }

    @Override
    public String commitAndPushChannel(String channelId, String message, String userId) throws ClientException {
        User user = null;
        Channel channel = channelController.getChannelById(channelId);

        try {
            user = userController.getUser(Integer.valueOf(userId), null);
        } catch (ControllerException e) {
            throw new ClientException("User is not found");
        }

        if (channel == null) {
            throw new ClientException("Channel is not found");
        }

        return GitRepositoryController.getInstance().commitAndPushChannel(channel, message, user);
    }

    @Override
    public List<String> loadCodeTemplateOnRepo() throws ClientException {
        try {
            return GitRepositoryController.getInstance().loadCodeTemplateOnRepo();
        } catch (Exception e) {
            logger.warn("Failed to load code templates on repo", e);
            throw new ClientException(e);
        }
    }

    @Override
    public String commitAndPushCodeTemplate(String codeTemplateId, String message, String userId) throws ClientException {
        User user = null;
        CodeTemplate template = null;

        try {
            user = userController.getUser(Integer.valueOf(userId), null);
        } catch (ControllerException e) {
            throw new ClientException("User is not found");
        }

        try {
            template = codeTemplateController.getCodeTemplateById(codeTemplateId);
        } catch (ControllerException e) {
            throw new ClientException("Code Template is not found");
        }

        return GitRepositoryController.getInstance().commitAndPushCodeTemplate(template, message, user);
    }
}
