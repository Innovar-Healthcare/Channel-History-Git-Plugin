package com.innovarhealthcare.channelHistory.client.model;

import com.mirth.connect.model.codetemplates.CodeTemplate;

public class CodeTemplateWithRaw {
    private CodeTemplate codeTemplate;
    private String rawContent;

    public CodeTemplateWithRaw(CodeTemplate codeTemplate, String rawContent) {
        this.codeTemplate = codeTemplate;
        this.rawContent = rawContent;
    }

    public CodeTemplate getCodeTemplate() {
        return codeTemplate;
    }

    public void setCodeTemplate(CodeTemplate codeTemplate) {
        this.codeTemplate = codeTemplate;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }
}
