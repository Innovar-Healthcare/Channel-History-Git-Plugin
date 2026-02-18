package com.innovarhealthcare.channelHistory.client.diff.model;

public class DiffLine {
    private final int lineNumber;
    private final String content;
    private final ChangeType type;

    public DiffLine(int lineNumber, String content, ChangeType type) {
        this.lineNumber = lineNumber;
        this.content = content;
        this.type = type;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getContent() {
        return content;
    }

    public ChangeType getType() {
        return type;
    }
}