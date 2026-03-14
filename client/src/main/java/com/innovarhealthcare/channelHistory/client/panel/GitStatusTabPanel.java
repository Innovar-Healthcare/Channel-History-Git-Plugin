package com.innovarhealthcare.channelHistory.client.panel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import com.innovarhealthcare.channelHistory.client.dialog.ChannelDiffDialog;
import com.innovarhealthcare.channelHistory.client.dialog.CodeTemplateDiffDialog;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoFile;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoFolder;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoInfo;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class GitStatusTabPanel extends JPanel {

    // ── Info panel ───────────────────────────────────────────────────────────
    private JPanel infoPanel;
    private JLabel localRepoPathValueLabel;
    private JLabel remoteUrlValueLabel;
    private JLabel branchValueLabel;
    private JLabel sizeValueLabel;

    // ── File browser (left) ───────────────────────────────────────────────────
    private JPanel fileBrowserPanel;
    private JTree fileTree;
    private JScrollPane fileTreeScrollPane;

    // ── Changes (right) ───────────────────────────────────────────────────────
    private JPanel changesPanel;
    private JTree changesTree;
    private JScrollPane changesTreeScrollPane;

    // ── Split pane ────────────────────────────────────────────────────────────
    private JSplitPane splitPane;

    // ── Status / controls ─────────────────────────────────────────────────────
    private JProgressBar loadingBar;
    private JLabel statusLabel;
    private JButton refreshButton;

    public GitStatusTabPanel() {
        initComponents();
        initLayout();

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                loadData();
            }
        });
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        // ── Info panel ──────────────────────────────────────────────────────
        infoPanel = new JPanel();
        infoPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        infoPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "Repository Info",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", Font.BOLD, 11)
        ));

        localRepoPathValueLabel = newValueLabel();
        remoteUrlValueLabel     = newValueLabel();
        branchValueLabel        = newValueLabel();
        sizeValueLabel          = newValueLabel();

        // ── File browser panel (left) ────────────────────────────────────────
        fileBrowserPanel = new JPanel();
        fileBrowserPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        fileBrowserPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "File Browser",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", Font.BOLD, 11)
        ));

        fileTree = new JTree(new DefaultMutableTreeNode());
        fileTree.setRootVisible(false);
        fileTree.setShowsRootHandles(true);
        fileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleFileTreeClick();
                }
            }
        });
        fileTreeScrollPane = new JScrollPane(fileTree,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // ── Changes panel (right) ────────────────────────────────────────────
        changesPanel = new JPanel();
        changesPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        changesPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "Working Tree Changes",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", Font.BOLD, 11)
        ));

        changesTree = new JTree(new DefaultMutableTreeNode());
        changesTree.setRootVisible(false);
        changesTree.setShowsRootHandles(true);
        changesTree.setCellRenderer(new ChangesCellRenderer());
        changesTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleChangesTreeClick();
                }
            }
        });
        changesTreeScrollPane = new JScrollPane(changesTree,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // ── Status / controls ────────────────────────────────────────────────
        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);

        statusLabel = new JLabel();
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Tahoma", Font.PLAIN, 11));
        statusLabel.setVisible(false);

        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadData());
    }

    private void initLayout() {
        setLayout(new MigLayout("hidemode 3, novisualpadding, insets 8", "[grow]", "[][grow][][]"));

        // ── Info panel: two-column [label | value] ───────────────────────────
        infoPanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        infoPanel.add(new JLabel("Local Path:"));
        infoPanel.add(localRepoPathValueLabel, "growx, wrap");
        infoPanel.add(new JLabel("Remote URL:"));
        infoPanel.add(remoteUrlValueLabel, "growx, wrap");
        infoPanel.add(new JLabel("Branch:"));
        infoPanel.add(branchValueLabel, "growx, wrap");
        infoPanel.add(new JLabel("Size:"));
        infoPanel.add(sizeValueLabel, "growx, wrap");

        // ── File browser panel layout ────────────────────────────────────────
        fileBrowserPanel.setLayout(new MigLayout("fill, insets 0, novisualpadding"));
        fileBrowserPanel.add(fileTreeScrollPane, "grow, push");

        // ── Changes panel layout ─────────────────────────────────────────────
        changesPanel.setLayout(new MigLayout("fill, insets 0, novisualpadding"));
        changesPanel.add(changesTreeScrollPane, "grow, push");

        // ── Split pane ───────────────────────────────────────────────────────
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileBrowserPanel, changesPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        splitPane.setBackground(UIConstants.BACKGROUND_COLOR);

        add(infoPanel,    "growx, wrap");
        add(splitPane,    "grow, push, wrap");
        add(loadingBar,   "growx, wrap");
        add(statusLabel,  "growx, wrap");
        add(refreshButton, "right");
    }

    // ========== Data Loading ==========

    private void loadData() {
        enterLoadingState();
        new LoadDataWorker().execute();
    }

    private void enterLoadingState() {
        loadingBar.setVisible(true);
        statusLabel.setVisible(false);
        refreshButton.setEnabled(false);
        clearValues();
    }

    private void exitLoadedState(RepoInfo info, RepoChanges changes) {
        loadingBar.setVisible(false);
        statusLabel.setVisible(false);
        refreshButton.setEnabled(true);

        localRepoPathValueLabel.setText(info.getLocalRepoPath());
        remoteUrlValueLabel.setText(info.getRemoteUrl());
        branchValueLabel.setText(info.getBranch());
        sizeValueLabel.setText(formatBytes(info.getTotalSizeBytes()));

        buildFileTree(info);
        buildChangesTree(changes);
    }

    private void exitErrorState(String message) {
        loadingBar.setVisible(false);
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        refreshButton.setEnabled(true);
    }

    private void clearValues() {
        localRepoPathValueLabel.setText("—");
        remoteUrlValueLabel.setText("—");
        branchValueLabel.setText("—");
        sizeValueLabel.setText("—");
        fileTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
        changesTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
    }

    // ========== File Browser Tree ==========

    private void buildFileTree(RepoInfo info) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");

        if (info.getFolders() != null) {
            for (RepoFolder folder : info.getFolders()) {
                DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(
                        folder.getName() + "  (" + folder.getFileCount() + " files)");

                if (folder.getFiles() != null) {
                    for (RepoFile file : folder.getFiles()) {
                        String relativePath = folder.getName() + "/" + file.getName();
                        String displayText  = file.getName() + "  (" + formatBytes(file.getSizeBytes()) + ")";
                        folderNode.add(new DefaultMutableTreeNode(new FileNode(displayText, relativePath)));
                    }
                }
                root.add(folderNode);
            }
        }

        fileTree.setModel(new DefaultTreeModel(root));
        expandAll(fileTree);
    }

    // ========== Changes Tree ==========

    private void buildChangesTree(RepoChanges changes) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");

        List<String> modifiedFiles  = changes.getModifiedFiles();
        List<String> deletedFiles   = changes.getDeletedFiles();
        List<String> untrackedFiles = changes.getUntrackedFiles();

        int changedCount = (modifiedFiles  != null ? modifiedFiles.size()  : 0)
                         + (deletedFiles   != null ? deletedFiles.size()   : 0);
        DefaultMutableTreeNode changedGroup = new DefaultMutableTreeNode("Changed (" + changedCount + ")");
        if (modifiedFiles != null) {
            for (String path : modifiedFiles) {
                changedGroup.add(new DefaultMutableTreeNode("[M] " + path));
            }
        }
        if (deletedFiles != null) {
            for (String path : deletedFiles) {
                changedGroup.add(new DefaultMutableTreeNode("[D] " + path));
            }
        }

        int untrackedCount = untrackedFiles != null ? untrackedFiles.size() : 0;
        DefaultMutableTreeNode untrackedGroup = new DefaultMutableTreeNode("Unversioned Files (" + untrackedCount + ")");
        if (untrackedFiles != null) {
            for (String path : untrackedFiles) {
                untrackedGroup.add(new DefaultMutableTreeNode("[U] " + path));
            }
        }

        root.add(changedGroup);
        root.add(untrackedGroup);

        changesTree.setModel(new DefaultTreeModel(root));
        expandAll(changesTree);
    }

    // ========== Click Handlers ==========

    private void handleFileTreeClick() {
        DefaultMutableTreeNode node = getSelectedLeafNode(fileTree);
        if (node == null) return;

        Object obj = node.getUserObject();
        if (!(obj instanceof FileNode)) return;

        FileNode fileNode = (FileNode) obj;
        if (fileNode.relativePath == null) return;

        openFileContentView(fileNode.relativePath);
    }

    private void handleChangesTreeClick() {
        DefaultMutableTreeNode node = getSelectedLeafNode(changesTree);
        if (node == null) return;

        String text = String.valueOf(node.getUserObject());

        if (text.startsWith("[M] ")) {
            openModifiedDiff(text.substring(4));
        } else if (text.startsWith("[D] ")) {
            openDeletedDiff(text.substring(4));
        } else if (text.startsWith("[U] ")) {
            openUnversionedView(text.substring(4));
        }
    }

    private DefaultMutableTreeNode getSelectedLeafNode(JTree tree) {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        return node.isLeaf() ? node : null;
    }

    // ========== File Content Workers ==========

    /** Repo Files tree double-click: binary check → simple text viewer. */
    private void openFileContentView(String filePath) {
        String fileName = getFileName(filePath);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return VersionHistoryServiceClient.getInstance().getFileContent(filePath);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String content = get();
                    if (isBinary(content)) {
                        JOptionPane.showMessageDialog(GitStatusTabPanel.this,
                                "Cannot display binary file: " + fileName, "Binary File", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    showTextViewer(fileName, content);
                } catch (Exception e) {
                    showFetchError(e);
                }
            }
        }.execute();
    }

    /** Changes tree [M] double-click: binary check → XML check → diff dialog or simple viewer. */
    private void openModifiedDiff(String filePath) {
        String fileName = getFileName(filePath);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                VersionHistoryServiceClient client = VersionHistoryServiceClient.getInstance();
                String head    = client.getFileContentAtHead(filePath);
                String current = client.getFileContent(filePath);
                return new String[]{head, current};
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String[] result      = get();
                    String headContent    = result[0];
                    String currentContent = result[1];

                    if (isBinary(headContent) || isBinary(currentContent)) {
                        JOptionPane.showMessageDialog(GitStatusTabPanel.this,
                                "Cannot display binary file: " + fileName, "Binary File", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    if (!currentContent.trim().startsWith("<")) {
                        showTextViewer(fileName + " (Current)", currentContent);
                        return;
                    }

                    VersionInfo headVersion    = VersionInfo.builder().name(fileName).version("HEAD").isCurrent(false).build();
                    VersionInfo currentVersion = VersionInfo.builder().name(fileName).version("Current").isCurrent(true).build();
                    openDiffDialog(filePath, headContent, currentContent, headVersion, currentVersion);
                } catch (Exception e) {
                    showFetchError(e);
                }
            }
        }.execute();
    }

    /** Changes tree [D] double-click: binary check → XML check → diff dialog or simple viewer. */
    private void openDeletedDiff(String filePath) {
        String fileName = getFileName(filePath);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return VersionHistoryServiceClient.getInstance().getFileContentAtHead(filePath);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String headContent = get();

                    if (isBinary(headContent)) {
                        JOptionPane.showMessageDialog(GitStatusTabPanel.this,
                                "Cannot display binary file: " + fileName, "Binary File", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    if (!headContent.trim().startsWith("<")) {
                        showTextViewer(fileName + " (HEAD)", headContent);
                        return;
                    }

                    VersionInfo headVersion    = VersionInfo.builder().name(fileName).version("HEAD").isCurrent(false).build();
                    VersionInfo deletedVersion = VersionInfo.builder().name(fileName).version("Deleted").isCurrent(true).build();
                    openDiffDialog(filePath, headContent, "", headVersion, deletedVersion);
                } catch (Exception e) {
                    showFetchError(e);
                }
            }
        }.execute();
    }

    /** Changes tree [U] double-click: binary check → XML check → diff dialog or simple viewer. */
    private void openUnversionedView(String filePath) {
        String fileName = getFileName(filePath);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return VersionHistoryServiceClient.getInstance().getFileContent(filePath);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String content = get();

                    if (isBinary(content)) {
                        JOptionPane.showMessageDialog(GitStatusTabPanel.this,
                                "Cannot display binary file: " + fileName, "Binary File", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    if (!content.trim().startsWith("<")) {
                        showTextViewer(fileName + " (Current)", content);
                        return;
                    }

                    VersionInfo notInRepoVersion = VersionInfo.builder().name(fileName).version("Not in repository").isCurrent(false).build();
                    VersionInfo currentVersion   = VersionInfo.builder().name(fileName).version("Current").isCurrent(true).build();
                    openDiffDialog(filePath, "", content, notInRepoVersion, currentVersion);
                } catch (Exception e) {
                    showFetchError(e);
                }
            }
        }.execute();
    }

    // ========== Dialog Routing ==========

    private void openDiffDialog(String filePath, String leftXml, String rightXml,
                                VersionInfo leftVersion, VersionInfo rightVersion) {
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(this);
        if (isCodeTemplateFile(filePath)) {
            new CodeTemplateDiffDialog(frame, leftVersion, rightVersion, leftXml, rightXml).setVisible(true);
        } else {
            // Channels and any unrecognised file type
            new ChannelDiffDialog(frame, leftVersion, rightVersion, leftXml, rightXml).setVisible(true);
        }
    }

    private boolean isCodeTemplateFile(String filePath) {
        String lower = filePath.toLowerCase();
        return lower.contains("codetemplate") || lower.contains("libraries") || lower.contains("library");
    }

    // ========== Helpers ==========

    private String getFileName(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    /** Returns true if the string content appears to be binary (contains a null byte in the first 8KB). */
    private static boolean isBinary(String content) {
        int limit = Math.min(content.length(), 8192);
        for (int i = 0; i < limit; i++) {
            if (content.charAt(i) == '\0') return true;
        }
        return false;
    }

    /** Shows a simple read-only text viewer dialog (800x600, monospaced JTextArea). */
    private void showTextViewer(String title, String content) {
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(frame, title, true);

        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setCaretPosition(0);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);

        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(textArea), BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(frame);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        dialog.setVisible(true);
    }

    private void showFetchError(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String msg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
        JOptionPane.showMessageDialog(this, "Failed to fetch file content: " + msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private JLabel newValueLabel() {
        JLabel label = new JLabel("—");
        label.setFont(new Font("Tahoma", Font.PLAIN, 11));
        return label;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        } else if (bytes < 1024L * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    // ========== Inner Classes ==========

    /** Carries both the display text and the repo-relative file path for file tree nodes. */
    private static final class FileNode {
        final String displayText;
        final String relativePath;

        FileNode(String displayText, String relativePath) {
            this.displayText  = displayText;
            this.relativePath = relativePath;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    private static final class ChangesCellRenderer extends DefaultTreeCellRenderer {
        private static final Color COLOR_MODIFIED   = new Color(204, 102, 0);
        private static final Color COLOR_DELETED    = new Color(204, 0, 0);
        private static final Color COLOR_UNVERSIONED = new Color(0, 153, 0);

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (!selected && value instanceof DefaultMutableTreeNode) {
                String text = String.valueOf(((DefaultMutableTreeNode) value).getUserObject());
                if (text.startsWith("[M] ")) {
                    setForeground(COLOR_MODIFIED);
                } else if (text.startsWith("[D] ")) {
                    setForeground(COLOR_DELETED);
                } else if (text.startsWith("[U] ")) {
                    setForeground(COLOR_UNVERSIONED);
                }
            }
            return this;
        }
    }

    // ========== Background Worker ==========

    private static final class LoadResult {
        final RepoInfo    info;
        final RepoChanges changes;

        LoadResult(RepoInfo info, RepoChanges changes) {
            this.info    = info;
            this.changes = changes;
        }
    }

    private final class LoadDataWorker extends SwingWorker<LoadResult, Void> {
        @Override
        protected LoadResult doInBackground() throws Exception {
            VersionHistoryServiceClient client = VersionHistoryServiceClient.getInstance();
            RepoInfo    info    = client.getRepoInfo();
            RepoChanges changes = client.getRepoChanges();
            return new LoadResult(info, changes);
        }

        @Override
        protected void done() {
            try {
                LoadResult result = get();
                exitLoadedState(result.info, result.changes);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String msg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                exitErrorState("Failed to load repository status: " + msg);
            }
        }
    }
}
