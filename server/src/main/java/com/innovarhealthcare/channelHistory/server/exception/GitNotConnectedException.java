package com.innovarhealthcare.channelHistory.server.exception;

/**
 * Thrown when Git service is not initialized/connected yet
 */
public class GitNotConnectedException extends GitRepositoryException {
    public GitNotConnectedException() {
        super("Git repository is not connected. Please configure Git settings first.");
    }

    public GitNotConnectedException(String message) {
        super(message);
    }
}
