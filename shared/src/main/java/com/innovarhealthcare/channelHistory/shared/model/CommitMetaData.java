package com.innovarhealthcare.channelHistory.shared.model;

import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONObject;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class CommitMetaData {

    private String hash;
    private String committer;
    private long timestamp;
    private CommitMessage message;

    /**
     * Constructor for RevCommit
     */
    public CommitMetaData(RevCommit commit) {
        this.hash = commit.getId().getName();
        this.committer = commit.getCommitterIdent() != null ? commit.getCommitterIdent().getName() : "Unknown";
        this.timestamp = commit.getCommitTime() * 1000L; // Convert seconds to milliseconds

        String rawMessage = commit.getFullMessage() != null ? commit.getFullMessage() : "";
        this.message = new CommitMessage(rawMessage);
    }

    /**
     * Constructor for JSON string
     */
    public CommitMetaData(String json) {
        JSONObject obj = new JSONObject(json);
        this.hash = obj.has("hash") && !obj.isNull("hash") ? obj.getString("hash") : "";
        this.committer = obj.has("committer") && !obj.isNull("committer") ? obj.getString("committer") : "Unknown";
        this.timestamp = obj.has("timestamp") ? obj.getLong("timestamp") : 0L;

        String rawMessage = obj.has("message") && !obj.isNull("message") ? obj.getString("message") : "";
        this.message = new CommitMessage(rawMessage);
    }

    /**
     * Constructor with all fields
     */
    public CommitMetaData(String hash, String committer, long timestamp, CommitMessage message) {
        this.hash = hash;
        this.committer = committer;
        this.timestamp = timestamp;
        this.message = message;
    }

    // ==================== Getters and Setters ====================

    public String getHash() {
        return hash;
    }

    public String getShortHash() {
        if (hash == null || hash.length() < 8) {
            return "(invalid)";
        }

        return hash.substring(0, 8);
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getCommitter() {
        return committer;
    }

    public void setCommitter(String committer) {
        this.committer = committer;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the CommitMessage object
     */
    public CommitMessage getMessage() {
        return message;
    }

    /**
     * Sets the CommitMessage object
     */
    public void setMessage(CommitMessage message) {
        this.message = message;
    }

    /**
     * Sets the message from a raw string
     */
    public void setMessage(String rawMessage) {
        this.message = new CommitMessage(rawMessage);
    }

    // ==================== Convenience Delegate Methods ====================

    /**
     * Gets the raw message string
     * Delegates to CommitMessage.getRawMessage()
     */
    public String getRawMessage() {
        return message != null ? message.getRawMessage() : "";
    }

    /**
     * Gets the user message content (without metadata)
     * Delegates to CommitMessage.getMessageContent()
     */
    public String getMessageContent() {
        return message != null ? message.getMessageContent() : "";
    }

    /**
     * Gets the server ID from the message
     * Delegates to CommitMessage.getServerId()
     */
    public String getServerId() {
        return message != null ? message.getServerId() : CommitMessage.DEFAULT_SERVER_ID;
    }

    /**
     * Gets the server name from the message (if available)
     * Delegates to CommitMessage.getServerName()
     */
    public String getServerName() {
        return message != null ? message.getServerName() : null;
    }

    /**
     * Gets the object type from the message
     * Delegates to CommitMessage.getObjectType()
     */
    public String getObjectType() {
        return message != null ? message.getObjectType() : null;
    }

    /**
     * Gets the object name from the message
     * Delegates to CommitMessage.getObjectName()
     */
    public String getObjectName() {
        return message != null ? message.getObjectName() : null;
    }

    /**
     * Checks if the message has server name (new format)
     * Delegates to CommitMessage.hasServerName()
     */
    public boolean hasServerName() {
        return message != null && message.hasServerName();
    }

    /**
     * Validates if the message follows expected format
     * Delegates to CommitMessage.isValidFormat()
     */
    public boolean isValidMessageFormat() {
        return message != null && message.isValidFormat();
    }

    // ==================== Serialization ====================

    /**
     * Serialize to JSON using org.json
     */
    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("hash", hash != null ? hash : JSONObject.NULL);
        json.put("committer", committer != null ? committer : JSONObject.NULL);
        json.put("timestamp", timestamp);
        json.put("message", message != null ? message.getRawMessage() : JSONObject.NULL);
        return json.toString();
    }

    @Override
    public String toString() {
        return "CommitMetaData{" + "hash='" + getShortHash() + "'" + ", committer='" + committer + "'" + ", timestamp=" + timestamp + ", objectType='" + getObjectType() + "'" + ", objectName='" + getObjectName() + "'" + ", serverId='" + getServerId() + "'" + ", serverName='" + getServerName() + "'" + '}';
    }
}