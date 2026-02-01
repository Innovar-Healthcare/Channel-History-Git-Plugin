package com.innovarhealthcare.channelHistory.shared.model;

/**
 * Error codes for Version History operations
 */
public class VersionHistoryErrorCodes {

    public static final String INVALID_REQUEST = "INVALID_REQUEST"; // Malformed request

    // Git connection errors
    public static final String GIT_NOT_CONNECTED = "GIT_NOT_CONNECTED";
    public static final String GIT_AUTH_FAILED = "GIT_AUTH_FAILED";
    public static final String GIT_NETWORK_ERROR = "GIT_NETWORK_ERROR";

    // File/Repository errors
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String INVALID_REVISION = "INVALID_REVISION";

    // General errors
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String UNPARSEABLE_RESPONSE = "UNPARSEABLE_RESPONSE";

    private VersionHistoryErrorCodes() {
        // Prevent instantiation
    }
}
