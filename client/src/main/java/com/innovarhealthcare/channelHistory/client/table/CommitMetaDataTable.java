package com.innovarhealthcare.channelHistory.client.table;

import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTable;

import org.jdesktop.swingx.decorator.ComponentAdapter;
import org.jdesktop.swingx.decorator.Highlighter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.event.ChangeListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import java.awt.Component;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class CommitMetaDataTable extends MirthTable {
    private static final Logger logger = LoggerFactory.getLogger(CommitMetaDataTable.class);
    private final MultiLineTableCellRenderer messageRenderer = new MultiLineTableCellRenderer();
    private String highlightValue = "";

    public CommitMetaDataTable() {
        super();
        initializeUI();
    }

    private void initializeUI() {
        try {
            // Add highlighter for first column
            Highlighter commitIdHighlighter = new Highlighter() {
                @Override
                public Component highlight(Component component, ComponentAdapter adapter) {
                    if (adapter.column == 0 && highlightValue != null && highlightValue.equals(adapter.getValue())) {
                        component.setBackground(UIConstants.LIGHT_YELLOW); // Light yellow highlight
                    }
                    return component;
                }

                @Override
                public void addChangeListener(ChangeListener changeListener) {

                }

                @Override
                public void removeChangeListener(ChangeListener changeListener) {

                }

                @Override
                public ChangeListener[] getChangeListeners() {
                    return new ChangeListener[0];
                }
            };

            setHighlighters(commitIdHighlighter);

            // Configure table appearance
            setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
            setRowSelectionAllowed(true);
            setColumnSelectionAllowed(false);
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            setRowHeight(20);
        } catch (Exception e) {
            logger.error("Failed to initialize CommitMetaDataTable UI", e);
        }
    }

    @Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);
        configureColumns();
        applyMessageRenderer();
        updateRowHeights();
    }

    private void configureColumns() {
        if (getColumnCount() == 5) { // Ensure model has expected columns
            try {
                TableColumnModel columnModel = getColumnModel();
                columnModel.getColumn(0).setPreferredWidth(150); // Commit Id (short hash)
                columnModel.getColumn(1).setPreferredWidth(250); // Message
                columnModel.getColumn(2).setPreferredWidth(40); // Committer
                columnModel.getColumn(3).setPreferredWidth(80); // Date
                columnModel.getColumn(4).setPreferredWidth(150); // Server Id
            } catch (Exception e) {
                logger.error("Failed to configure table columns", e);
            }
        }
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        if (column == 1) {
            return messageRenderer;
        }
        return super.getCellRenderer(row, column);
    }

    private void applyMessageRenderer() {
        try {
            if (getColumnCount() >= 2) {
                getColumnModel().getColumn(1).setCellRenderer(messageRenderer);
            }
        } catch (Exception e) {
            logger.error("Failed to apply MultiLineTableCellRenderer", e);
        }
    }

    private void updateRowHeights() {
        SwingUtilities.invokeLater(() -> {
            try {
                int column = 1; // Message column
                if (getColumnCount() <= column) {
                    return;
                }

                int columnWidth = getColumnModel().getColumn(column).getWidth();

                for (int row = 0; row < getRowCount(); row++) {
                    Object value = getValueAt(row, column);
                    String text = value != null ? value.toString() : "";

                    JTextArea tempArea = new JTextArea(text);
                    tempArea.setLineWrap(true);
                    tempArea.setWrapStyleWord(true);
                    tempArea.setFont(getFont());
                    tempArea.setSize(columnWidth, Short.MAX_VALUE);

                    int preferredHeight = tempArea.getPreferredSize().height;
                    int currentHeight = getRowHeight(row);
                    int finalHeight = Math.max(preferredHeight, 20);

                    if (currentHeight != finalHeight) {
                        setRowHeight(row, finalHeight);
                    }

//                    logger.debug("Row {} -> Height: {}, Text: {}", row, finalHeight, text);
                }
            } catch (Exception e) {
                logger.error("Failed to update row heights", e);
            }
        });
    }


    @Override
    public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
        if (rowIndex >= 0 && rowIndex < getRowCount() && columnIndex >= 0 && columnIndex < getColumnCount()) {
            super.changeSelection(rowIndex, columnIndex, true, false);
        }
    }

    /**
     * Sets the value to highlight in the first column and refreshes the table.
     *
     * @param value The string to match for highlighting
     */
    public void setHighlightValue(String value) {
        this.highlightValue = value != null ? value : "";
        repaint(); // Refresh the table to apply new highlighting
    }
}