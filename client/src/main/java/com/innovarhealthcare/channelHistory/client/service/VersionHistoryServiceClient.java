package com.innovarhealthcare.channelHistory.client.service;

import java.util.List;

import com.innovarhealthcare.channelHistory.client.exception.VersionHistoryClientException;
import com.innovarhealthcare.channelHistory.client.model.ChannelWithRaw;
import com.innovarhealthcare.channelHistory.client.model.CodeTemplateWithRaw;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.LibrariesAndTemplatesResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryErrorCodes;
import com.innovarhealthcare.channelHistory.shared.util.JsonUtils;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.EntityException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.donkey.util.xstream.SerializerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
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
    public List<CommitMetaData> loadChannelHistory(String channelId) throws ClientException {
        try {
            // 1. Make the call
            String jsonResponse = getServlet().getHistory(channelId, VersionControlConstants.MODE_CHANNEL);
            return JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new ClientException("Failed to load channel history", e);
        }
    }

    /**
     * Load channel list metadata from repository
     *
     * @return List of channel metadata (id, name, path, lastCommitId)
     * @throws ClientException if Git operations fail
     */
    public List<RepoItemMetadata> loadChannelListFromRepo() throws ClientException {
        try {
            // 1. Make the call
            String jsonResponse = getServlet().loadChannelsMetadata();
            return JsonUtils.fromJsonList(jsonResponse, RepoItemMetadata.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new ClientException("Failed to load channel list from repo: " + e.getMessage(), e);
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
            String xmlContent = loadChannelRawContentFromRepo(channelId, revision);
            Channel channel = ObjectXMLSerializer.getInstance().deserialize(xmlContent, Channel.class);

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
            String xmlContent = loadChannelRawContentFromRepo(channelId, revision);
            Channel channel = ObjectXMLSerializer.getInstance().deserialize(xmlContent, Channel.class);

            if (channel == null) {
                throw new ClientException("Failed to deserialize channel content for ID: " + channelId);
            }

            if (channel instanceof InvalidChannel) {
                InvalidChannel invalidChannel = (InvalidChannel) channel;
                String errorMsg = invalidChannel.getCause() != null ? invalidChannel.getCause().getMessage() : "Unknown deserialization error";
                throw new ClientException("Invalid channel content for ID: " + channelId + ". Error: " + errorMsg);
            }

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
     * Commit and push a channel to the repository
     *
     * @param channel The channel object to commit
     * @param message User's commit message describing the changes
     * @param userId  The user ID performing the commit
     * @return String containing the operation result and commit information
     * @throws ClientException if channel is invalid, commit fails, or push operation fails
     */
    public String commitAndPushChannel(Channel channel, String message, String userId) throws ClientException {
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null");
        }
        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("Commit message cannot be null or empty");
        }
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        try {
            return getServlet().commitAndPushChannel(channel, message, userId);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to commit and push channel: " + e.getMessage(), e);
        }
    }

    public String saveLibraries(List<CodeTemplateLibrary> libraries, String message, String userId) throws ClientException {
        if (libraries == null) {
            throw new IllegalArgumentException("libraries cannot be null");
        }
        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("Commit message cannot be null or empty");
        }
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        try {
            return getServlet().saveLibraries(libraries, message, userId);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to save and push libraries: " + e.getMessage(), e);
        }
    }

    /**
     * Load libraries and code template metadata from repository
     * Returns both library information and template metadata for client-side grouping
     *
     * @return LibrariesAndTemplatesResponse containing libraries and template metadata
     * @throws ClientException if Git operations fail
     */
    public LibrariesAndTemplatesResponse loadLibrariesAndTemplateMetadata() throws ClientException {
        try {
            String jsonResponse = getServlet().loadLibrariesAndTemplateMetadata();
            return JsonUtils.fromJson(jsonResponse, LibrariesAndTemplatesResponse.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load libraries and template metadata: " + e.getMessage(), e);
        }
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
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load code template history: " + e.getMessage(), e);
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
            String jsonResponse = getServlet().loadCodeTemplatesMetadata();
            return JsonUtils.fromJsonList(jsonResponse, RepoItemMetadata.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load code template list from repo: " + e.getMessage(), e);
        }
    }

    /**
     * Load code template from repository at specific revision
     *
     * @param templateId Code template UUID
     * @param revision   Git revision (typically metadata.getLastCommitId())
     * @return CodeTemplate object
     * @throws ClientException if template not found, invalid, or Git error occurs
     */
    public CodeTemplate loadCodeTemplateFromRepo(String templateId, String revision) throws ClientException {
        try {
            String xmlContent = loadCodeTemplateRawContentFromRepo(templateId, revision);
            CodeTemplate template = ObjectXMLSerializer.getInstance().deserialize(xmlContent, CodeTemplate.class);

            if (template == null) {
                throw new ClientException("Failed to deserialize code template content for ID: " + templateId);
            }

            return template;

        } catch (SerializerException e) {
            throw new ClientException("Failed to deserialize code template XML for ID: " + templateId + ". " + e.getMessage(), e);
        }
    }

    /**
     * Load code template with raw XML content from repository
     *
     * @param templateId Code template UUID
     * @param revision   Git revision (typically metadata.getLastCommitId())
     * @return CodeTemplateWithRaw object containing both CodeTemplate and raw XML
     * @throws ClientException if template not found, invalid, or Git error occurs
     */
    public CodeTemplateWithRaw loadCodeTemplateWithRawFromRepo(String templateId, String revision) throws ClientException {
        try {
            String xmlContent = loadCodeTemplateRawContentFromRepo(templateId, revision);
            CodeTemplate template = ObjectXMLSerializer.getInstance().deserialize(xmlContent, CodeTemplate.class);

            if (template == null) {
                throw new ClientException("Failed to deserialize code template content for ID: " + templateId);
            }

            return new CodeTemplateWithRaw(template, xmlContent);
        } catch (SerializerException e) {
            throw new ClientException("Failed to deserialize code template XML for ID: " + templateId + ". " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method - load code template using metadata
     *
     * @param metadata Code template metadata from loadCodeTemplateListFromRepo()
     */
    public CodeTemplate loadCodeTemplateFromRepo(RepoItemMetadata metadata) throws ClientException {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        return loadCodeTemplateFromRepo(metadata.getId(), metadata.getLastCommitId());
    }

    /**
     * Commit and push a code template to the repository
     *
     * @param codeTemplateId The code template ID (UUID) to commit
     * @param message        User's commit message describing the changes
     * @param userId         The user ID performing the commit
     * @return String containing the operation result and commit information
     * @throws ClientException if code template is invalid, commit fails, or push operation fails
     */
    public String commitAndPushCodeTemplate(String codeTemplateId, String message, String userId) throws ClientException {
        if (StringUtils.isBlank(codeTemplateId)) {
            throw new IllegalArgumentException("Code template ID cannot be null or empty");
        }
        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("Commit message cannot be null or empty");
        }
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        try {
            return getServlet().commitAndPushCodeTemplate(codeTemplateId, message, userId);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to commit and push code template: " + e.getMessage(), e);
        }
    }

    /**
     * Private helper: Load raw XML content from repository
     */
    private String loadChannelRawContentFromRepo(String channelId, String revision) throws ClientException {
        if (StringUtils.isBlank(channelId)) {
            throw new ClientException("Channel ID cannot be null or empty");
        }
        if (StringUtils.isBlank(revision)) {
            throw new ClientException("Revision cannot be null or empty");
        }

        try {
            String xmlContent = getServlet().getContentAtRevision(channelId, revision, VersionControlConstants.MODE_CHANNEL);

            if (StringUtils.isBlank(xmlContent)) {
                throw new ClientException("Channel not found or content is empty: " + channelId);
            }

            return xmlContent;

        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load channel from repository: " + e.getMessage(), e);
        }
    }

    /**
     * Private helper: Load raw XML content of code template from repository
     */
    private String loadCodeTemplateRawContentFromRepo(String templateId, String revision) throws ClientException {
        if (StringUtils.isBlank(templateId)) {
            throw new ClientException("Code template ID cannot be null or empty");
        }
        if (StringUtils.isBlank(revision)) {
            throw new ClientException("Revision cannot be null or empty");
        }

        try {
            String xmlContent = getServlet().getContentAtRevision(templateId, revision, VersionControlConstants.MODE_CODE_TEMPLATE);

            if (StringUtils.isBlank(xmlContent)) {
                throw new ClientException("Code template not found or content is empty: " + templateId);
            }

            return xmlContent;

        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load code template from repository: " + e.getMessage(), e);
        }
    }

    private VersionHistoryServletInterface getServlet() {
        Client client = PlatformUI.MIRTH_FRAME.mirthClient;
        return client.getServlet(VersionHistoryServletInterface.class);
    }

    /**
     * Parse structured error response from server and rethrow as VersionHistoryClientException
     *
     * @param e        Original ClientException from server
     * @param logError Whether to log the parsed error
     * @return VersionHistoryClientException with parsed error details
     */
    private ClientException rethrowParsedClientError(ClientException e, boolean logError) {
        Throwable cause = e.getCause();

        // Try to extract structured error from response entity
        if (cause instanceof EntityException) {
            String rawEntity = (String) ((EntityException) cause).getEntity();

            ErrorResponse error;
            try {
                // Try to parse as structured ErrorResponse
                error = JsonUtils.fromJson(rawEntity, ErrorResponse.class);

                return new VersionHistoryClientException(error, e);
            } catch (Exception parseError) {
                // Failed to parse - could be plain text error or different format
                if (logError) {
                    logger.error("Failed to parse server error response: {}", rawEntity, parseError);
                }

                // Create error response from raw text
                error = new ErrorResponse(VersionHistoryErrorCodes.UNPARSEABLE_RESPONSE, rawEntity);

                return new VersionHistoryClientException(error, e);
            }
        }

        // No EntityException - return original
        if (logError) {
            logger.error("Unstructured client exception: {}", e.getMessage(), e);
        }

        return e;
    }
}
