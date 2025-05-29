package com.innovarhealthcare.channelHistory.shared.model;

import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONObject;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class CommitMetaData {
    public static final String DEFAULT_SERVER_ID = "00000000-0000-0000-0000-000000000000";
    private String hash;
    private String committer;
    private long timestamp;
    private String message;

    // Constructor for RevCommit
    public CommitMetaData(RevCommit commit) {
        this.hash = commit.getId().getName();
        this.committer = commit.getCommitterIdent() != null ? commit.getCommitterIdent().getName() : "Unknown";
        this.timestamp = commit.getCommitTime() * 1000L; // Convert seconds to milliseconds
        String rawMessage = commit.getFullMessage() != null ? commit.getFullMessage() : "";
        // Validate message for getServerId()
        this.message = rawMessage.length() >= 36 ? rawMessage : rawMessage + "[" + DEFAULT_SERVER_ID + "]";
    }

    // Constructor for JSON string
    public CommitMetaData(String json) {
        JSONObject obj = new JSONObject(json);
        this.hash = obj.has("hash") && !obj.isNull("hash") ? obj.getString("hash") : "";
        this.committer = obj.has("committer") && !obj.isNull("committer") ? obj.getString("committer") : "Unknown";
        this.timestamp = obj.has("timestamp") ? obj.getLong("timestamp") : 0L;
        String rawMessage = obj.has("message") && !obj.isNull("message") ? obj.getString("message") : "";
        // Validate message for getServerId()
        this.message = rawMessage.length() >= 36 ? rawMessage : rawMessage + "[" + DEFAULT_SERVER_ID + "]";
    }

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

    public String getMessage() {
        return message;
    }

    public String getMessageContent() {
        if (message == null) {
            return "";
        }
        try {
            // Expected format: "Channel name: ... Message: ... Server Id: ..."
            int messageStart = message.indexOf("Message: ");
            int serverIdStart = message.indexOf("Server Id: ");
            if (messageStart == -1 || serverIdStart == -1 || messageStart >= serverIdStart) {
                return message;
            }
            // Extract content between "Message: " and "Server Id: "
            return message.substring(messageStart + 9, serverIdStart).trim();
        } catch (Exception e) {
            return message;
        }
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // Note: server id is stored in message, it is last 36 chars
    public String getServerId() {
        return message.substring(message.length() - 36);
    }

    // Serialize to JSON using org.json
    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("hash", hash != null ? hash : JSONObject.NULL);
        json.put("committer", committer != null ? committer : JSONObject.NULL);
        json.put("timestamp", timestamp);
        json.put("message", message != null ? message : JSONObject.NULL);
        return json.toString();
    }
}