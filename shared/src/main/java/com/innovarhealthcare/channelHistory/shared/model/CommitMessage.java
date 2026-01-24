package com.innovarhealthcare.channelHistory.shared.model;

import com.mirth.connect.model.Channel;
import com.mirth.connect.model.codetemplates.CodeTemplate;

public class CommitMessage {
    // Message format constants
    private static final String NAME_SEPARATOR = " name: ";
    private static final String MESSAGE_PREFIX = "Message: ";
    private static final String SERVER_NAME_PREFIX = "Server Name: ";
    private static final String SERVER_ID_PREFIX = "Server Id: ";
    private static final int SERVER_ID_LENGTH = 36;
    public static final String DEFAULT_SERVER_ID = "00000000-0000-0000-0000-000000000000";

    private final String rawMessage;

    /**
     * Constructor for parsing existing commit message
     *
     * @param rawMessage The raw commit message string
     */
    public CommitMessage(String rawMessage) {
        if (rawMessage == null) {
            this.rawMessage = "";
        } else if (rawMessage.length() < SERVER_ID_LENGTH) {
            // Ensure message has at least 36 chars for getServerId()
            this.rawMessage = rawMessage + "[" + DEFAULT_SERVER_ID + "]";
        } else {
            this.rawMessage = rawMessage;
        }
    }

    /**
     * Static factory method to create a new formatted commit message
     *
     * @param object      The object being committed (Channel, CodeTemplate, etc.)
     * @param userMessage The commit message from user
     * @param serverId    The server identifier
     * @param serverName  The server name (optional)
     * @return New CommitMessage instance
     */
    public static CommitMessage create(Object object, String userMessage, String serverId, String serverName) {
        String objectType = determineObjectType(object);
        String objectName = getObjectName(object);

        StringBuilder formattedMessage = new StringBuilder();
        formattedMessage.append(objectType).append(NAME_SEPARATOR).append(objectName).append(". ").append(MESSAGE_PREFIX).append(userMessage);

        if (serverName != null && !serverName.trim().isEmpty()) {
            formattedMessage.append(". ").append(SERVER_NAME_PREFIX).append(serverName).append(". ").append(SERVER_ID_PREFIX).append(serverId);
        } else {
            formattedMessage.append(". ").append(SERVER_ID_PREFIX).append(serverId);
        }

        return new CommitMessage(formattedMessage.toString());
    }

    /**
     * Overloaded factory method without server name
     */
    public static CommitMessage create(Object object, String userMessage, String serverId) {
        return create(object, userMessage, serverId, null);
    }

    /**
     * Gets the raw commit message string
     */
    public String getRawMessage() {
        return rawMessage;
    }

    /**
     * Extracts the user message content from the formatted message
     * Format: "ObjectType name: ObjectName. Message: UserMessage. Server ..."
     */
    public String getMessageContent() {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return "";
        }

        try {
            int messageStart = rawMessage.indexOf(MESSAGE_PREFIX);

            // Try to find server name format first
            int serverStart = rawMessage.indexOf(". " + SERVER_NAME_PREFIX);
            if (serverStart == -1) {
                // Fallback to server id format (old format or no server name)
                serverStart = rawMessage.indexOf(". " + SERVER_ID_PREFIX);
            }

            if (messageStart == -1 || serverStart == -1 || messageStart >= serverStart) {
                return rawMessage;
            }

            // Extract content between "Message: " and server info
            return rawMessage.substring(messageStart + MESSAGE_PREFIX.length(), serverStart).trim();
        } catch (Exception e) {
            return rawMessage;
        }
    }

    /**
     * Extracts the server ID from the message
     * Server ID always follows "Server Id: " prefix
     * Works for both old and new formats
     * <p>
     * Old format: "...Message: xxx. Server Id: abc-123..."
     * New format: "...Message: xxx. Server Name: Production. Server Id: abc-123..."
     */
    public String getServerId() {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return DEFAULT_SERVER_ID;
        }

        try {
            int serverIdStart = rawMessage.indexOf(SERVER_ID_PREFIX);
            if (serverIdStart == -1) {
                return DEFAULT_SERVER_ID;
            }

            // Server ID starts after "Server Id: " and goes to end of message (36 chars)
            int idStart = serverIdStart + SERVER_ID_PREFIX.length();
            if (idStart + SERVER_ID_LENGTH > rawMessage.length()) {
                return DEFAULT_SERVER_ID;
            }

            String serverId = rawMessage.substring(idStart, idStart + SERVER_ID_LENGTH);

            // Validate it looks like a UUID format
            if (serverId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
                return serverId;
            }

            return DEFAULT_SERVER_ID;
        } catch (Exception e) {
            return DEFAULT_SERVER_ID;
        }
    }

    /**
     * Extracts the server name from the message (if available)
     * Returns null if message uses old format (no Server Name field)
     * <p>
     * Old format: "...Message: xxx. Server Id: abc-123..."
     * New format: "...Message: xxx. Server Name: Production. Server Id: abc-123..."
     */
    public String getServerName() {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return null;
        }

        try {
            // Check for Server Name prefix
            int serverNameStart = rawMessage.indexOf(". " + SERVER_NAME_PREFIX);
            if (serverNameStart == -1) {
                return null; // Old format, no server name
            }

            // Find the Server Id that follows
            int serverIdStart = rawMessage.indexOf(". " + SERVER_ID_PREFIX, serverNameStart);
            if (serverIdStart == -1) {
                return null; // Invalid format
            }

            // Extract server name between "Server Name: " and ". Server Id:"
            String serverName = rawMessage.substring(serverNameStart + 2 + SERVER_NAME_PREFIX.length(), serverIdStart).trim();

            return serverName.isEmpty() ? null : serverName;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts the object type from the message
     * Format: "ObjectType name: ..."
     */
    public String getObjectType() {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return null;
        }
        try {
            int nameIndex = rawMessage.indexOf(NAME_SEPARATOR);
            if (nameIndex == -1) {
                return null;
            }
            return rawMessage.substring(0, nameIndex).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts the object name from the message
     * Format: "... name: ObjectName. Message: ..."
     */
    public String getObjectName() {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return null;
        }
        try {
            int nameStart = rawMessage.indexOf(NAME_SEPARATOR);
            int messageStart = rawMessage.indexOf(". " + MESSAGE_PREFIX);

            if (nameStart == -1 || messageStart == -1 || nameStart >= messageStart) {
                return null;
            }

            return rawMessage.substring(nameStart + NAME_SEPARATOR.length(), messageStart).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if the message contains server name (new format)
     */
    public boolean hasServerName() {
        return getServerName() != null;
    }

    /**
     * Validates if the message follows the expected format
     */
    public boolean isValidFormat() {
        return getObjectType() != null && getObjectName() != null && getMessageContent() != null && getServerId() != null;
    }

    @Override
    public String toString() {
        return rawMessage;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        CommitMessage that = (CommitMessage) obj;
        return rawMessage != null ? rawMessage.equals(that.rawMessage) : that.rawMessage == null;
    }

    @Override
    public int hashCode() {
        return rawMessage != null ? rawMessage.hashCode() : 0;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Determines the type of the object
     */
    private static String determineObjectType(Object object) {
        if (object instanceof Channel) {
            return "Channel";
        }

        if (object instanceof CodeTemplate) {
            return "Code Template";
        }

        return "Object";
    }

    /**
     * Extracts the name from the object
     */
    private static String getObjectName(Object object) {
        if (object instanceof Channel) {
            return ((Channel) object).getName();
        }

        if (object instanceof CodeTemplate) {
            return ((CodeTemplate) object).getName();
        }

        return "Unknown";
    }
}
