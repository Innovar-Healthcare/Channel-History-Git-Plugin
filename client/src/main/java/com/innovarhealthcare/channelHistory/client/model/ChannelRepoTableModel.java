package com.innovarhealthcare.channelHistory.client.model;

import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */
public class ChannelRepoTableModel extends AbstractTableModel {
    private static final Logger logger = LoggerFactory.getLogger(ChannelRepoTableModel.class);
    private static final String[] columnNames = {"Channel Id", "Channel Name", "Last Commit Id"};
    private final List<ChannelEntry> entries;

    private static class ChannelEntry {
        Channel channel;
        String lastCommitId;

        ChannelEntry(Channel channel, String lastCommitId) {
            this.channel = channel;
            this.lastCommitId = lastCommitId != null ? lastCommitId : "(unknown)";
        }
    }

    public ChannelRepoTableModel(List<String> jsonList) {
        this.entries = new ArrayList<>();
        for (String json : jsonList) {
            try {
                JSONObject obj = new JSONObject(json);
                String content = obj.has("content") && !obj.isNull("content") ? obj.getString("content") : "";
                String lastCommitId = obj.has("lastCommitId") && !obj.isNull("lastCommitId") ? obj.getString("lastCommitId") : null;
                Channel channel = stringToChannel(content);
                if (channel != null) {
                    entries.add(new ChannelEntry(channel, lastCommitId));
                }
            } catch (Exception e) {
                logger.error("Failed to parse JSON: {}", json, e);
            }
        }
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class; // All columns return strings
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ChannelEntry entry = entries.get(rowIndex);
        Channel channel = entry.channel;

        switch (columnIndex) {
            case 0: // Channel Id
                return channel != null ? channel.getId() : "(error)";
            case 1: // Channel Name
                return channel != null ? channel.getName() : "(error)";
            case 2: // Last Commit Id
                return entry.lastCommitId;
            default:
                throw new IllegalArgumentException("Unknown column number: " + columnIndex);
        }
    }

    public Channel getChannelAt(int row) {
        if (row < 0 || row >= entries.size()) {
            return null;
        }
        return entries.get(row).channel;
    }

    public String getLastCommitIdAt(int row) {
        if (row < 0 || row >= entries.size()) {
            return null;
        }
        return entries.get(row).lastCommitId;
    }

    private Channel stringToChannel(String xml) {
        try {
            Channel ch = ObjectXMLSerializer.getInstance().deserialize(xml, Channel.class);
            if (ch instanceof InvalidChannel) {
                logger.warn("Invalid channel XML: {}", xml);
                return null;
            }
            return ch;
        } catch (Exception e) {
            logger.warn("Failed to deserialize channel XML: {}", xml, e);
            return null;
        }
    }
}