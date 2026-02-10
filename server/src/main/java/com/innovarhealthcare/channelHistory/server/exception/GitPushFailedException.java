package com.innovarhealthcare.channelHistory.server.exception;

public class GitPushFailedException extends Exception {

    public GitPushFailedException(String message) {
        super(message);
    }

    public GitPushFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}