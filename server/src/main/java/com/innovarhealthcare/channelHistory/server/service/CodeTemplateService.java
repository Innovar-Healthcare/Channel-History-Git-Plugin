package com.innovarhealthcare.channelHistory.server.service;

import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service for managing CodeTemplate objects in Git repository
 */
public class CodeTemplateService extends ModeService<CodeTemplate> {
    private static final Logger logger = LogManager.getLogger(CodeTemplateService.class);
    private static final String DIRECTORY = "codetemplates";
    private static final String TYPE_NAME = "Code Template";

    public CodeTemplateService(GitRepositoryServiceLegacy gitService) {
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
    protected CodeTemplate deserializeAndVerify(String content, String filePath) {
        try {
            // Deserialize XML to CodeTemplate object
            CodeTemplate template = ObjectXMLSerializer.getInstance().deserialize(content, CodeTemplate.class);

            // Verify template is not null
            if (template == null) {
                logger.warn("Deserialized code template is null: {}", filePath);
                return null;
            }

            // CodeTemplate doesn't have InvalidCodeTemplate like Channel has InvalidChannel
            // Just return if not null

            return template;

        } catch (Exception e) {
            logger.warn("Failed to deserialize code template from: {}", filePath, e);
            return null;
        }
    }

    @Override
    protected String extractId(CodeTemplate template) {
        return template.getId();
    }

    @Override
    protected String extractName(CodeTemplate template) {
        return template.getName();
    }

    @Override
    protected void postCommit(String id, String commitId) {
        // Default implementation - no special post-commit actions for code templates
        // Can be extended in the future if needed
    }
}
