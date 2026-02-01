package com.innovarhealthcare.channelHistory.server.exception;

public class GitFileNotFoundException extends RuntimeException {
   
    public GitFileNotFoundException(String message) {
        super(message);
    }

    public GitFileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
