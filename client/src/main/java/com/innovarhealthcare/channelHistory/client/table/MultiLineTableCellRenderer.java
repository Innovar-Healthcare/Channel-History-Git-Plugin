package com.innovarhealthcare.channelHistory.client.table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JTextArea;
import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class MultiLineTableCellRenderer extends JTextArea implements TableCellRenderer {
    private static final Logger logger = LoggerFactory.getLogger(MultiLineTableCellRenderer.class);

    public MultiLineTableCellRenderer() {
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(true);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        String text = value != null ? value.toString() : "";
        setText(text);

        if (isSelected) {
            setBackground(table.getSelectionBackground());
            setForeground(table.getSelectionForeground());
        } else {
            setBackground(table.getBackground());
            setForeground(table.getForeground());
        }
        setFont(table.getFont());

        return this;
    }
}