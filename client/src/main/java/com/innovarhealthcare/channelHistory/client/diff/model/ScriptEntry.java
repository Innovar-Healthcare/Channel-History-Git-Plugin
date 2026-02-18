package com.innovarhealthcare.channelHistory.client.diff.model;

/**
 * Model representing a script entry with its left/right versions
 */
public class ScriptEntry {
    private final String name;
    private final String leftCode;
    private final String rightCode;
    private final ChangeType changeType;

    public ScriptEntry(String name, String leftCode, String rightCode, ChangeType changeType) {
        this.name = name;
        this.leftCode = leftCode;
        this.rightCode = rightCode;
        this.changeType = changeType;
    }

    public String getName() {
        return name;
    }

    public String getLeftCode() {
        return leftCode;
    }

    public String getRightCode() {
        return rightCode;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    @Override
    public String toString() {
        return name;
    }
}