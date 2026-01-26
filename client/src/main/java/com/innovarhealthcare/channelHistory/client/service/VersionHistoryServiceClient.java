package com.innovarhealthcare.channelHistory.client.service;

import java.util.List;

import com.innovarhealthcare.channelHistory.client.exception.VersionHistoryClientException;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.util.JsonUtils;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.EntityException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.donkey.util.xstream.SerializerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class VersionHistoryServiceClient {
    private static VersionHistoryServiceClient instance = null;
    private final Logger logger = LogManager.getLogger(this.getClass());

    public static VersionHistoryServiceClient getInstance() {
        synchronized (VersionHistoryServiceClient.class) {
            if (instance == null) {
                instance = new VersionHistoryServiceClient();
            }

            return instance;
        }
    }

    public VersionHistoryServiceClient() {
    }

    public List<RepoItemMetadata> loadChannelOnRepo() throws ClientException {
        try {
            // 1. Make the call
            String jsonResponse = getServlet().loadChannelOnRepo();
            // Client receives
            return JsonUtils.fromJsonList(jsonResponse, RepoItemMetadata.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null;
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get load channel on repo", e);
        }
    }

    /**
     * Load channel from repository at specific revision
     *
     * @param channelId Channel UUID
     * @param revision  Git revision (typically metadata.getLastCommitId())
     * @return Channel object
     * @throws ClientException if channel not found, invalid, or Git error occurs
     */
    public Channel loadChannelFromRepo(String channelId, String revision) throws ClientException {
        // Validate inputs
        if (StringUtils.isBlank(channelId)) {
            throw new ClientException("Channel ID cannot be null or empty");
        }
        if (StringUtils.isBlank(revision)) {
            throw new ClientException("Revision cannot be null or empty");
        }

        try {
            // Get channel content (XML string, not JSON)
            String xmlContent = getServlet().getContent(channelId, revision, VersionControlConstants.MODE_CHANNEL);

            // Validate content returned
            if (StringUtils.isBlank(xmlContent)) {
                throw new ClientException("Channel not found or content is empty: " + channelId);
            }

            // Deserialize XML to Channel object
            Channel channel = ObjectXMLSerializer.getInstance().deserialize(xmlContent, Channel.class);

            // Validate deserialized channel
            if (channel == null) {
                throw new ClientException("Failed to deserialize channel content for ID: " + channelId);
            }

            if (channel instanceof InvalidChannel) {
                InvalidChannel invalidChannel = (InvalidChannel) channel;
                String errorMsg = invalidChannel.getCause() != null ? invalidChannel.getCause().getMessage() : "Unknown deserialization error";
                throw new ClientException("Invalid channel content for ID: " + channelId + ". Error: " + errorMsg);
            }

            return channel;

        } catch (ClientException e) {
            // Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);
            return null;  // Won't reach here due to rethrow

        } catch (SerializerException e) {
            // XML deserialization specific error
            throw new ClientException("Failed to deserialize channel XML for ID: " + channelId + ". " + e.getMessage(), e);

        } catch (Exception e) {
            // Unexpected errors
            throw new ClientException("Failed to load channel from repository: channelId=" + channelId + ", revision=" + revision, e);
        }
    }

    /**
     * Convenience method - load channel using metadata
     *
     * @param metadata Channel metadata from loadChannelListOnRepo()
     */
    public Channel loadChannelFromRepo(RepoItemMetadata metadata) throws ClientException {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }

        return loadChannelFromRepo(metadata.getId(), metadata.getLastCommitId());
    }

    private VersionHistoryServletInterface getServlet() {
        Client client = PlatformUI.MIRTH_FRAME.mirthClient;
        return client.getServlet(VersionHistoryServletInterface.class);
    }

    private void rethrowParsedClientError(ClientException e) throws ClientException {
        rethrowParsedClientError(e, true); // default to logging enabled
    }

    private void rethrowParsedClientError(ClientException e, boolean logError) throws ClientException {
        Throwable cause = e.getCause();

        if (cause instanceof EntityException) {
            String rawEntity = (String) ((EntityException) cause).getEntity();

            ErrorResponse error;
            try {
                error = JsonUtils.fromJson(rawEntity, ErrorResponse.class);
                if (logError) {
                    logger.error("Parsed API error: " + JsonUtils.toJson(error));
                }
            } catch (Exception parseError) {
                if (logError) {
                    logger.error("Failed to parse server error response: " + rawEntity, parseError);
                }
                error = new ErrorResponse("UNPARSEABLE_RESPONSE", "Failed to parse server error");
            }

            throw new VersionHistoryClientException(error, e);
        }

        throw e; // fallback if not structured
    }
}
