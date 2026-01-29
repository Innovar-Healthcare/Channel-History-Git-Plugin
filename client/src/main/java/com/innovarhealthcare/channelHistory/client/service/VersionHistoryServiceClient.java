package com.innovarhealthcare.channelHistory.client.service;

import java.util.List;

import com.innovarhealthcare.channelHistory.client.exception.VersionHistoryClientException;
import com.innovarhealthcare.channelHistory.client.model.ChannelWithRaw;
import com.innovarhealthcare.channelHistory.client.model.CodeTemplateWithRaw;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.util.JsonUtils;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.EntityException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.donkey.util.xstream.SerializerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private VersionHistoryServiceClient() {
    }

    /**
     * Load complete commit history for a channel
     *
     * @param channelId Channel UUID
     * @return List of commit history entries (newest first)
     * @throws ClientException if channel not found or Git error occurs
     */
//    public List<CommitMetaData> loadChannelHistory(String channelId) throws ClientException {
//        if (StringUtils.isBlank(channelId)) {
//            throw new ClientException("Channel ID cannot be null or empty");
//        }
//
//        try {
//            String jsonResponse = getServlet().getHistory(channelId, VersionControlConstants.MODE_CHANNEL);
//            logger.error(jsonResponse);
//
//            List<CommitMetaData> revisions = JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);
//
//            logger.error("Found {} revisions for channel {}", revisions != null ? revisions.size() : 0, channelId);
//            if (revisions != null) {
//                for (int i = 0; i < revisions.size(); i++) {
//                    CommitMetaData meta = revisions.get(i);
//                    logger.error("Revision {}: hash={}, committer={}, timestamp={}, message={}", i, meta.getHash(), meta.getCommitter(), meta.getTimestamp(), meta.getMessageContent());
//                }
//            }
//
//            return revisions;
//
//        } catch (ClientException e) {
//            rethrowParsedClientError(e);
//            return null;
//
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to load channel history for ID: " + channelId, e);
//        }
//    }
    public List<CommitMetaData> loadChannelHistory(String channelId) throws ClientException {
        if (StringUtils.isBlank(channelId)) {
            throw new ClientException("Channel ID cannot be null or empty");
        }

        try {
            logger.error("=== START loadChannelHistory for: {} ===", channelId);

            String jsonResponse = getServlet().getHistory(channelId, VersionControlConstants.MODE_CHANNEL);
            logger.error("JSON Response received, length: {}", jsonResponse != null ? jsonResponse.length() : 0);
            logger.error("JSON Response content (first 500 chars): {}", jsonResponse != null && jsonResponse.length() > 500 ? jsonResponse.substring(0, 500) + "..." : jsonResponse);

            // Try to parse
            logger.error("Attempting to parse JSON to List<CommitMetaData>...");
            List<CommitMetaData> revisions = null;

            try {
                revisions = JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);
                logger.error("✓ JSON parsing SUCCESS!");

            } catch (Exception parseEx) {
                logger.error("✗ JSON parsing FAILED!", parseEx);
                logger.error("Parse exception type: {}", parseEx.getClass().getName());
                logger.error("Parse exception message: {}", parseEx.getMessage());

                // Print full stack trace
                parseEx.printStackTrace();

                // Try to understand the JSON structure
                logger.error("Trying to parse as raw object to see structure...");
                try {
                    Object rawObj = JsonUtils.fromJson(jsonResponse, Object.class);
                    logger.error("Raw object type: {}", rawObj.getClass().getName());
                    logger.error("Raw object: {}", rawObj);
                } catch (Exception e2) {
                    logger.error("Even raw parsing failed", e2);
                }

                throw parseEx; // Rethrow to be caught by outer catch
            }

            logger.error("Found {} revisions for channel {}", revisions != null ? revisions.size() : 0, channelId);

            if (revisions != null && !revisions.isEmpty()) {
                for (int i = 0; i < Math.min(3, revisions.size()); i++) {
                    CommitMetaData meta = revisions.get(i);
                    logger.error("Revision {}: hash={}, committer={}, timestamp={}, message={}", i, meta.getHash(), meta.getCommitter(), meta.getTimestamp(), meta.getMessageContent());
                }
            }

            logger.error("=== END loadChannelHistory SUCCESS ===");
            return revisions;

        } catch (ClientException e) {
            logger.error("ClientException in loadChannelHistory", e);
            rethrowParsedClientError(e);
            return null;

        } catch (Exception e) {
            logger.error("Exception in loadChannelHistory for ID: {}", channelId, e);
            throw new RuntimeException("Failed to load channel history for ID: " + channelId, e);
        }
    }

    public List<RepoItemMetadata> loadChannelListFromRepo() throws ClientException {
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
        try {
            // Get raw content (shared logic)
            String xmlContent = loadChannelRawContentFromRepo(channelId, revision);

            // Deserialize to Channel
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

        } catch (SerializerException e) {
            throw new ClientException("Failed to deserialize channel XML for ID: " + channelId + ". " + e.getMessage(), e);
        }
    }

    /**
     * Load Channel object with raw content from repository
     * Use this when you need both the Channel object and raw XML content (e.g., for diff comparison)
     */
    public ChannelWithRaw loadChannelWithRawFromRepo(String channelId, String revision) throws ClientException {
        try {
            // Get raw content (shared logic)
            String xmlContent = loadChannelRawContentFromRepo(channelId, revision);

            // Deserialize to Channel
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

            // Return both channel and raw content
            return new ChannelWithRaw(channel, xmlContent);

        } catch (SerializerException e) {
            throw new ClientException("Failed to deserialize channel XML for ID: " + channelId + ". " + e.getMessage(), e);
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

    /**
     * Load complete commit history for a code template
     *
     * @param codeTemplateId Code template UUID
     * @return List of commit history entries (newest first)
     * @throws ClientException if code template not found or Git error occurs
     */
    public List<CommitMetaData> loadCodeTemplateHistory(String codeTemplateId) throws ClientException {
        if (StringUtils.isBlank(codeTemplateId)) {
            throw new ClientException("Code template ID cannot be null or empty");
        }

        try {
            String jsonResponse = getServlet().getHistory(codeTemplateId, VersionControlConstants.MODE_CODE_TEMPLATE);
            return JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);

        } catch (ClientException e) {
            rethrowParsedClientError(e);
            return null;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load code template history for ID: " + codeTemplateId, e);
        }
    }

    /**
     * Load code template list metadata from repository
     *
     * @return List of code template metadata (id, name, path, lastCommitId)
     * @throws ClientException if Git operations fail
     */
    public List<RepoItemMetadata> loadCodeTemplateListFromRepo() throws ClientException {
        try {
            // Make the call to servlet
            String jsonResponse = getServlet().loadCodeTemplateOnRepo();

            // Parse JSON response to metadata list
            return JsonUtils.fromJsonList(jsonResponse, RepoItemMetadata.class);

        } catch (ClientException e) {
            // Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);
            return null;

        } catch (Exception e) {
            // JSON serialization or unexpected errors
            throw new RuntimeException("Failed to load code template list from repo", e);
        }
    }

    /**
     * Load code template from repository at specific revision
     * Use this when you only need the CodeTemplate object for processing
     *
     * @param templateId Code template UUID
     * @param revision   Git revision (typically metadata.getLastCommitId())
     * @return CodeTemplate object
     * @throws ClientException if template not found, invalid, or Git error occurs
     */
    public CodeTemplate loadCodeTemplateFromRepo(String templateId, String revision) throws ClientException {
        try {
            // Get raw content (shared logic)
            String xmlContent = loadCodeTemplateRawContentFromRepo(templateId, revision);

            // Deserialize XML to CodeTemplate object
            CodeTemplate template = ObjectXMLSerializer.getInstance().deserialize(xmlContent, CodeTemplate.class);

            // Validate deserialized template
            if (template == null) {
                throw new ClientException("Failed to deserialize code template content for ID: " + templateId);
            }

            // Note: CodeTemplate doesn't have InvalidCodeTemplate like Channel has InvalidChannel
            // So we just check for null

            return template;

        } catch (SerializerException e) {
            // XML deserialization specific error
            throw new ClientException("Failed to deserialize code template XML for ID: " + templateId + ". " + e.getMessage(), e);
        }
    }

    /**
     * Load code template with raw XML content from repository at specific revision
     * Use this when you need both the CodeTemplate object and raw XML content (e.g., for diff comparison)
     *
     * @param templateId Code template UUID
     * @param revision   Git revision (typically metadata.getLastCommitId())
     * @return CodeTemplateWithRaw object containing both CodeTemplate and raw XML
     * @throws ClientException if template not found, invalid, or Git error occurs
     */
    public CodeTemplateWithRaw loadCodeTemplateWithRawFromRepo(String templateId, String revision) throws ClientException {
        try {
            // Get raw content (shared logic)
            String xmlContent = loadCodeTemplateRawContentFromRepo(templateId, revision);

            // Deserialize XML to CodeTemplate object
            CodeTemplate template = ObjectXMLSerializer.getInstance().deserialize(xmlContent, CodeTemplate.class);

            // Validate deserialized template
            if (template == null) {
                throw new ClientException("Failed to deserialize code template content for ID: " + templateId);
            }

            // Return both template and raw content
            return new CodeTemplateWithRaw(template, xmlContent);

        } catch (SerializerException e) {
            // XML deserialization specific error
            throw new ClientException("Failed to deserialize code template XML for ID: " + templateId + ". " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method - load code template using metadata
     *
     * @param metadata Code template metadata from loadCodeTemplateListFromRepo()
     * @return CodeTemplate object
     * @throws ClientException if template not found or invalid
     */
    public CodeTemplate loadCodeTemplateFromRepo(RepoItemMetadata metadata) throws ClientException {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }

        return loadCodeTemplateFromRepo(metadata.getId(), metadata.getLastCommitId());
    }

    /**
     * Private helper: Load raw XML content from repository
     * This method is shared by both public methods to avoid code duplication
     */
    private String loadChannelRawContentFromRepo(String channelId, String revision) throws ClientException {
        // Validate inputs
        if (StringUtils.isBlank(channelId)) {
            throw new ClientException("Channel ID cannot be null or empty");
        }
        if (StringUtils.isBlank(revision)) {
            throw new ClientException("Revision cannot be null or empty");
        }

        try {
            // Get raw content from servlet
            String xmlContent = getServlet().getFileContentFromRepo(channelId, revision, VersionControlConstants.MODE_CHANNEL);

            // Validate content returned
            if (StringUtils.isBlank(xmlContent)) {
                throw new ClientException("Channel not found or content is empty: " + channelId);
            }

            return xmlContent;

        } catch (ClientException e) {
            rethrowParsedClientError(e);
            return null;
        } catch (Exception e) {
            throw new ClientException("Failed to load channel from repository: channelId=" + channelId + ", revision=" + revision, e);
        }
    }

    /**
     * Private helper: Load raw XML content of code template from repository
     *
     * @param templateId Code template UUID
     * @param revision   Git revision (typically metadata.getLastCommitId())
     * @return Raw XML content as String
     * @throws ClientException if template not found or Git error occurs
     */
    private String loadCodeTemplateRawContentFromRepo(String templateId, String revision) throws ClientException {
        // Validate inputs
        if (StringUtils.isBlank(templateId)) {
            throw new ClientException("Code template ID cannot be null or empty");
        }
        if (StringUtils.isBlank(revision)) {
            throw new ClientException("Revision cannot be null or empty");
        }

        try {
            // Get code template content (XML string) from servlet
            String xmlContent = getServlet().getFileContentFromRepo(templateId, revision, VersionControlConstants.MODE_CODE_TEMPLATE);

            // Validate content returned
            if (StringUtils.isBlank(xmlContent)) {
                throw new ClientException("Code template not found or content is empty: " + templateId);
            }

            return xmlContent;

        } catch (ClientException e) {
            // Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);
            return null;  // Won't reach here due to rethrow

        } catch (Exception e) {
            // Unexpected errors
            throw new ClientException("Failed to load code template from repository: templateId=" + templateId + ", revision=" + revision, e);
        }
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
