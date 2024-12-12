package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.client.model.ChannelRepoTableModel;
import com.innovarhealthcare.channelHistory.shared.interfaces.channelHistoryServletInterface;

import com.mirth.connect.model.ChannelDependency;
import org.apache.commons.collections4.CollectionUtils;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTable;

import com.mirth.connect.model.Channel;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.Set;
import java.util.HashSet;

/**
 * @author Thai Tran
 * @create 2024-11-09 12:13 PM
 */
public class ImportChannelDialog extends MirthDialog {
    final boolean DO_ADD = true;

    private MirthTable channelRepoTable;
    private JScrollPane channelsScrollPane;

    private JButton okButton;
    private JButton cancelButton;

    private channelHistoryServletInterface gitServlet;
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

        channelRepoTable = new MirthTable() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        channelRepoTable.setRowSelectionAllowed(true);
        channelRepoTable.setColumnSelectionAllowed(false);

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
                        try {
                            boolean ret = DO_ADD ? doAddChannel(channel) : doImportChannel(channel, true);
                            if (ret) {
                                dispose();

                                parent.channelPanel.doRefreshChannels();
                            }
                        } catch (ClientException e) {
                            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
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
                    gitServlet = parent.mirthClient.getServlet(channelHistoryServletInterface.class);
                }

                // then fetch revisions
                channelRepoTable.setModel(new ChannelRepoTableModel(gitServlet.loadChannelOnRepo()));
            } catch (Exception e) {
                PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
            }
        }
    }

    private boolean doAddChannel(Channel channel) throws ClientException {
        try {
            Client client = parent.mirthClient;

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

            return client.updateChannel(channel, true, null);

        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }

        return true;
    }

    private boolean doImportChannel(Channel importChannel, boolean force) throws ClientException {
        try {
            Client client = parent.mirthClient;

            String channelName = importChannel.getName();
            String channelId = importChannel.getId();
            String tempId = client.getGuid();
            importChannel.setRevision(0);

            Channel idChannelMatch = getChannelById(channelId);
            Channel nameChannelMatch = getChannelByName(channelName);

            // Check if channel id already exists
            if (idChannelMatch != null) {
                if (!force) {
                    importChannel.setId(tempId);
                } else {
                    importChannel.setRevision(idChannelMatch.getRevision());
                }
            }

            // Check if channel name already exists
            if (nameChannelMatch != null) {
                if (!force) {
                    importChannel.setName(tempId);
                } else {
                    importChannel.setRevision(nameChannelMatch.getRevision());
                    importChannel.setId(nameChannelMatch.getId());
                }
            }

            importChannelDependencies(importChannel);

            client.updateChannel(importChannel, true, null);
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }

        return true;
    }

    private void importChannelDependencies(Channel importChannel) throws ClientException {
        Client client = parent.mirthClient;

        try {
            if (CollectionUtils.isNotEmpty(importChannel.getExportData().getDependentIds()) || CollectionUtils.isNotEmpty(importChannel.getExportData().getDependencyIds())) {
                Set<ChannelDependency> cachedChannelDependencies = client.getChannelDependencies();
                Set<ChannelDependency> channelDependencies = new HashSet<ChannelDependency>(cachedChannelDependencies);

                if (CollectionUtils.isNotEmpty(importChannel.getExportData().getDependentIds())) {
                    for (String dependentId : importChannel.getExportData().getDependentIds()) {
                        if (StringUtils.isNotBlank(dependentId) && !StringUtils.equals(dependentId, importChannel.getId())) {
                            channelDependencies.add(new ChannelDependency(dependentId, importChannel.getId()));
                        }
                    }
                }

                if (CollectionUtils.isNotEmpty(importChannel.getExportData().getDependencyIds())) {
                    for (String dependencyId : importChannel.getExportData().getDependencyIds()) {
                        if (StringUtils.isNotBlank(dependencyId) && !StringUtils.equals(dependencyId, importChannel.getId())) {
                            channelDependencies.add(new ChannelDependency(importChannel.getId(), dependencyId));
                        }
                    }
                }

                if (!channelDependencies.equals(cachedChannelDependencies)) {
                    try {
                        client.setChannelDependencies(channelDependencies);
                    } catch (ClientException e) {
                        PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
                    }
                }

                importChannel.getExportData().clearAllExceptMetadata();
            }
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }
    }

    /**
     * Checks to see if the passed in channel id already exists
     */
    private Channel getChannelById(String id) throws ClientException {
        Client client = parent.mirthClient;
        try {
            for (Channel channel : client.getAllChannels()) {
                if (channel.getId().equalsIgnoreCase(id)) {
                    return channel;
                }
            }
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }

        return null;
    }

    private Channel getChannelByName(String name) throws ClientException {
        Client client = parent.mirthClient;

        try {
            for (Channel channel : client.getAllChannels()) {
                if (channel.getName().equalsIgnoreCase(name)) {
                    return channel;
                }
            }
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }

        return null;
    }
}