package com.innovarhealthcare.channelHistory.server.exception;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class GitRepositoryException extends Exception {
    public GitRepositoryException(String message) {
        super(message);
    }

    public GitRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public GitRepositoryException(Throwable cause) {
        super(cause);
    }
}
