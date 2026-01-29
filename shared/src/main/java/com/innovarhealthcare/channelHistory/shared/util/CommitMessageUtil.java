package com.innovarhealthcare.channelHistory.shared.util;

import com.mirth.connect.model.Channel;
import com.mirth.connect.model.codetemplates.CodeTemplate;

/**
 * Utility class for creating and parsing commit messages
 * Handles both formatting (creation) and parsing (extraction) of commit message strings
 */
public class CommitMessageUtil {

    // Message format constants
    private static final String NAME_SEPARATOR = " name: ";
    private static final String MESSAGE_PREFIX = "Message: ";
    private static final String SERVER_NAME_PREFIX = "Server Name: ";
    private static final String SERVER_ID_PREFIX = "Server Id: ";
    private static final int SERVER_ID_LENGTH = 36;
    public static final String DEFAULT_SERVER_ID = "00000000-0000-0000-0000-000000000000";

    // ==================== FORMATTING (Creation) ====================

    /**
     * Create a formatted commit message string
     *
     * @param object      The object being committed (Channel, CodeTemplate, etc.)
     * @param userMessage The commit message from user
     * @param serverId    The server identifier
     * @param serverName  The server name (optional)
     * @return Formatted commit message string
     */
    public static String create(Object object, String userMessage, String serverId, String serverName) {
        String objectType = determineObjectType(object);
        String objectName = getObjectName(object);

        StringBuilder formattedMessage = new StringBuilder();
        formattedMessage.append(objectType).append(NAME_SEPARATOR).append(objectName).append(". ").append(MESSAGE_PREFIX).append(userMessage);

        if (serverName != null && !serverName.trim().isEmpty()) {
            formattedMessage.append(". ").append(SERVER_NAME_PREFIX).append(serverName).append(". ").append(SERVER_ID_PREFIX).append(serverId);
        } else {
            formattedMessage.append(". ").append(SERVER_ID_PREFIX).append(serverId);
        }

        return formattedMessage.toString();
    }

    /**
     * Overloaded create method without server name
     */
    public static String create(Object object, String userMessage, String serverId) {
        return create(object, userMessage, serverId, null);
    }

    // ==================== PARSING (Extraction) ====================

    /**
     * Extracts the user message content from the formatted message
     * Format: "ObjectType name: ObjectName. Message: UserMessage. Server ..."
     */
    public static String extractContent(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return "";
        }

        try {
            int messageStart = rawMessage.indexOf(MESSAGE_PREFIX);
            int serverStart = rawMessage.indexOf(". " + SERVER_NAME_PREFIX);
            if (serverStart == -1) {
                serverStart = rawMessage.indexOf(". " + SERVER_ID_PREFIX);
            }

            if (messageStart == -1 || serverStart == -1 || messageStart >= serverStart) {
                return rawMessage;
            }

            return rawMessage.substring(messageStart + MESSAGE_PREFIX.length(), serverStart).trim();
        } catch (Exception e) {
            return rawMessage;
        }
    }

    /**
     * Extracts the server ID from the message
     * Works for both old and new formats
     */
    public static String extractServerId(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return DEFAULT_SERVER_ID;
        }

        try {
            int serverIdStart = rawMessage.indexOf(SERVER_ID_PREFIX);
            if (serverIdStart == -1) {
                return DEFAULT_SERVER_ID;
            }

            int idStart = serverIdStart + SERVER_ID_PREFIX.length();
            if (idStart + SERVER_ID_LENGTH > rawMessage.length()) {
                return DEFAULT_SERVER_ID;
            }

            String serverId = rawMessage.substring(idStart, idStart + SERVER_ID_LENGTH);

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
     */
    public static String extractServerName(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return null;
        }

        try {
            int serverNameStart = rawMessage.indexOf(". " + SERVER_NAME_PREFIX);
            if (serverNameStart == -1) {
                return null; // Old format, no server name
            }

            int serverIdStart = rawMessage.indexOf(". " + SERVER_ID_PREFIX, serverNameStart);
            if (serverIdStart == -1) {
                return null;
            }

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
    public static String extractObjectType(String rawMessage) {
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
    public static String extractObjectName(String rawMessage) {
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
    public static boolean hasServerName(String rawMessage) {
        return extractServerName(rawMessage) != null;
    }

    /**
     * Validates if the message follows the expected format
     */
    public static boolean isValidFormat(String rawMessage) {
        return extractObjectType(rawMessage) != null && extractObjectName(rawMessage) != null && extractContent(rawMessage) != null && extractServerId(rawMessage) != null;
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
