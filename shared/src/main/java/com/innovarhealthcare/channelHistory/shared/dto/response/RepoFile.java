package com.innovarhealthcare.channelHistory.shared.dto.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RepoFile {

    private String name;
    private long sizeBytes;

    public RepoFile() {
    }

    @JsonCreator
    public RepoFile(@JsonProperty("name") String name, @JsonProperty("sizeBytes") long sizeBytes) {
        this.name = name;
        this.sizeBytes = sizeBytes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    //@formatter:off
    @Override
    public String toString() {
        return "RepoFile{" +
                "name='" + name + '\'' +
                ", sizeBytes=" + sizeBytes +
                '}';
    }
    //@formatter:on
}
