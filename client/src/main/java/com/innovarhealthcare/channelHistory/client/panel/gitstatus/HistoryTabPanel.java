package com.innovarhealthcare.channelHistory.client.panel.gitstatus;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.innovarhealthcare.channelHistory.client.diff.DiffComparisonPanel;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemChange;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

/**
 * Left-panel tab for the commit History view in the Git Status tab.
 * Owns the history JList (with filter bar), changed-files JList,
 * embedded DiffComparisonPanel, and the HISTORY / EMPTY right cards.
 *
 * @author Thai Tran
 */
public class HistoryTabPanel extends JPanel {

    private static final String CARD_EMPTY = "EMPTY";
    private static final String CARD_HISTORY = "HISTORY";

    private final VersionHistoryServiceClient client;

    // ── Left: history list ─────────────────────────────────────────────────────
    private JLabel historyFilterLabel;
    private JButton clearFilterButton;
    private JProgressBar loadingBar;
    private DefaultListModel<CommitMetaData> historyListModel;
    private JList<CommitMetaData> historyList;
    private JScrollPane historyListScrollPane;

    // ── Right cards ────────────────────────────────────────────────────────────
    private JPanel rightCards;
    private CardLayout rightCardLayout;
    private JLabel emptyLabel;

    // HISTORY card fields
    private JLabel historyCommitHashValue;
    private JLabel historyCommitAuthorValue;
    private JLabel historyCommitDateValue;
    private JLabel historyCommitMsgValue;
    private JSplitPane historyContentSplit;
    private DefaultListModel<RepoItemChange> changedFilesListModel;
    private JList<RepoItemChange> changedFilesList;
    private JScrollPane changedFilesScrollPane;
    private CommitMetaData currentHistoryCommit;

    // ── Main split pane ────────────────────────────────────────────────────────
    private JSplitPane splitPane;

    public HistoryTabPanel(VersionHistoryServiceClient client) {
        this.client = client;
        initComponents();
        initLayout();
        initListeners();
    }

    // ========== Public API ==========

    /**
     * Called by the shell's JTabbedPane ChangeListener when this tab becomes active.
     * Auto-selects first item if nothing is selected; re-fires existing selection;
     * shows EMPTY card if the list is empty.
     */
    public void onTabSelected() {
        loadRepoLog();
    }

    /**
     * Loads commit history for a specific file (file-filtered view).
     * Runs a SwingWorker, populates the history list, sets the filter label,
     * and auto-selects index 0.
     *
     * @param relativePath repo-relative file path (used for filter label and history lookup)
     */
    public void loadHistory(String relativePath) {
        showLoadingState();

        new SwingWorker<List<CommitMetaData>, Void>() {
            @Override
            protected List<CommitMetaData> doInBackground() throws Exception {
                return client.getFileHistory(relativePath);
            }

            @Override
            protected void done() {
                loadingBar.setVisible(false);
                try {
                    List<CommitMetaData> history = get();
                    DefaultListModel<CommitMetaData> newModel = new DefaultListModel<>();
                    if (history != null) {
                        for (CommitMetaData c : history) newModel.addElement(c);
                    }
                    historyList.setModel(newModel);
                    historyListModel = newModel;
                    historyFilterLabel.setText("Filtered: " + relativePath);
                    clearFilterButton.setVisible(true);
                    if (!historyListModel.isEmpty()) {
                        historyList.setSelectedIndex(0);
                    } else {
                        emptyLabel.setText("No commits found");
                        showCard(CARD_EMPTY);
                    }
                } catch (Exception ex) {
                    emptyLabel.setText("Failed to load file history");
                    showCard(CARD_EMPTY);
                    JOptionPane.showMessageDialog(HistoryTabPanel.this, "Failed to load file history: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadRepoLog() {
        showLoadingState();

        new SwingWorker<List<CommitMetaData>, Void>() {
            @Override
            protected List<CommitMetaData> doInBackground() throws Exception {
                return client.getRepoLog(200);
            }

            @Override
            protected void done() {
                loadingBar.setVisible(false);
                try {
                    List<CommitMetaData> log = get();
                    DefaultListModel<CommitMetaData> newModel = new DefaultListModel<>();
                    if (log != null) {
                        for (CommitMetaData c : log) newModel.addElement(c);
                    }
                    historyList.setModel(newModel);
                    historyListModel = newModel;
                    historyFilterLabel.setText("All commits");
                    clearFilterButton.setVisible(false);
                    if (!historyListModel.isEmpty()) {
                        historyList.setSelectedIndex(0);
                    } else {
                        emptyLabel.setText("No commits found");
                        showCard(CARD_EMPTY);
                    }
                } catch (Exception ex) {
                    emptyLabel.setText("Failed to load repository log");
                    showCard(CARD_EMPTY);
                    JOptionPane.showMessageDialog(HistoryTabPanel.this, "Failed to load repository log: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Resets the history list to empty state and shows the EMPTY card.
     * Called before a new data load begins.
     */
    public void clear() {
        historyListModel.clear();
        historyFilterLabel.setText("All commits");
        clearFilterButton.setVisible(false);
        currentHistoryCommit = null;
        emptyLabel.setText("");
        showCard(CARD_EMPTY);
    }

    // ========== Initialization ==========

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        // Filter bar
        historyFilterLabel = new JLabel("All commits");
        historyFilterLabel.setFont(new Font("Tahoma", Font.ITALIC, 11));
        historyFilterLabel.setForeground(new Color(100, 100, 100));

        clearFilterButton = new JButton("Clear filter");
        clearFilterButton.setFont(new Font("Tahoma", Font.PLAIN, 11));
        clearFilterButton.setVisible(false);
        clearFilterButton.addActionListener(e -> clearFilter());

        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);

        // History list
        historyListModel = new DefaultListModel<>();
        historyList = new JList<>(historyListModel);
        historyList.setCellRenderer(new CommitListCellRenderer());
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyListScrollPane = new JScrollPane(historyList);

        // Right cards
        rightCardLayout = new CardLayout();
        rightCards = new JPanel(rightCardLayout);
        rightCards.setBackground(UIConstants.BACKGROUND_COLOR);

        emptyLabel = new JLabel("Select a file in the Files tab to browse its commit history", JLabel.CENTER);
        emptyLabel.setForeground(new Color(150, 150, 150));
        emptyLabel.setFont(new Font("Tahoma", Font.ITALIC, 12));
        rightCards.add(emptyLabel, CARD_EMPTY);

        rightCards.add(buildHistoryCard(), CARD_HISTORY);
    }

    private JPanel buildHistoryCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UIConstants.BACKGROUND_COLOR);

        // Commit metadata block
        JPanel commitMetaPanel = new JPanel(new MigLayout("insets 8 8 4 8, novisualpadding", "[right]8[grow,fill]"));
        commitMetaPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        commitMetaPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 204, 204)));

        historyCommitHashValue = new JLabel("—");
        historyCommitAuthorValue = new JLabel("—");
        historyCommitDateValue = new JLabel("—");
        historyCommitMsgValue = new JLabel("—");

        commitMetaPanel.add(new JLabel("Commit:"));
        commitMetaPanel.add(historyCommitHashValue, "growx, wrap");
        commitMetaPanel.add(new JLabel("Author:"));
        commitMetaPanel.add(historyCommitAuthorValue, "growx, wrap");
        commitMetaPanel.add(new JLabel("Date:"));
        commitMetaPanel.add(historyCommitDateValue, "growx, wrap");
        commitMetaPanel.add(new JLabel("Message:"));
        commitMetaPanel.add(historyCommitMsgValue, "growx, wrap");

        // Changed files list
        changedFilesListModel = new DefaultListModel<>();
        changedFilesList = new JList<>(changedFilesListModel);
        changedFilesList.setCellRenderer(new RepoItemChangeCellRenderer());
        changedFilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        changedFilesScrollPane = new JScrollPane(changedFilesList);
        changedFilesScrollPane.setBorder(BorderFactory.createTitledBorder("Changed files"));

        // Diff placeholder (replaced on each file selection)
        JPanel diffPlaceholder = buildDiffPlaceholder("Select a file to view diff");

        historyContentSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, changedFilesScrollPane, diffPlaceholder);
        historyContentSplit.setResizeWeight(0.3);
        historyContentSplit.setDividerSize(6);
        historyContentSplit.setBorder(null);
        historyContentSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                historyContentSplit.setDividerLocation(0.3);
                historyContentSplit.removeComponentListener(this);
            }
        });

        card.add(commitMetaPanel, BorderLayout.NORTH);
        card.add(historyContentSplit, BorderLayout.CENTER);
        return card;
    }

    private void initLayout() {
        setLayout(new BorderLayout());

        // Left panel: filter bar + history list
        JPanel historyTopBar = new JPanel(new MigLayout("insets 4, novisualpadding", "[grow,fill][][]"));
        historyTopBar.setBackground(UIConstants.BACKGROUND_COLOR);
        historyTopBar.add(historyFilterLabel, "growx, push");
        historyTopBar.add(clearFilterButton);
        historyTopBar.add(loadingBar, "w 80!");

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        leftPanel.add(historyTopBar, BorderLayout.NORTH);
        leftPanel.add(historyListScrollPane, BorderLayout.CENTER);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightCards);
        splitPane.setResizeWeight(0.3);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                splitPane.setDividerLocation(0.3);
                splitPane.removeComponentListener(this);
            }
        });

        add(splitPane, BorderLayout.CENTER);
    }

    private void initListeners() {
        historyList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            CommitMetaData commit = historyList.getSelectedValue();
            if (commit == null) {
                return;
            }
            onHistoryCommitSelected(commit);
        });

        changedFilesList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            RepoItemChange change = changedFilesList.getSelectedValue();
            if (change == null || currentHistoryCommit == null) {
                return;
            }
            onHistoryChangedFileSelected(change, currentHistoryCommit.getHash());
        });
    }

    // ========== Selection Handlers ==========

    private void onHistoryCommitSelected(CommitMetaData commit) {
        currentHistoryCommit = commit;

        historyCommitHashValue.setText(commit.getShortHash());
        historyCommitAuthorValue.setText(commit.getCommitter());
        historyCommitDateValue.setText(formatTimestamp(commit.getTimestamp()));
        historyCommitMsgValue.setText(trimMessage(commit.getMessage()));

        changedFilesListModel.clear();
        historyContentSplit.setBottomComponent(buildDiffPlaceholder("Loading changes…"));
        showCard(CARD_HISTORY);

        String hash = commit.getHash();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<RepoItemChange>, Void>() {
            @Override
            protected List<RepoItemChange> doInBackground() throws Exception {
                return client.getCommitChanges(hash);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<RepoItemChange> changes = get();
                    changedFilesListModel.clear();
                    if (changes != null) {
                        for (RepoItemChange c : changes) changedFilesListModel.addElement(c);
                    }
                    historyContentSplit.setBottomComponent(buildDiffPlaceholder("Select a file to view diff"));
                    if (!changedFilesListModel.isEmpty()) {
                        changedFilesList.setSelectedIndex(0);
                    }
                } catch (Exception ex) {
                    historyContentSplit.setBottomComponent(buildDiffPlaceholder("Error loading changes: " + ex.getMessage()));
                }
            }
        }.execute();
    }

    private void onHistoryChangedFileSelected(RepoItemChange change, String commitHash) {
        String filePath = change.getPath();
        String changeType = change.getChangeType();
        String parentHash = commitHash + "^";
        String fileName = getFileName(filePath);

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                String left = "";
                String right = "";
                if ("ADDED".equals(changeType)) {
                    try {
                        right = client.getFileContentAtRevision(filePath, commitHash);
                    } catch (Exception ignored) {
                    }
                } else if ("DELETED".equals(changeType)) {
                    try {
                        left = client.getFileContentAtRevision(filePath, parentHash);
                    } catch (Exception ignored) {
                    }
                } else {
                    try {
                        right = client.getFileContentAtRevision(filePath, commitHash);
                    } catch (Exception ignored) {
                    }
                    try {
                        left = client.getFileContentAtRevision(filePath, parentHash);
                    } catch (Exception ignored) {
                    }
                }
                return new String[]{left, right};
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String[] content = get();
                    String shortHash = commitHash.length() >= 7 ? commitHash.substring(0, 7) : commitHash;

                    VersionInfo leftVi;
                    VersionInfo rightVi;
                    if ("ADDED".equals(changeType)) {
                        leftVi = VersionInfo.builder().name(fileName).version("New file").isCurrent(false).build();
                        rightVi = VersionInfo.builder().name(fileName).version(shortHash).isCurrent(false).build();
                    } else if ("DELETED".equals(changeType)) {
                        leftVi = VersionInfo.builder().name(fileName).version(shortHash).isCurrent(false).build();
                        rightVi = VersionInfo.builder().name(fileName).version("Deleted").isCurrent(false).build();
                    } else {
                        String parentShort = parentHash.length() >= 8 ? parentHash.substring(0, 7) : parentHash;
                        leftVi = VersionInfo.builder().name(fileName).version(parentShort).isCurrent(false).build();
                        rightVi = VersionInfo.builder().name(fileName).version(shortHash).isCurrent(false).build();
                    }

                    DiffComparisonPanel diffPanel = new DiffComparisonPanel(leftVi, rightVi);
                    historyContentSplit.setBottomComponent(diffPanel);
                    diffPanel.updateDiff(content[0], content[1]);
                } catch (Exception ex) {
                    historyContentSplit.setBottomComponent(buildDiffPlaceholder("Error loading diff: " + ex.getMessage()));
                }
            }
        }.execute();
    }

    // ========== Filter ==========

    private void clearFilter() {
        loadRepoLog();
    }

    // ========== Helpers ==========

    private void showCard(String cardName) {
        rightCardLayout.show(rightCards, cardName);
    }

    private void showLoadingState() {
        historyListModel.clear();
        emptyLabel.setText("Loading...");
        showCard(CARD_EMPTY);
        loadingBar.setVisible(true);
    }

    private static JPanel buildDiffPlaceholder(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setForeground(new Color(150, 150, 150));
        label.setFont(new Font("Tahoma", Font.ITALIC, 12));
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private static String getFileName(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    private static String formatTimestamp(long millis) {
        return new SimpleDateFormat("MMM dd, yyyy HH:mm").format(new Date(millis));
    }

    private static String trimMessage(String message) {
        if (message == null) {
            return "—";
        }
        return message.replace("\n", " ").trim();
    }

    // ========== Inner Classes ==========

    private static final class CommitListCellRenderer extends DefaultListCellRenderer {
        private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MMM dd, yyyy HH:mm");

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof CommitMetaData) {
                CommitMetaData commit = (CommitMetaData) value;
                String hash = commit.getShortHash();
                String msg = commit.getMessage();
                if (msg != null) {
                    msg = msg.replace("\n", " ").trim();
                    if (msg.length() > 60) {
                        msg = msg.substring(0, 57) + "...";
                    }
                } else {
                    msg = "(no message)";
                }
                String author = commit.getCommitter() != null ? commit.getCommitter() : "Unknown";
                String date = DATE_FMT.format(new Date(commit.getTimestamp()));

                if (isSelected) {
                    setText("<html><b>" + hash + "</b> " + msg + "<br><small>" + author + " \u2022 " + date + "</small></html>");
                } else {
                    setText("<html><b>" + hash + "</b> " + msg + "<br><font color='#757575'><small>" + author + " \u2022 " + date + "</small></font></html>");
                }
                setFont(getFont().deriveFont(Font.PLAIN, 12f));
            }
            return this;
        }
    }

    private static final class RepoItemChangeCellRenderer extends DefaultListCellRenderer {
        private static final String HEX_MODIFIED = "#CC6600";
        private static final String HEX_ADDED = "#009900";
        private static final String HEX_DELETED = "#CC0000";

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof RepoItemChange) {
                RepoItemChange change = (RepoItemChange) value;
                String changeType = change.getChangeType();
                String path = change.getPath();

                String prefix;
                String hex;
                if ("ADDED".equals(changeType)) {
                    prefix = "[A]";
                    hex = HEX_ADDED;
                } else if ("DELETED".equals(changeType)) {
                    prefix = "[D]";
                    hex = HEX_DELETED;
                } else {
                    prefix = "[M]";
                    hex = HEX_MODIFIED;
                }

                if (isSelected) {
                    setText(prefix + " " + path);
                } else {
                    setText("<html><font color='" + hex + "'>" + prefix + "</font> " + path + "</html>");
                }
                setToolTipText(path);
                setFont(getFont().deriveFont(Font.PLAIN, 12f));
            }
            return this;
        }
    }
}
