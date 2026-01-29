package com.innovarhealthcare.channelHistory.client.panel;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
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
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import com.innovarhealthcare.channelHistory.client.dialog.DiffWindow;
import com.innovarhealthcare.channelHistory.client.dialog.ImportChannelDialog;
import com.innovarhealthcare.channelHistory.client.model.ChannelWithRaw;
import com.innovarhealthcare.channelHistory.client.model.CommitMetaDataTableModel;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.client.table.CommitMetaDataTable;
import com.innovarhealthcare.channelHistory.client.util.VersionControlUtil;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.innovarhealthcare.channelHistory.shared.util.ResponseUtil;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.AbstractChannelTabPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class ChannelHistoryTabPanel extends AbstractChannelTabPanel {
    private static Logger logger = LogManager.getLogger(ChannelHistoryTabPanel.class);

    private JPanel disablePanel;
    private JPanel actionPanel;
    private JPanel historyPanel;
    private JScrollPane historyScrollPane;

    private CommitMetaDataTable tblCommitMetaData;
    private JButton differenceButton;
    private JButton commitPushButton;
    private JButton pullButton;

    private VersionHistoryServletInterface gitServlet;
    private static final DateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");

    private String cid;

    private JPopupMenu popupMenu;

    private JMenuItem revertRevision;
    private JMenuItem mnuShowDiff;

    private final Frame parent;
    private VersionHistoryProperties versionHistoryProperties;

    public ChannelHistoryTabPanel(Frame parent) {
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

            int result = JOptionPane.showConfirmDialog(parent, panel, "Auto Commit", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                message = StringUtils.trim(textArea.getText());
                if (message.isEmpty()) {
                    message = versionHistoryProperties.getAutoCommitMsg();
                }
            } else {
                message = versionHistoryProperties.getAutoCommitMsg();
            }
        } else {
            message = versionHistoryProperties.getAutoCommitMsg();
        }

        final String workingId = parent.startWorking("Commit & Push " + cid + " channel...");

        String finalMessage = message;
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String errorMessage = null;

            @Override
            protected Void doInBackground() throws Exception {
                // Wait for save to complete (silent, in background)
                final int MAX_TRY = 10;
                int cnt = 0;
                while (parent.isSaveEnabled() && cnt < MAX_TRY) {
                    Thread.sleep(500);
                    cnt++;
                }

                // Check timeout
                if (cnt >= MAX_TRY) {
                    errorMessage = "Cannot commit: Channel is still being saved.\nPlease commit manually.";
                    return null;
                }

                // Extra buffer to ensure save is complete
                Thread.sleep(300);

                // Try to commit
                try {
                    ResponseUtil response = doCommitAndPushCurrentChannel(finalMessage);

                    if (!response.isSuccess()) {
                        errorMessage = "Failed to commit channel:\n" + response.getOperationDetails();
                        logger.error("Commit failed: {}", response.getOperationDetails());
                    } else {
                        logger.info("Commit successful");
                    }

                } catch (Exception e) {
                    errorMessage = "Failed to commit channel:\n" + e.getMessage();
                    logger.error("Commit exception", e);
                }

                return null;
            }

            @Override
            protected void done() {
                parent.stopWorking(workingId);

                if (errorMessage != null) {
                    showError(errorMessage);
                } else {
                    // Reload history if needed (still showing)
                    if (isShowing()) {
                        loadHistory(false);
                    }
                }
            }
        };

        worker.execute();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        // Disable
        disablePanel = new JPanel();
        disablePanel.setBackground(this.getBackground());
        disablePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, new Color(204, 204, 204)), VersionControlUtil.getAlertText(), TitledBorder.DEFAULT_JUSTIFICATION, 1, new Font("Tahoma", 1, 15)));

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

    private void loadHistory() {
        loadHistory(true);
    }

    /**
     * Load git history in background thread
     *
     * @param shouldNotifyOnComplete Whether to show success notification
     */
    private void loadHistory(boolean shouldNotifyOnComplete) {
        new LoadGitHistoryWorker(shouldNotifyOnComplete).execute();
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

            ChannelWithRaw right = VersionHistoryServiceClient.getInstance().loadChannelWithRawFromRepo(cid, lastChange.getHash());

            String leftLabel = leftCh.getName() + " - Current - Editing by " + currentUserName;
            String rightLabel = leftCh.getName() + " - Time: " + df.format(new Date(lastChange.getTimestamp())) + " - Committed by " + lastChange.getCommitter();

            DiffWindow dw = DiffWindow.create("Channel Diff", leftLabel, rightLabel, leftCh, right.getChannel(), left, right.getRawContent(), parent);
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
            ChannelWithRaw left = VersionHistoryServiceClient.getInstance().loadChannelWithRawFromRepo(cid, ri1.getHash());
            ChannelWithRaw right = VersionHistoryServiceClient.getInstance().loadChannelWithRawFromRepo(cid, ri2.getHash());

            Channel leftCh = left.getChannel();
            Channel rightCh = right.getChannel();

            String labelPrefix = leftCh.getName();
            String leftLabel = labelPrefix + " - Time: " + df.format(new Date(ri1.getTimestamp())) + " - Committed by " + ri1.getCommitter();
            String rightLabel = labelPrefix + " - Time: " + df.format(new Date(ri2.getTimestamp())) + " - Committed by " + ri1.getCommitter();

            DiffWindow dw = DiffWindow.create("Channel Diff", leftLabel, rightLabel, leftCh, rightCh, left.getRawContent(), right.getRawContent(), parent);
            dw.setSize(parent.getWidth() - 10, parent.getHeight() - 10);
            dw.setVisible(true);
        } catch (Exception e) {
            showError("Failed to show difference in channel");
        }
    }

    private void revert(String channelId, String rev) {
        int option = JOptionPane.showConfirmDialog(parent, "Would you like to revert channel to this revision?", "Select an Option", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            Client client = parent.mirthClient;

            try {
                Channel channel = VersionHistoryServiceClient.getInstance().loadChannelFromRepo(channelId, rev);

                if (client.updateChannel(channel, true, null)) {
                    // store channel commit id at here
                    VersionControlUtil.setChannelCommitId(parent.mirthClient, channelId, rev);

                    JOptionPane.showMessageDialog(parent, "Exit channel edit screen without saving to complete reverting channel", "Successfully Reverted Channel", JOptionPane.INFORMATION_MESSAGE);
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

        int result = JOptionPane.showConfirmDialog(parent, panel, "Commit & Push", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String message = StringUtils.trim(textArea.getText());
        new CommitThenPushChannelWorker(message).execute();
    }

    /**
     * SwingWorker to load commit history in background
     */
    private class LoadGitHistoryWorker extends SwingWorker<List<CommitMetaData>, Void> {
        private final boolean shouldNotifyOnComplete;

        LoadGitHistoryWorker(boolean shouldNotifyOnComplete) {
            this.shouldNotifyOnComplete = shouldNotifyOnComplete;
        }

        @Override
        protected List<CommitMetaData> doInBackground() throws Exception {
            // Background thread - load history from server
            logger.debug("Loading history for channel: {}", cid);
            return VersionHistoryServiceClient.getInstance().loadChannelHistory(cid);
        }

        @Override
        protected void done() {
            // EDT - update UI
            try {
                List<CommitMetaData> revisions = get();
                logger.debug("Loaded {} revisions", revisions.size());

                // Update table model
                CommitMetaDataTableModel model = new CommitMetaDataTableModel(revisions);
                tblCommitMetaData.setModel(model);

                // Get current channel commit ID
                Client client = parent.mirthClient;
                String commitId = VersionControlUtil.getChannelCommitId(client, cid);
                tblCommitMetaData.setHighlightValue(commitId);

                // Check if there's a newer version on remote
                boolean alertWarning = false;
                if (!revisions.isEmpty()) {
                    CommitMetaData latestCommit = revisions.get(0);
                    boolean hasNewerVersion = (commitId != null) && !Objects.equals(latestCommit.getHash(), commitId);

                    if (hasNewerVersion) {
                        alertWarning = true;
                        PlatformUI.MIRTH_FRAME.alertWarning(parent, "Remote repository contains a more recent version of this channel, are you sure you want to edit?");
                    }
                }

                // Show success notification if requested and no warning shown
                if (!alertWarning && shouldNotifyOnComplete) {
                    showInformation("History refreshed!");
                }

            } catch (ExecutionException e) {
                logger.error("Failed to load channel history", e);

                // Set empty model on error
                tblCommitMetaData.setModel(new CommitMetaDataTableModel(new ArrayList<>()));

                // Extract error message
                Throwable cause = e.getCause();
                String errorMsg = "Failed to load history from repository";

                if (cause != null) {
                    if (cause.getMessage() != null && !cause.getMessage().isEmpty()) {
                        errorMsg = cause.getMessage();
                    }
                }

                showError(errorMsg);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("History loading was interrupted");
            }
        }
    }

    private class CommitThenPushChannelWorker extends SwingWorker<ResponseUtil, Void> {
        private final String message;

        CommitThenPushChannelWorker(String message) {
            this.message = message;
        }

        @Override
        protected ResponseUtil doInBackground() throws Exception {
            return doCommitAndPushCurrentChannel(message);
        }

        @Override
        protected void done() {
            try {
                ResponseUtil response = get();

                if (response.isSuccess()) {
                    showInformation(response.getMessage());
                    // Refresh history
                    loadHistory(false);
                } else {
                    showError("Commit failed: " + response.getOperationDetails());
                }

            } catch (ExecutionException e) {
                logger.error("Commit failed", e);
                showError("Error: " + e.getCause().getMessage());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                showError("Operation cancelled");
            }
        }
    }

    private void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(parent, msg);
    }

    private void showError(String msg) {
        PlatformUI.MIRTH_FRAME.alertError(parent, msg);
    }

    private ResponseUtil doCommitAndPushCurrentChannel(String message) throws ClientException {
        Client client = parent.mirthClient;
        Channel channel = client.getChannel(cid, false);
        String userId = String.valueOf(client.getCurrentUser().getId());

        return VersionHistoryServiceClient.getInstance().commitAndPushChannel(channel, message, userId);
    }
}
