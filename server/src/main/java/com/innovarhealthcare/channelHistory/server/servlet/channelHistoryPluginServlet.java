package com.innovarhealthcare.channelHistory.server.servlet;

/**
 * @author Jim(Zi Min) Weng
 * @create 2023-10-20 3:31 PM
 */

import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.interfaces.ChannelHistoryServletInterface;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthApiProvider;
import com.kaurpalang.mirth.annotationsplugin.type.ApiProviderType;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.ControllerException;

import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.User;

import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.UserController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.CodeTemplateController;

import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Properties;

@MirthApiProvider(type = ApiProviderType.SERVER_CLASS)
public class ChannelHistoryPluginServlet extends MirthServlet implements ChannelHistoryServletInterface {
    private static final UserController userController = ControllerFactory.getFactory().createUserController();
    private static final CodeTemplateController codeTemplateController = ControllerFactory.getFactory().createCodeTemplateController();
    private static final Logger logger = Logger.getLogger(ChannelHistoryPluginServlet.class);

    public ChannelHistoryPluginServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
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
            logger.warn("Failed to load channels on repo. Error: ", e);
            throw new ClientException(e);
        }
    }

    @Override
    public String commitAndPushChannel(Channel channel, String message, String userId) throws ClientException {
        User user;

        if (channel == null) {
            throw new ClientException("Channel is not found");
        }

        try {
            user = userController.getUser(Integer.valueOf(userId), null);
        } catch (ControllerException e) {
            throw new ClientException("User is not found");
        }

        try {
            return GitRepositoryController.getInstance().commitAndPushChannel(channel, message, user);
        } catch (Exception e) {
            logger.warn("Failed to commit and push channel", e);
            throw new ClientException(e);
        }
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
        User user;
        CodeTemplate template;

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

        try {
            return GitRepositoryController.getInstance().commitAndPushCodeTemplate(template, message, user);
        } catch (GitRepositoryException e) {
            logger.warn("Failed to commit and push code template", e);
            throw new ClientException("Failed to commit and push code template");
        }
    }
}
