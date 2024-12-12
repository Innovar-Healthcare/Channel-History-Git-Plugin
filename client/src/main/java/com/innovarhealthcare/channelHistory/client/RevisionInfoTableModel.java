package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.shared.RevisionInfo;
import org.joda.time.Period;
import org.json.JSONObject;

import javax.swing.table.AbstractTableModel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Kiran Ayyagari (kayyagari@apache.org)
 */
public class RevisionInfoTableModel extends AbstractTableModel {

    private List<String> revisions;

    private static DateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");

    private static final String[] columnNames = {"Commit Id", "Message", "Committer", "Date", "Server Id"};

    public RevisionInfoTableModel(List<String> revisions) {
        this.revisions = revisions;
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
        if (columnIndex == 0) {
            return RevisionInfo.class;
        }

        return super.getColumnClass(columnIndex);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Object val = null;

        RevisionInfo ri = getRevisionAt(rowIndex);
        if (ri == null) {
            return "";
        }

        switch (columnIndex) {
            case 0:
                val = ri.getHash();
                break;

            case 1:
                val = ri.getMessage();
                break;

            case 2:
                val = ri.getCommitterName();
                break;

            case 3:
                val = formatTime(ri.getTime());
                break;

            case 4:
                val = ri.getServerId();
                break;

            default:
                throw new IllegalArgumentException("unknown column number " + columnIndex);
        }

        return val;
    }

    public RevisionInfo getRevisionAt(int row) {
        if (row < 0 || row >= revisions.size()) {
            return null;
        }

        RevisionInfo revisionInfo = new RevisionInfo();
        JSONObject rj = new JSONObject(revisions.get(row));

        revisionInfo.setCommitterEmail((String) rj.get("CommitterEmail"));
        revisionInfo.setCommitterName((String) rj.get("CommitterName"));
        revisionInfo.setHash((String) rj.get("Hash"));
        revisionInfo.setMessage((String) rj.get("Message"));
        revisionInfo.setTime((Long) rj.get("Time"));

        return revisionInfo;
    }

    private String formatTime(long t) {
//        long now = System.currentTimeMillis();

        return df.format(new Date(t));

//        if (t > now) {
//            return df.format(new Date(t));
//        }
//
//        Period period = new Period(t, now);
//
//        System.out.println("years: " + period.getYears() + " months: " + period.getMonths() + " days: " + period.getDays() + " hours: " + period.getHours() + " minutes: " + period.getMinutes());
//
//        int years = period.getYears();
//        int months = period.getMonths();
//        int days = period.getDays();
//
//        if (years > 0 || months > 0 || days > 0) {
//            return df.format(new Date(t));
//        }
//
//        if (period.getHours() > 0) {
//            return period.getHours() + " hours ago";
//        }
//
//        if (period.getMinutes() > 0) {
//            return period.getMinutes() + " minutes ago";
//        }
//
//        return period.getSeconds() + " seconds ago";
    }
}
