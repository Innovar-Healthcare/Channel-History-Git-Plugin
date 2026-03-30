package com.innovarhealthcare.channelHistory.shared.model;

public enum TransportType {
    SSH,
    HTTPS;

    public static TransportType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return SSH;
        }
        try {
            return TransportType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SSH;
        }
    }
}
