package com.innovarhealthcare.channelHistory.server.service;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;

import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

import java.util.Properties;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class ChannelService extends ModeService {
    protected static final String DIRECTORY = "channels";

    public ChannelService(GitRepositoryService gitService) {
        super(gitService);
    }

    @Override
    public String getDirectory() {
        return DIRECTORY;
    }

    @Override
    protected void postCommit(String id, String commitId) {
        String EXTENSION_NAME = VersionControlConstants.PLUGIN_NAME;
        ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();

        Properties props = new Properties();
        String key = "channel-" + id;
        props.setProperty(key, commitId);

        try {
            extensionController.setPluginProperties(EXTENSION_NAME, props, true);
        } catch (Exception e) {
//            System.err.println("Failed to store commit ID for channel " + id + ": " + e.getMessage());
        }
    }
}
