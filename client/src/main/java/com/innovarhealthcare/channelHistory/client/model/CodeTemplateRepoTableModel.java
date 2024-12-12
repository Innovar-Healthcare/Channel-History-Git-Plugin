package com.innovarhealthcare.channelHistory.client.model;

import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class CodeTemplateRepoTableModel extends AbstractTableModel {
    private static final String[] columnNames = {"Code Template Id", "Code Template Name"};
    private final List<String> list;

    public CodeTemplateRepoTableModel(List<String> list) {
        this.list = list;
    }

    @Override
    public int getRowCount() {
        return list.size();
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
    public Object getValueAt(int rowIndex, int columnIndex) {
        CodeTemplate template = stringToCodeTemplate(list.get(rowIndex));

        if (template == null) {
            return "";
        }

        switch (columnIndex) {
            case 0:
                return template.getId();
            case 1:
                return template.getName();

            default:
                throw new IllegalArgumentException("unknown column number " + columnIndex);
        }
    }

    public CodeTemplate getCodeTemplateAt(int row) {
        if (row >= list.size()) {
            return null;
        }

        return stringToCodeTemplate(list.get(row));
    }

    private CodeTemplate stringToCodeTemplate(String xml) {
        return ObjectXMLSerializer.getInstance().deserialize(xml, CodeTemplate.class);
    }
}
