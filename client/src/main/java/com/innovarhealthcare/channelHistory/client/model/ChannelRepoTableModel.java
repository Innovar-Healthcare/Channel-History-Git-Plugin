package com.innovarhealthcare.channelHistory.client.model;

import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import javax.swing.table.AbstractTableModel;

import java.util.List;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class ChannelRepoTableModel extends AbstractTableModel {
    private static final String[] columnNames = {"Channel Id", "Channel Name"};
    private final List<String> list;

    public ChannelRepoTableModel(List<String> list) {
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
        Channel channel = stringToChannel(list.get(rowIndex));

        if (channel == null) {
            return "";
        }

        switch (columnIndex) {
            case 0:
                return channel.getId();
            case 1:
                return channel.getName();

            default:
                throw new IllegalArgumentException("unknown column number " + columnIndex);
        }
    }

    public Channel getChannelAt(int row) {
        if (row >= list.size()) {
            return null;
        }

        return stringToChannel(list.get(row));
    }

    private Channel stringToChannel(String xml) {
        Channel ch = ObjectXMLSerializer.getInstance().deserialize(xml, Channel.class);
        if (ch instanceof InvalidChannel) {
            return null;
        }

        return ch;
    }
}
