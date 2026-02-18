package com.innovarhealthcare.channelHistory.client.diff;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import com.innovarhealthcare.channelHistory.client.diff.model.ChangeType;
import com.innovarhealthcare.channelHistory.client.diff.model.ScriptEntry;

class ScriptListCellRenderer extends DefaultListCellRenderer {

    private static final Color ADDED_COLOR = new Color(34, 134, 58);
    private static final Color DELETED_COLOR = new Color(203, 36, 49);
    private static final Color MODIFIED_COLOR = new Color(227, 98, 9);
    private static final Color UNCHANGED_COLOR = new Color(117, 117, 117);

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof ScriptEntry) {
            ScriptEntry entry = (ScriptEntry) value;

            String icon = getIconForChangeType(entry.getChangeType());
            setText(icon + " " + entry.getName());

            if (!isSelected) {
                setForeground(getColorForChangeType(entry.getChangeType()));
            }

            setFont(getFont().deriveFont(Font.PLAIN, 13f));
        }

        return this;
    }

    private String getIconForChangeType(ChangeType type) {
        switch (type) {
            case ADDED:
                return "+";
            case DELETED:
                return "-";
            case MODIFIED:
                return "✎";
            default:
                return "✓";
        }
    }

    private Color getColorForChangeType(ChangeType type) {
        switch (type) {
            case ADDED:
                return ADDED_COLOR;
            case DELETED:
                return DELETED_COLOR;
            case MODIFIED:
                return MODIFIED_COLOR;
            default:
                return UNCHANGED_COLOR;
        }
    }
}
