package com.innovarhealthcare.channelHistory.client.diff;

/**
 * Holds the parsed representation of a single channel connector (source or destination).
 */
class ChannelConnector {
    final String transportName;
    final String xmlContent;

    ChannelConnector(String transportName, String xmlContent) {
        this.transportName = transportName != null ? transportName : "";
        this.xmlContent    = xmlContent    != null ? xmlContent    : "";
    }
}
