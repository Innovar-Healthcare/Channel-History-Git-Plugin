package com.innovarhealthcare.channelHistory.client.dialog;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.innovarhealthcare.channelHistory.client.model.CodeTemplateWithRaw;
import com.innovarhealthcare.channelHistory.client.model.CommitMetaDataTableModel;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.client.table.CommitMetaDataTable;
import com.innovarhealthcare.channelHistory.client.util.VersionControlUtil;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.util.ResponseUtil;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Jim(Zi Min) Weng
 * @create 2024-05-07 8:46 AM
 */
public class CodeTemplateHistoryDialog extends JDialog {
    private static Logger logger = LogManager.getLogger(CodeTemplateHistoryDialog.class);

    private JPanel actionPanel;
    private JPanel historyPanel;

    private JButton differenceButton;
    private JButton commitPushButton;
    private JButton pullButton;

    private CommitMetaDataTable tblCommitMetaData;
    private JScrollPane historyScrollPane;

    private VersionHistoryServletInterface gitServlet;
    private static final DateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");

    private final String codeTemplateId;

    private JPopupMenu popupMenu;

    private JMenuItem revertRevision;
    private JMenuItem mnuShowDiff;

    private final Frame parent = PlatformUI.MIRTH_FRAME;

    public CodeTemplateHistoryDialog(Window parent, String codeTemplateId) {
        super(parent);

        this.codeTemplateId = codeTemplateId;

        initComponents();
        initLayout();

        load();

        pack();
        setModal(true);
        setVisible(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    private void initComponents() {
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
                loadHistory(true);
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
        historyScrollPane = new JScrollPane(tblCommitMetaData);

        popupMenu = new JPopupMenu();

        revertRevision = new JMenuItem("Revert to revision");
        revertRevision.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = tblCommitMetaData.getSelectedRow();

                CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
                CommitMetaData meta = model.getCommitMetaDataAt(row);
                revert(codeTemplateId, meta.getHash());
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
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill", "", "[][][][grow]"));

        setTitle("Code Template History");
        setPreferredSize(new Dimension(1200, 700));
        Dimension dlgSize = getPreferredSize();
        Dimension frmSize = parent.getSize();
        Point loc = parent.getLocation();

        if ((frmSize.width == 0 && frmSize.height == 0) || (loc.x == 0 && loc.y == 0)) {
            setLocationRelativeTo(null);
        } else {
            setLocation((frmSize.width - dlgSize.width) / 2 + loc.x, (frmSize.height - dlgSize.height) / 2 + loc.y);
        }

        actionPanel.setLayout(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 3, fill, gap 6", "[]12[]12[][grow]"));
        actionPanel.add(differenceButton, "newline, w 108!");
        actionPanel.add(commitPushButton, "w 108!");
        actionPanel.add(pullButton, "w 108!");

        historyPanel.setLayout(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 3, fill, gap 6", "[grow][]"));
        historyPanel.add(historyScrollPane, "sy, grow");

        add(actionPanel, "growx, sx");
        add(historyPanel, "newline, grow, pushx");
    }

    public void load() {
        commitPushButton.setVisible(VersionControlUtil.isAutoCommitDisable(parent.mirthClient));

        this.loadHistory(false);
    }

    public void loadHistory(boolean shouldNotifyOnComplete) {
        new LoadGitHistoryWorker(shouldNotifyOnComplete).execute();
    }

    private void showDiffLastChangeWindow() {
        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
        CommitMetaData lastChange = model.getCommitMetaDataAt(tblCommitMetaData.getSelectedRow());

        if (lastChange == null) {
            showError("No code template revision selected");
            return;
        }

        try {
            Client client = parent.mirthClient;
            String currentUserName = client.getCurrentUser().getUsername();

            // Load current code template
            CodeTemplate leftCodeTemplate = client.getCodeTemplate(codeTemplateId);
            String left = ObjectXMLSerializer.getInstance().serialize(leftCodeTemplate);

            // Load historical code template
            CodeTemplateWithRaw right = VersionHistoryServiceClient.getInstance().loadCodeTemplateWithRawFromRepo(codeTemplateId, lastChange.getHash());

            // Build VersionInfo for current version
            VersionComparisonDialog.VersionInfo currentVersion = VersionComparisonDialog.VersionInfo.createCurrent(leftCodeTemplate.getName(), currentUserName);

            // Build VersionInfo for historical version
            VersionComparisonDialog.VersionInfo historicalVersion = VersionComparisonDialog.VersionInfo.createHistorical(leftCodeTemplate.getName(), lastChange.getHash().substring(0, 7), lastChange.getCommitter(), new Date(lastChange.getTimestamp()));

            // Create and show comparison dialog
            VersionComparisonDialog.create("Code Template Version Comparison", currentVersion, historicalVersion, leftCodeTemplate, right.getCodeTemplate(), left, right.getRawContent(), this);
        } catch (Exception e) {
            logger.error("Failed to show code template comparison", e);
            showError("Cannot compare versions: " + e.getMessage());
        }
    }

    private void showDiffWindow() {
        popupMenu.setVisible(false);

        int[] rows = tblCommitMetaData.getSelectedRows();

        // Validate selection
        if (rows.length != 2) {
            showError("Please select exactly 2 versions to compare");
            return;
        }

        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
        CommitMetaData ri1 = model.getCommitMetaDataAt(rows[0]);
        CommitMetaData ri2 = model.getCommitMetaDataAt(rows[1]);

        if (ri1 == null || ri2 == null) {
            showError("Invalid version selection");
            return;
        }

        try {
            // Load versions
            CodeTemplateWithRaw left = VersionHistoryServiceClient.getInstance().loadCodeTemplateWithRawFromRepo(codeTemplateId, ri1.getHash());
            CodeTemplateWithRaw right = VersionHistoryServiceClient.getInstance().loadCodeTemplateWithRawFromRepo(codeTemplateId, ri2.getHash());

            CodeTemplate leftCodeTemplate = left.getCodeTemplate();
            CodeTemplate rightCodeTemplate = right.getCodeTemplate();

            // Build VersionInfo for left side
            VersionComparisonDialog.VersionInfo leftVersion = VersionComparisonDialog.VersionInfo.createHistorical(leftCodeTemplate.getName(), ri1.getHash().substring(0, 7),  // Short hash
                    ri1.getCommitter(), new Date(ri1.getTimestamp()));

            // Build VersionInfo for right side
            VersionComparisonDialog.VersionInfo rightVersion = VersionComparisonDialog.VersionInfo.createHistorical(rightCodeTemplate.getName(), ri2.getHash().substring(0, 7),  // Short hash
                    ri2.getCommitter(), new Date(ri2.getTimestamp()));

            // Create and show comparison dialog
            VersionComparisonDialog.create("Code Template Version Comparison", leftVersion, rightVersion, leftCodeTemplate, rightCodeTemplate, left.getRawContent(), right.getRawContent(), this);
        } catch (Exception e) {
            logger.error("Failed to show code template comparison", e);
            showError("Cannot compare versions: " + e.getMessage());
        }
    }

    private void revert(String codeTemplateId, String rev) {
        int option = JOptionPane.showConfirmDialog(this, "Would you like to revert code template to this revision?", "Select an Option", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            Client client = parent.mirthClient;

            try {
                CodeTemplate codeTemplate = VersionHistoryServiceClient.getInstance().loadCodeTemplateFromRepo(codeTemplateId, rev);

                if (client.updateCodeTemplate(codeTemplate, true)) {
                    showInformation("Successfully Reverted Code Template");

                    parent.codeTemplatePanel.doRefreshCodeTemplates();
                }
            } catch (ClientException e) {
                showError("Failed to revert code template");
            }
        }
    }

    private void commitThenPush() {
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
        new CommitThenPushCodeTemplateWorker(message).execute();
    }

    /**
     * SwingWorker to load code template commit history in background
     */
    private class LoadGitHistoryWorker extends SwingWorker<List<CommitMetaData>, Void> {
        private final boolean shouldNotifyOnComplete;

        LoadGitHistoryWorker(boolean shouldNotifyOnComplete) {
            this.shouldNotifyOnComplete = shouldNotifyOnComplete;
        }

        @Override
        protected List<CommitMetaData> doInBackground() throws Exception {
            logger.debug("Loading history for code template: {}", codeTemplateId);
            return VersionHistoryServiceClient.getInstance().loadCodeTemplateHistory(codeTemplateId);
        }

        @Override
        protected void done() {
            try {
                List<CommitMetaData> revisions = get();
                logger.debug("Loaded {} revisions for code template", revisions.size());

                // Update table model
                CommitMetaDataTableModel model = new CommitMetaDataTableModel(revisions);
                tblCommitMetaData.setModel(model);

                // Show success notification if requested
                if (shouldNotifyOnComplete) {
                    showInformation("History refreshed!");
                }

            } catch (ExecutionException e) {
                logger.error("Failed to load code template history", e);

                // Set empty model on error
                tblCommitMetaData.setModel(new CommitMetaDataTableModel(new ArrayList<>()));

                if (shouldNotifyOnComplete) {
                    // Extract error message
                    Throwable cause = e.getCause();
                    String errorMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "Failed to pull code template history from repository";

                    showError(errorMsg);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Code template history loading was interrupted");
            }
        }
    }

    /**
     * SwingWorker to commit and push code template to repository
     */
    private class CommitThenPushCodeTemplateWorker extends SwingWorker<ResponseUtil, Void> {
        private final String message;

        CommitThenPushCodeTemplateWorker(String message) {
            this.message = message;
        }

        @Override
        protected ResponseUtil doInBackground() throws Exception {
            Client client = parent.mirthClient;
            String userId = String.valueOf(client.getCurrentUser().getId());

            logger.debug("Committing code template: {} by user: {}", codeTemplateId, userId);

            return VersionHistoryServiceClient.getInstance().commitAndPushCodeTemplate(codeTemplateId, message, userId);
        }

        @Override
        protected void done() {
            try {
                ResponseUtil response = get();

                if (response.isSuccess()) {
                    showInformation(response.getMessage());

                    // Reload history in background
                    loadHistory(false);

                } else {
                    showError("Commit failed: " + response.getOperationDetails());
                    logger.error("Commit failed: {}", response.getOperationDetails());
                }

            } catch (ExecutionException e) {
                logger.error("Failed to commit code template", e);

                Throwable cause = e.getCause();
                String errorMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "Failed to commit and push code template to repository";

                showError(errorMsg);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Commit operation was interrupted");
            }
        }
    }

    private void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(this, msg);
    }

    private void showError(String msg) {
        PlatformUI.MIRTH_FRAME.alertError(this, msg);
    }
}