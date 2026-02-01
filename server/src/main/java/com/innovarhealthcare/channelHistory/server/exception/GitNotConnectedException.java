package com.innovarhealthcare.channelHistory.server.exception;

/**
 * Thrown when Git service is not initialized/connected yet
 */
public class GitNotConnectedException extends RuntimeException {
    public GitNotConnectedException(String message) {
        super(message);
    }
    
    public GitNotConnectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
