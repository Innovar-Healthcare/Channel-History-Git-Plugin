package com.innovarhealthcare.channelHistory.client.model;

import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.table.AbstractTableModel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class CommitMetaDataTableModel extends AbstractTableModel {
    private static final Logger logger = LoggerFactory.getLogger(CommitMetaDataTableModel.class);
    private final List<CommitMetaData> revisions;
    private static final DateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
    private static final String[] columnNames = {"Commit Id", "Message", "Committer", "Date", "Server Id"};

    public CommitMetaDataTableModel(List<String> jsonRevisions) {
        this.revisions = new ArrayList<>();
        for (String json : jsonRevisions) {
            try {
                revisions.add(new CommitMetaData(json));
            } catch (IllegalArgumentException e) {
                logger.error("Failed to parse JSON revision: {}", json, e);
                // Add a placeholder for invalid JSON
                CommitMetaData placeholder = new CommitMetaData(new JSONObject()
                        .put("hash", "(error)")
                        .put("committer", "(error)")
                        .put("timestamp", 0L)
                        .put("message", "[" + CommitMetaData.DEFAULT_SERVER_ID + "]")
                        .toString());
                revisions.add(placeholder);
            }
        }
    }

    @Override
    public int getRowCount() {
        return revisions.size();
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
        switch (columnIndex) {
            case 0: // Commit Id
            case 1: // Message
            case 2: // Committer
            case 4: // Server Id
                return String.class;
            case 3: // Date
                return String.class; // Formatted date is a string
            default:
                return super.getColumnClass(columnIndex);
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        CommitMetaData meta = revisions.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return meta.getHash();
            case 1:
                return meta.getMessageContent();
            case 2:
                return meta.getCommitter();
            case 3:
                return formatTime(meta.getTimestamp());
            case 4:
                return meta.getServerId();
            default:
                throw new IllegalArgumentException("Unknown column number: " + columnIndex);
        }
    }

    public CommitMetaData getCommitMetaDataAt(int row) {
        if (row < 0 || row >= revisions.size()) {
            return null;
        }
        return revisions.get(row);
    }

    private String formatTime(long t) {
        if (t <= 0) {
            return "(unknown)";
        }
        Instant commitTime = Instant.ofEpochMilli(t);
        Instant now = Instant.now();
        Duration duration = Duration.between(commitTime, now);

        if (duration.toDays() > 3) {
            return df.format(new Date(t));
        } else if (duration.toDays() > 0) {
            return duration.toDays() + " days ago";
        } else if (duration.toHours() > 0) {
            return duration.toHours() + " hours ago";
        } else if (duration.toMinutes() > 0) {
            return duration.toMinutes() + " minutes ago";
        } else {
            return duration.getSeconds() + " seconds ago";
        }
    }
}