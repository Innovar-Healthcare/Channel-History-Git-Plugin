package com.innovarhealthcare.channelHistory.client.exception;

import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponse;
import com.mirth.connect.client.core.ClientException;

public class VersionHistoryClientException extends ClientException {
    private final ErrorResponse error;

    public VersionHistoryClientException(ErrorResponse error, Throwable cause) {
        super(error.getMessage(), cause);
        this.error = error;
    }

    public ErrorResponse getError() {
        return error;
    }
}
