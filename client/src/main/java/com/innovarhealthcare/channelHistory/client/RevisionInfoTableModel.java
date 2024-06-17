/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
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
        if(columnIndex == 0) {
            return RevisionInfo.class;
        }

        return super.getColumnClass(columnIndex);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Object val = null;
        String r = revisions.get(rowIndex);
        JSONObject rj = new JSONObject(r);

        switch (columnIndex) {
        case 0:
            val = rj.get("Hash");
            break;

        case 1:
            val = rj.get("Message");
            break;

        case 2:
            val = rj.get("CommitterName");
            break;

        case 3:

            val = formatTime((Long) rj.get("Time"));
            break;
        case 4:
            String msg = (String) rj.get("Message");
            val = "";
            if (msg.contains("auto-committed by Innovar Healthcare Git Plugin on Server Id")) {
                int start = msg.indexOf("auto-committed by Innovar Healthcare Git Plugin on Server Id");
                start = (int) start + 61;
                val = msg.substring(start);
            }

            break;

        default:
            throw new IllegalArgumentException("unknown column number " + columnIndex);
        }
        return val;
    }

    public RevisionInfo getRevisionAt(int row) {
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
        Period period = new Period(t, System.currentTimeMillis());
        String txt = null;
        int hours = period.getHours();
        if(hours > 0) {
            txt = df.format(new Date(t));
        }
        else {
            int min = period.getMinutes();
            if(min > 0) {
                txt = min + " minutes ago";
            }
            else {
                txt = period.getSeconds() + " seconds ago";
            }
        }

        return txt;
    }
}
