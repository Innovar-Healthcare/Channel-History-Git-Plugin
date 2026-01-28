package com.innovarhealthcare.channelHistory.client.model;

import com.mirth.connect.model.Channel;

public class ChannelWithRaw {
    private Channel channel;
    private String rawContent;

    public ChannelWithRaw(Channel channel, String rawContent) {
        this.channel = channel;
        this.rawContent = rawContent;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }
}
