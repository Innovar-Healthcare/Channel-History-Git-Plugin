package com.innovarhealthcare.channelHistory.client.model;

import com.mirth.connect.model.codetemplates.CodeTemplate;
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
public class CodeTemplateRepoTableModel extends AbstractTableModel {
    private static final Logger logger = LoggerFactory.getLogger(CodeTemplateRepoTableModel.class);
    private static final String[] columnNames = {"Code Template Id", "Code Template Name", "Last Commit Id"};
    private final List<CodeTemplateEntry> entries;

    private static class CodeTemplateEntry {
        CodeTemplate template;
        String lastCommitId;

        CodeTemplateEntry(CodeTemplate template, String lastCommitId) {
            this.template = template;
            this.lastCommitId = lastCommitId != null ? lastCommitId : "(unknown)";
        }
    }

    public CodeTemplateRepoTableModel(List<String> jsonList) {
        this.entries = new ArrayList<>();
        for (String json : jsonList) {
            try {
                JSONObject obj = new JSONObject(json);
                String content = obj.has("content") && !obj.isNull("content") ? obj.getString("content") : "";
                String lastCommitId = obj.has("lastCommitId") && !obj.isNull("lastCommitId") ? obj.getString("lastCommitId") : null;
                CodeTemplate template = stringToCodeTemplate(content);
                if (template != null) {
                    entries.add(new CodeTemplateEntry(template, lastCommitId));
                }
            } catch (Exception e) {
                logger.error("Failed to parse JSON: {}", json, e);
                entries.add(new CodeTemplateEntry(null, "(error)"));
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
        CodeTemplateEntry entry = entries.get(rowIndex);
        CodeTemplate template = entry.template;

        switch (columnIndex) {
            case 0: // Code Template Id
                return template != null ? template.getId() : "(error)";
            case 1: // Code Template Name
                return template != null ? template.getName() : "(error)";
            case 2: // Last Commit Id
                return entry.lastCommitId;
            default:
                throw new IllegalArgumentException("Unknown column number: " + columnIndex);
        }
    }

    public CodeTemplate getCodeTemplateAt(int row) {
        if (row < 0 || row >= entries.size()) {
            return null;
        }
        return entries.get(row).template;
    }

    public String getLastCommitIdAt(int row) {
        if (row < 0 || row >= entries.size()) {
            return null;
        }
        return entries.get(row).lastCommitId;
    }

    private CodeTemplate stringToCodeTemplate(String xml) {
        try {
            CodeTemplate template = ObjectXMLSerializer.getInstance().deserialize(xml, CodeTemplate.class);
            if (template == null || template.getId() == null || template.getName() == null) {
                logger.warn("Invalid code template XML: {}", xml);
                return null;
            }
            return template;
        } catch (Exception e) {
            logger.warn("Failed to deserialize code template XML: {}", xml, e);
            return null;
        }
    }
}