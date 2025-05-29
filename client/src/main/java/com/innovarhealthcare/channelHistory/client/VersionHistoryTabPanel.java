package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.client.dialog.ImportChannelDialog;
import com.innovarhealthcare.channelHistory.client.model.CommitMetaDataTableModel;
import com.innovarhealthcare.channelHistory.client.table.CommitMetaDataTable;
import com.innovarhealthcare.channelHistory.client.util.VersionControlUtil;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.interfaces.ChannelHistoryServletInterface;

import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.AbstractChannelTabPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;

import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.ImageIcon;

import java.awt.Color;
import java.awt.Font;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Properties;
import java.util.Date;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;

import net.miginfocom.swing.MigLayout;
import org.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class VersionHistoryTabPanel extends AbstractChannelTabPanel {
    private final String MODE = VersionControlConstants.MODE_CHANNEL;
    private static Logger logger = Logger.getLogger(VersionHistoryTabPanel.class);

    private JPanel disablePanel;
    private JPanel actionPanel;
    private JPanel historyPanel;
    private JScrollPane historyScrollPane;

    private CommitMetaDataTable tblCommitMetaData;
    private JButton differenceButton;
    private JButton commitPushButton;
    private JButton pullButton;

    private ChannelHistoryServletInterface gitServlet;
    private static final DateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");

    private String cid;

    private JPopupMenu popupMenu;

    private JMenuItem revertRevision;
    private JMenuItem mnuShowDiff;

    private final Frame parent;
    private VersionHistoryProperties versionHistoryProperties;

    public VersionHistoryTabPanel(Frame parent) {
        this.parent = parent;

        initComponents();
        initLayout();

        parent.addTask("importChannelFromRepo", "Import Channel From Repo", "Import Channel From Repo", "", new ImageIcon(Frame.class.getResource("images/report_go.png")), parent.channelPanel.channelTasks, parent.channelPanel.channelPopupMenu, this);

        versionHistoryProperties = new VersionHistoryProperties();
    }

    @Override
    public void load(Channel channel) {
        // load Version History Setting
        // then store
        loadVersionHistoryProperties();

        if (!versionHistoryProperties.isEnableVersionHistory()) {
            disablePanel.setVisible(true);
            actionPanel.setVisible(false);
            historyPanel.setVisible(false);
        } else {
            disablePanel.setVisible(false);
            actionPanel.setVisible(true);
            historyPanel.setVisible(true);

            commitPushButton.setVisible(!versionHistoryProperties.isEnableAutoCommit());

            cid = channel.getId();

            loadHistory(false);
        }
    }

    @Override
    public void save(Channel channel) {
        if (!versionHistoryProperties.isEnableVersionHistory()) {
            return;
        }

        if (!versionHistoryProperties.isEnableAutoCommit()) {
            return;
        }

        String message = "";
        if (versionHistoryProperties.isEnableAutoCommitPrompt()) {
            // show prompt at here
            JTextArea textArea = new JTextArea(5, 30); // 5 rows, 30 columns
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(textArea);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("Enter a comment:"), BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(
                    parent,
                    panel,
                    "Auto Commit",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (result == JOptionPane.OK_OPTION) {
                message = StringUtils.trim(textArea.getText());
            } else {
                message = versionHistoryProperties.getAutoCommitMsg();
            }
        } else {
            message = versionHistoryProperties.getAutoCommitMsg();
        }

        final String workingId = parent.startWorking("Commit & Push " + cid + " channel...");

        String finalMessage = message;
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                final int MAX_TRY = 5;
                int cnt = 0;
                while (parent.isSaveEnabled() && cnt < MAX_TRY) {
                    Thread.sleep(1000); // wait 1 second
                    cnt++;
                }

                if (cnt < MAX_TRY) {
                    try {
                        String response = doCommitAndPushCurrentChannel(finalMessage);

                        JSONObject resObj = new JSONObject(response);
                        if (resObj.get("validate").equals("success")) {
                            if (isShowing()) {
                                loadHistory(false);
                            }
                        } else {
                            logger.error("Failed to commit and push channel to remote repository. Error: " + resObj.get("body"));
                        }
                    } catch (Exception e) {
                        logger.error("Failed to commit and push channel to remote repository. Error: " + e.getMessage());
                    }
                }

                return null;
            }

            @Override
            protected void done() {
                parent.stopWorking(workingId);
            }
        };

        worker.execute();
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
                commitThenPushAction();
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

        tblCommitMetaData = new CommitMetaDataTable();
        tblCommitMetaData.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) { // Avoid duplicate events
                    differenceButton.setEnabled(tblCommitMetaData.getSelectedRowCount() == 1);
                }
            }
        });

        historyScrollPane = new JScrollPane(tblCommitMetaData);

        popupMenu = new JPopupMenu();

        revertRevision = new JMenuItem("Revert to revision");
        revertRevision.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = tblCommitMetaData.getSelectedRow();

                CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
                CommitMetaData meta = model.getCommitMetaDataAt(row);
                revert(cid, meta.getHash());
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

        tblCommitMetaData.addMouseListener(new MouseAdapter() {
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
                    revertRevision.setVisible(tblCommitMetaData.getSelectedRowCount() == 1);
                    mnuShowDiff.setVisible(tblCommitMetaData.getSelectedRowCount() == 2);

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

    private void loadVersionHistoryProperties() {
        Properties properties;
        try {
            Client client = parent.mirthClient;
            properties = client.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
        } catch (ClientException e) {
            properties = new Properties();
        }

        versionHistoryProperties.fromProperties(properties);
    }

    public void importChannelFromRepo() {
        // always load git setting first
        loadVersionHistoryProperties();

        if (versionHistoryProperties.isEnableVersionHistory()) {
            new ImportChannelDialog(parent);
        } else {
            showError(VersionControlUtil.getAlertText());
        }
    }

    public void loadHistory() {
        this.loadHistory(true);
    }

    public void loadHistory(boolean shouldNotifyOnComplete) {
        SwingUtilities.invokeLater(new LoadGitHistoryRunnable(shouldNotifyOnComplete));
    }

    private void showDiffLastChangeWindow() {
        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
        CommitMetaData lastChange = model.getCommitMetaDataAt(tblCommitMetaData.getSelectedRow());

        if (lastChange == null) {
            showError("No channel revision selected");
            return;
        }

        try {
            Client client = parent.mirthClient;
            String currentUserName = client.getCurrentUser().getUsername();

            Channel leftCh = client.getChannel(cid, false);
            String left = ObjectXMLSerializer.getInstance().serialize(leftCh);

            String right = gitServlet.getContent(cid, lastChange.getHash(), MODE);
            Channel rightCh = parse(right, lastChange.getShortHash());

            String leftLabel = leftCh.getName() + " - Current - Editing by " + currentUserName;
            String rightLabel = leftCh.getName() + " - Time: " + df.format(new Date(lastChange.getTimestamp())) + " - Committed by " + lastChange.getCommitter();

            DiffWindow dw = DiffWindow.create("Channel Diff", leftLabel, rightLabel, leftCh, rightCh, left, right, parent);
            dw.setSize(parent.getWidth() - 10, parent.getHeight() - 10);
            dw.setVisible(true);
        } catch (Exception e) {
            showError("Failed to show difference in channel");
        }
    }

    private void showDiffWindow() {
        popupMenu.setVisible(false);
        int[] rows = tblCommitMetaData.getSelectedRows();
        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
        CommitMetaData ri1 = model.getCommitMetaDataAt(rows[0]);
        CommitMetaData ri2 = model.getCommitMetaDataAt(rows[1]);

        try {
            String left = gitServlet.getContent(cid, ri1.getHash(), MODE);
            Channel leftCh = parse(left, ri1.getShortHash());
            String right = gitServlet.getContent(cid, ri2.getHash(), MODE);
            Channel rightCh = parse(right, ri2.getShortHash());

            String labelPrefix = leftCh.getName();
            String leftLabel = labelPrefix + " - Time: " + df.format(new Date(ri1.getTimestamp())) + " - Committed by " + ri1.getCommitter();
            String rightLabel = labelPrefix + " - Time: " + df.format(new Date(ri2.getTimestamp())) + " - Committed by " + ri1.getCommitter();

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

    private void commitThenPushAction() {
        if (parent.isSaveEnabled()) {
            showInformation("This channel has been modified. You must save the channel changes before you can commit to remote repository");
            return;
        }

        JTextArea textArea = new JTextArea(5, 30); // 5 rows, 30 columns
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Enter a comment:"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "Commit & Push",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        SwingUtilities.invokeLater(new CommitThenPushChannelRunnable(StringUtils.trim(textArea.getText())));
    }

    private class LoadGitHistoryRunnable implements Runnable {
        private final boolean shouldNotifyOnComplete;

        LoadGitHistoryRunnable(boolean shouldNotifyOnComplete) {
            this.shouldNotifyOnComplete = shouldNotifyOnComplete;
        }

        @Override
        public void run() {
            try {
                Client client = parent.mirthClient;
                // initialize once
                // doing here because do not want to delay the startup of MC client which takes several seconds to start.
                if (gitServlet == null) {
                    gitServlet = client.getServlet(ChannelHistoryServletInterface.class);
                }

                // then fetch revisions
                List<String> revisions = gitServlet.getHistory(cid, MODE);
                CommitMetaDataTableModel model = new CommitMetaDataTableModel(revisions);
                tblCommitMetaData.setModel(model);

                // check warning if last commit done from other servers
                String commitId = VersionControlUtil.getChannelCommitId(client, cid);
                tblCommitMetaData.setHighlightValue(commitId);

                boolean alertWarning = false;
                CommitMetaData meta = model.getCommitMetaDataAt(0);
                if (meta != null) {
                    boolean warning = (commitId != null) && !Objects.equals(meta.getHash(), commitId);

                    if (warning) {
                        alertWarning = true;
                        PlatformUI.MIRTH_FRAME.alertWarning(parent, "Remote repository contains a more recent version of this channel, are you sure you want to edit?");
                    }
                }

                if (!alertWarning && shouldNotifyOnComplete) {
                    showInformation("History refreshed!");
                }
            } catch (Exception e) {
                CommitMetaDataTableModel model = new CommitMetaDataTableModel(new ArrayList<>());
                tblCommitMetaData.setModel(model);

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
                String response = doCommitAndPushCurrentChannel(message);

                JSONObject resObj = new JSONObject(response);
                if (resObj.get("validate").equals("success")) {
                    showInformation(resObj.get("body").toString());

                    // fetch history panel again at here
                    loadHistory(false);
                } else {
                    showError("Error: " + resObj.get("body"));
                }
            } catch (Exception e) {
                showError("Failed to commit and push channel to remote repository. Error: " + e.getMessage());
            }
        }
    }

    private void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(parent, msg);
    }

    private void showError(String msg) {
        PlatformUI.MIRTH_FRAME.alertError(parent, msg);
    }

    private String doCommitAndPushCurrentChannel(String message) throws ClientException {
        Client client = parent.mirthClient;
        ChannelHistoryServletInterface servlet = client.getServlet(ChannelHistoryServletInterface.class);
        Channel channel = client.getChannel(cid, false);
        String userId = String.valueOf(client.getCurrentUser().getId());

        return servlet.commitAndPushChannel(channel, message, userId);
    }
}
