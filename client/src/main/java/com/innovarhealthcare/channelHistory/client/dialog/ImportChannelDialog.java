package com.innovarhealthcare.channelHistory.client.dialog;

import com.innovarhealthcare.channelHistory.client.model.ChannelRepoTableModel;
import com.innovarhealthcare.channelHistory.client.table.ChannelRepoTable;
import com.innovarhealthcare.channelHistory.client.util.VersionControlUtil;
import com.innovarhealthcare.channelHistory.shared.interfaces.ChannelHistoryServletInterface;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTable;
import com.mirth.connect.model.Channel;

import net.miginfocom.swing.MigLayout;

import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.JSeparator;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Thai Tran
 * @create 2024-11-09 12:13 PM
 */
public class ImportChannelDialog extends MirthDialog {
    private MirthTable channelRepoTable;
    private JScrollPane channelsScrollPane;

    private JButton okButton;
    private JButton cancelButton;

    private ChannelHistoryServletInterface gitServlet;
    private final Frame parent;

    public ImportChannelDialog(Frame parent) {
        super(parent, true);

        this.parent = parent;

        initComponents();
        initLayout();

        // start thread to load channels on repo
        SwingUtilities.invokeLater(new LoadChannelInRepoRunnable());

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Import Channel From Repo");
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        channelRepoTable = new ChannelRepoTable();

        channelsScrollPane = new JScrollPane(channelRepoTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        channelsScrollPane.setPreferredSize(new Dimension(600, 300));

        okButton = new JButton("Import");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                int row = channelRepoTable.getSelectedRow();

                if (row < 0) {
                    PlatformUI.MIRTH_FRAME.alertInformation(parent, "You should select at least one channel!");
                } else {
                    ChannelRepoTableModel model = (ChannelRepoTableModel) channelRepoTable.getModel();
                    Channel channel = model.getChannelAt(row);
                    if (channel != null) {
                        boolean ret = doAddChannel(channel);
                        if (ret) {
                            // store channel commit id at here
                            VersionControlUtil.setChannelCommitId(parent.mirthClient, channel.getId(), model.getLastCommitIdAt(row));

                            dispose();
                        }
                    } else {
                        PlatformUI.MIRTH_FRAME.alertError(parent, "Channel is null");
                    }
                }
            }
        });

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                dispose();
            }
        });
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 8, novisualpadding, hidemode 3, fill", "", "[grow][][]"));

        add(channelsScrollPane, "grow, push");

        add(new JSeparator(), "newline, growx");

        add(okButton, "newline, w 120!, sx, right, split");
        add(cancelButton, "w 51!");
    }

    private class LoadChannelInRepoRunnable implements Runnable {
        LoadChannelInRepoRunnable() {
        }

        @Override
        public void run() {
            try {
                // initialize once
                // doing here because do not want to delay the startup of MC client which takes several seconds to start.
                if (gitServlet == null) {
                    gitServlet = parent.mirthClient.getServlet(ChannelHistoryServletInterface.class);
                }

                // then fetch revisions
                channelRepoTable.setModel(new ChannelRepoTableModel(gitServlet.loadChannelOnRepo()));
            } catch (ClientException e) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Failed to load channels in repository");

                dispose();
            }
        }
    }

    private boolean doAddChannel(Channel channel) {
        try {
            String channelName = channel.getName();
            String channelId = channel.getId();
            Channel idChannelMatch = getChannelById(channelId);
            Channel nameChannelMatch = getChannelByName(channelName);

            if (idChannelMatch != null) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Channel \"" + channelId + "\" already exists.");
                return false;
            }

            if (nameChannelMatch != null) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Channel \"" + channelName + "\" already exists.");
                return false;
            }

            parent.channelPanel.importChannel(channel, true);
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertError(parent, e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Checks to see if the passed in channel id already exists
     */
    private Channel getChannelById(String id) throws ClientException {
        Client client = parent.mirthClient;

        for (Channel channel : client.getAllChannels()) {
            if (channel.getId().equalsIgnoreCase(id)) {
                return channel;
            }
        }

        return null;
    }

    private Channel getChannelByName(String name) throws ClientException {
        Client client = parent.mirthClient;

        for (Channel channel : client.getAllChannels()) {
            if (channel.getName().equalsIgnoreCase(name)) {
                return channel;
            }
        }

        return null;
    }
}