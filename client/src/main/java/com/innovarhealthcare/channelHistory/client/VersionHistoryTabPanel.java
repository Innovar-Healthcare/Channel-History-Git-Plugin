package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.client.util.VersionControlUtil;
import com.innovarhealthcare.channelHistory.server.exception.GitRepositoryException;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.interfaces.channelHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.RevisionInfo;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.AbstractChannelTabPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.util.DisplayUtil;

import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.User;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import net.miginfocom.swing.MigLayout;
import org.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

/**
 * @author Kiran Ayyagari (kayyagari@apache.org)
 */
public class VersionHistoryTabPanel extends AbstractChannelTabPanel {
    private final String MODE = VersionControlConstants.MODE_CHANNEL;
    private static Logger log = Logger.getLogger(VersionHistoryTabPanel.class);

    private JPanel disablePanel;
    private JPanel actionPanel;
    private JPanel historyPanel;
    private JScrollPane historyScrollPane;

    private RevisionInfoTable tblRevisions;
    private JButton differenceButton;
    private JButton commitPushButton;
    private JButton pullButton;

    private channelHistoryServletInterface gitServlet;
    private static final DateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");

    private String cid;

    private JPopupMenu popupMenu;

    private JMenuItem revertRevision;
    private JMenuItem mnuShowDiff;

    private final Frame parent;

    public VersionHistoryTabPanel(Frame parent) {
        this.parent = parent;

        initComponents();
        initLayout();

        parent.addTask("importChannelFromRepo", "Import Channel From Repo", "Import Channel From Repo", "", new ImageIcon(Frame.class.getResource("images/report_go.png")), parent.channelPanel.channelTasks, parent.channelPanel.channelPopupMenu, this);
    }

    @Override
    public void load(Channel channel) {
        if (VersionControlUtil.isDisableVersionControl(parent.mirthClient)) {
            disablePanel.setVisible(true);
            actionPanel.setVisible(false);
            historyPanel.setVisible(false);
        } else {
            disablePanel.setVisible(false);
            actionPanel.setVisible(true);
            historyPanel.setVisible(true);

            commitPushButton.setVisible(VersionControlUtil.isAutoCommitDisable(parent.mirthClient));

            cid = channel.getId();
            this.loadHistory(false);
        }
    }

    @Override
    public void save(Channel channel) {
        log.info("saving channel " + channel.getId());
    }

    private void initComponents() {

        setBackground(UIConstants.BACKGROUND_COLOR);

        // Disable
        disablePanel = new JPanel();
        disablePanel.setBackground(this.getBackground());
        disablePanel.setBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createMatteBorder(0, 0, 0, 0, new Color(204, 204, 204)),
                        VersionControlUtil.getAlertText(),
                        TitledBorder.DEFAULT_JUSTIFICATION,
                        1,
                        new Font("Tahoma", 1, 15)
                )
        );

        // Action
        actionPanel = new JPanel();
        actionPanel.setBackground(this.getBackground());
        actionPanel.setBorder(BorderFactory.createTitledBorder("Action"));

        differenceButton = new JButton("Diff");
        differenceButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                showDiffLastChangeWindow();
            }
        });

        commitPushButton = new JButton("Commit & Push");
        commitPushButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                commitThenPush();
            }
        });

        pullButton = new JButton("Pull");
        pullButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                loadHistory();
            }
        });

        // History
        historyPanel = new JPanel();
        historyPanel.setBackground(this.getBackground());
        historyPanel.setBorder(BorderFactory.createTitledBorder("History"));

        tblRevisions = new RevisionInfoTable();
        tblRevisions.setRowSelectionAllowed(true);
        tblRevisions.setColumnSelectionAllowed(false);
        tblRevisions.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tblRevisions.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) { // Avoid duplicate events
                    differenceButton.setEnabled(tblRevisions.getSelectedRowCount() == 1);
                }
            }
        });

        historyScrollPane = new JScrollPane(tblRevisions);

        popupMenu = new JPopupMenu();

        revertRevision = new JMenuItem("Revert to revision");
        revertRevision.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = tblRevisions.getSelectedRow();

                RevisionInfoTableModel model = (RevisionInfoTableModel) tblRevisions.getModel();
                RevisionInfo rev = model.getRevisionAt(row);
                revert(cid, rev.getHash());
            }
        });
        popupMenu.add(revertRevision);

        mnuShowDiff = new JMenuItem("Show Diff");
        mnuShowDiff.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showDiffWindow();
            }
        });
        popupMenu.add(mnuShowDiff);

        tblRevisions.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopupEvent(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopupEvent(e);
            }

            public void handlePopupEvent(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    revertRevision.setVisible(tblRevisions.getSelectedRowCount() == 1);
                    mnuShowDiff.setVisible(tblRevisions.getSelectedRowCount() == 2);

                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill", "", "[][][][grow]"));

        actionPanel.setLayout(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 3, fill, gap 6", "[]12[]12[][grow]"));
        actionPanel.add(differenceButton, "newline, w 108!");
        actionPanel.add(commitPushButton, "w 108!");
        actionPanel.add(pullButton, "w 108!");

        historyPanel.setLayout(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 3, fill, gap 6", "[grow][]"));
        historyPanel.add(historyScrollPane, "sy, grow");

        disablePanel.setLayout(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 3, fill, gap 6", "[]12[]12[][grow]"));

        add(actionPanel, "growx, sx");
        add(historyPanel, "newline, grow, pushx");
        add(disablePanel, "newline, growx, sx");
    }

    public void importChannelFromRepo() {
        if (VersionControlUtil.isDisableVersionControl(parent.mirthClient)) {
            showError(VersionControlUtil.getAlertText());
        } else {
            new ImportChannelDialog(parent);
        }
    }

    public void loadHistory() {
        this.loadHistory(true);
    }

    public void loadHistory(boolean shouldNotifyOnComplete) {
        SwingUtilities.invokeLater(new LoadGitHistoryRunnable(shouldNotifyOnComplete));
    }

    private void showDiffLastChangeWindow() {
        RevisionInfoTableModel model = (RevisionInfoTableModel) tblRevisions.getModel();
        RevisionInfo lastChange = model.getRevisionAt(tblRevisions.getSelectedRow());

        if (lastChange == null) {
            showError("No channel revision selected");
            return;
        }

        try {
            Client client = parent.mirthClient;
            String currentUserName = client.getCurrentUser().getUsername();

            Channel leftCh = client.getChannel(cid, false);
            leftCh.clearExportData();
            String left = ObjectXMLSerializer.getInstance().serialize(leftCh);

            String right = gitServlet.getContent(cid, lastChange.getHash(), MODE);
            Channel rightCh = parse(right, lastChange.getShortHash());

            String leftLabel = leftCh.getName() + " - Current - Editing by " + currentUserName;
            String rightLabel = leftCh.getName() + " - Time: " + df.format(new Date(lastChange.getTime())) + " - Committed by " + lastChange.getCommitterName();

            DiffWindow dw = DiffWindow.create("Channel Diff", leftLabel, rightLabel, leftCh, rightCh, left, right, parent);
            dw.setSize(parent.getWidth() - 10, parent.getHeight() - 10);
            dw.setVisible(true);
        } catch (Exception e) {
            showError("Failed to show difference in channel");
        }
    }

    private void showDiffWindow() {
        popupMenu.setVisible(false);
        int[] rows = tblRevisions.getSelectedRows();
        RevisionInfoTableModel model = (RevisionInfoTableModel) tblRevisions.getModel();
        RevisionInfo ri1 = model.getRevisionAt(rows[0]);
        RevisionInfo ri2 = model.getRevisionAt(rows[1]);

        try {
            String left = gitServlet.getContent(cid, ri1.getHash(), MODE);
            Channel leftCh = parse(left, ri1.getShortHash());
            String right = gitServlet.getContent(cid, ri2.getHash(), MODE);
            Channel rightCh = parse(right, ri2.getShortHash());

            String labelPrefix = leftCh.getName();
            String leftLabel = labelPrefix + " - Time: " + df.format(new Date(ri1.getTime())) + " - Committed by " + ri1.getCommitterName();
            String rightLabel = labelPrefix + " - Time: " + df.format(new Date(ri2.getTime())) + " - Committed by " + ri1.getCommitterName();

            DiffWindow dw = DiffWindow.create("Channel Diff", leftLabel, rightLabel, leftCh, rightCh, left, right, parent);
            dw.setSize(parent.getWidth() - 10, parent.getHeight() - 10);
            dw.setVisible(true);
        } catch (Exception e) {
            showError("Failed to show difference in channel");
        }
    }

    private Channel parse(String xml, String rev) {
        Channel ch = ObjectXMLSerializer.getInstance().deserialize(xml, Channel.class);
        if (ch instanceof InvalidChannel) {
            throw new IllegalStateException("could not parse channel at revision " + rev);
        }

        return ch;
    }

    private void revert(String channelId, String rev) {
        int option = JOptionPane.showConfirmDialog(parent, "Would you like to revert channel to this revision?", "Select an Option", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            Client client = parent.mirthClient;

            try {
                String xml = gitServlet.getContent(channelId, rev, MODE);
                Channel channel = parse(xml, rev);
                if (channel == null) {
                    showError("Channel is null");
                    return;
                }

                if (client.updateChannel(channel, true, null)) {
                    // store channel commit id at here
                    VersionControlUtil.setChannelCommitId(parent.mirthClient, channelId, rev);

                    JOptionPane.showMessageDialog(parent, "Exit channel edit screen without saving to complete reverting channel",
                            "Successfully Reverted Channel", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (ClientException e) {
                showError("Failed to revert channel");
            }
        }
    }

    private void commitThenPush() {
        Object response = DisplayUtil.showInputDialog(parent, "Enter a comment:", "Commit & Push", JOptionPane.QUESTION_MESSAGE, null, null, "");

        if (response == null) {
            return;
        }

        SwingUtilities.invokeLater(new CommitThenPushChannelRunnable(StringUtils.trim(response.toString())));
    }

    private class LoadGitHistoryRunnable implements Runnable {
        private final boolean shouldNotifyOnComplete;

        LoadGitHistoryRunnable(boolean shouldNotifyOnComplete) {
            this.shouldNotifyOnComplete = shouldNotifyOnComplete;
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
                List<String> revisions = gitServlet.getHistory(cid, MODE);
                RevisionInfoTableModel model = new RevisionInfoTableModel(revisions);
                tblRevisions.setModel(model);

                // check warning if last commit done from other servers
                boolean alertWarning = false;
                RevisionInfo ri = model.getRevisionAt(0);
                if (ri != null) {
                    String commitId = VersionControlUtil.getChannelCommitId(parent.mirthClient, cid);

                    boolean warning = (commitId != null) && !Objects.equals(ri.getHash(), commitId);

                    if (warning) {
                        alertWarning = true;
                        PlatformUI.MIRTH_FRAME.alertWarning(parent, "Remote repository contains a more recent version of this channel, are you sure you want to edit?");
                    }
                }

                if (!alertWarning && shouldNotifyOnComplete) {
                    PlatformUI.MIRTH_FRAME.alertInformation(parent, "History refreshed!");
                }
            } catch (Exception e) {
                RevisionInfoTableModel model = new RevisionInfoTableModel(new ArrayList<>());
                tblRevisions.setModel(model);

                if (shouldNotifyOnComplete) {
                    showError("Failed to pull history channel from repository");
                }
            }
        }
    }

    private class CommitThenPushChannelRunnable implements Runnable {
        private final String message;

        CommitThenPushChannelRunnable(String message) {
            this.message = message;
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
                String chanelId = cid;
                String userId = String.valueOf(parent.mirthClient.getCurrentUser().getId());
                String response = gitServlet.commitAndPushChannel(chanelId, message, userId);

                JSONObject resObj = new JSONObject(response);
                if (resObj.get("validate").equals("success")) {
                    JOptionPane.showMessageDialog(parent, resObj.get("body"),
                            "Channel History", JOptionPane.INFORMATION_MESSAGE);

                    // fetch history panel again at here
                    // thai
                    loadHistory(false);
                } else {
                    JOptionPane.showMessageDialog(parent, "Error: " + resObj.get("body"),
                            "Channel History", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                showError("Failed to commit and push channel to repository");
            }
        }
    }

    private void showError(String msg) {
        PlatformUI.MIRTH_FRAME.alertError(parent, msg);
    }
}
