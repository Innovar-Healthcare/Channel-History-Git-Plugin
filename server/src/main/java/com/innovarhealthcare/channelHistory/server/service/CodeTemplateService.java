package com.innovarhealthcare.channelHistory.server.service;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class CodeTemplateService extends ModeService {
    protected static final String DIRECTORY = "codetemplates";

    public CodeTemplateService(GitRepositoryService gitService) {
        super(gitService);
    }

    @Override
    public String getDirectory() {
        return DIRECTORY;
    }

    @Override
    protected void postCommit(String id, String commitId) {
        // Default implementation does nothing, or add custom logic
    }
}
