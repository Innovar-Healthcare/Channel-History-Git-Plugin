package com.innovarhealthcare.channelHistory.server.service;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

/**
 * Service for managing Channel objects in Git repository
 */
public class ChannelService extends ModeService<Channel> {
    private final Logger logger = LogManager.getLogger(ChannelService.class);

    private static final String DIRECTORY = "channels";
    private static final String TYPE_NAME = "Channel";

    public ChannelService(GitRepositoryService gitService) {
        super(gitService);
    }

    @Override
    public String getDirectory() {
        return DIRECTORY;
    }

    @Override
    protected String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    protected Channel deserializeAndVerify(String content, String filePath) {
        try {
            // Deserialize XML to Channel object
            Channel channel = ObjectXMLSerializer.getInstance().deserialize(content, Channel.class);

            // Verify channel is not null
            if (channel == null) {
                logger.warn("Deserialized channel is null: {}", filePath);
                return null;
            }

            // Verify it's not an InvalidChannel
            if (channel instanceof InvalidChannel) {
                logger.warn("Skipping invalid channel: {}", filePath);
                return null;
            }

            return channel;

        } catch (Exception e) {
            logger.warn("Failed to deserialize channel from: {}", filePath, e);
            return null;
        }
    }

    @Override
    protected String extractId(Channel channel) {
        return channel.getId();
    }

    @Override
    protected String extractName(Channel channel) {
        return channel.getName();
    }

    @Override
    protected void postCommit(String id, String commitId) {
        ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();

        Properties props = new Properties();
        String key = "channel-" + id;
        props.setProperty(key, commitId);

        try {
            extensionController.setPluginProperties(VersionControlConstants.PLUGIN_NAME, props, true);
        } catch (Exception e) {
            logger.debug("Failed to store commit ID for channel {}: {}", id, e.getMessage());
        }
    }
}