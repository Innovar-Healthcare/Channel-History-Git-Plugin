package com.innovarhealthcare.channelHistory.client.panel.gitstatus;

import com.innovarhealthcare.channelHistory.client.diff.DiffComparisonPanel;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.mirth.connect.client.ui.UIConstants;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

/**
 * Left-panel tab for the Changes (working-tree diff) view in the Git Status tab.
 * Owns the changes JTree and the CHANGES_DIFF / EMPTY right cards.
 *
 * @author Thai Tran
 */
public class ChangesTabPanel extends JPanel {

    private static final String CARD_EMPTY        = "EMPTY";
    private static final String CARD_CHANGES_DIFF = "CHANGES_DIFF";

    private final VersionHistoryServiceClient client;

    // ── Changes tree ───────────────────────────────────────────────────────────
    private JTree       changesTree;
    private JScrollPane changesTreeScrollPane;

    // ── Right cards ────────────────────────────────────────────────────────────
    private JPanel     rightCards;
    private CardLayout rightCardLayout;
    private JLabel     emptyLabel;

    // CHANGES_DIFF card fields
    private JLabel              changesDiffTitleLabel;
    private DiffComparisonPanel changesDiffPanel;

    // ── Split pane ─────────────────────────────────────────────────────────────
    private JSplitPane splitPane;

    public ChangesTabPanel(VersionHistoryServiceClient client) {
        this.client = client;
        initComponents();
        initLayout();
        initListeners();
    }

    // ========== Public API ==========

    /**
     * Called by the shell's JTabbedPane ChangeListener when this tab becomes active.
     * Re-fires any existing selection; shows EMPTY card if nothing is selected.
     */
    public void onTabSelected() {
        TreePath path = changesTree.getSelectionPath();
        if (path != null) {
            changesTree.clearSelection();
            changesTree.setSelectionPath(path);
        } else {
            emptyLabel.setText(
                    "Select a file from the changes list to view a diff of your local modifications");
            showCard(CARD_EMPTY);
        }
    }

    /**
     * Populates the changes tree from the loaded RepoChanges. Shows the EMPTY card.
     * Called by the shell after LoadDataWorker completes.
     */
    public void populate(RepoChanges changes) {
        buildChangesTree(changes);
        emptyLabel.setText(
                "Select a file from the changes list to view a diff of your local modifications");
        showCard(CARD_EMPTY);
    }

    /**
     * Resets to empty/loading state. Called before a new data load begins.
     */
    public void clear() {
        changesTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
        emptyLabel.setText(
                "Select a file from the changes list to view a diff of your local modifications");
        showCard(CARD_EMPTY);
    }

    // ========== Initialization ==========

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        changesTree = new JTree(new DefaultMutableTreeNode());
        changesTree.setRootVisible(false);
        changesTree.setShowsRootHandles(true);
        changesTree.setCellRenderer(new ChangesCellRenderer());
        changesTreeScrollPane = new JScrollPane(changesTree,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        rightCardLayout = new CardLayout();
        rightCards = new JPanel(rightCardLayout);
        rightCards.setBackground(UIConstants.BACKGROUND_COLOR);

        emptyLabel = new JLabel(
                "Select a file from the changes list to view a diff of your local modifications",
                JLabel.CENTER);
        emptyLabel.setForeground(new Color(150, 150, 150));
        emptyLabel.setFont(new Font("Tahoma", Font.ITALIC, 12));
        rightCards.add(emptyLabel, CARD_EMPTY);

        rightCards.add(buildChangesDiffCard(), CARD_CHANGES_DIFF);
    }

    private JPanel buildChangesDiffCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UIConstants.BACKGROUND_COLOR);

        changesDiffTitleLabel = new JLabel(" ");
        changesDiffTitleLabel.setFont(new Font("Tahoma", Font.BOLD, 11));
        changesDiffTitleLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 204, 204)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        VersionInfo leftVi  = VersionInfo.builder().name("").version("HEAD").isCurrent(false).build();
        VersionInfo rightVi = VersionInfo.builder().name("").version("Current").isCurrent(true).build();
        changesDiffPanel = new DiffComparisonPanel(leftVi, rightVi);

        card.add(changesDiffTitleLabel, BorderLayout.NORTH);
        card.add(changesDiffPanel,      BorderLayout.CENTER);
        return card;
    }

    private void initLayout() {
        setLayout(new BorderLayout());

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, changesTreeScrollPane, rightCards);
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
        changesTree.addTreeSelectionListener(e -> {
            TreePath path = changesTree.getSelectionPath();
            if (path == null) {
                emptyLabel.setText(
                        "Select a file from the changes list to view a diff of your local modifications");
                showCard(CARD_EMPTY);
                return;
            }
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (!node.isLeaf()) {
                emptyLabel.setText(
                        "Select a file from the changes list to view a diff of your local modifications");
                showCard(CARD_EMPTY);
                return;
            }
            String text = String.valueOf(node.getUserObject());
            if (text.startsWith("[M] ")) {
                onChangedFileSelected(text.substring(4), "Modified");
            } else if (text.startsWith("[D] ")) {
                onDeletedFileSelected(text.substring(4), "Deleted");
            } else if (text.startsWith("[U] ")) {
                onUntrackedFileSelected(text.substring(4), "Unversioned");
            }
        });
    }

    // ========== Selection Handlers ==========

    private void onChangedFileSelected(String filePath, String changeTypeLabel) {
        changesDiffTitleLabel.setText("  " + getFileName(filePath) + "  [" + changeTypeLabel + "]");
        showCard(CARD_CHANGES_DIFF);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String[], Void>() {
            @Override protected String[] doInBackground() throws Exception {
                return new String[]{
                        client.getFileContentAtHead(filePath),
                        client.getFileContent(filePath)};
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String[] r = get();
                    changesDiffPanel.updateDiff(r[0], r[1]);
                } catch (Exception ex) {
                    changesDiffPanel.updateDiff("Error: " + ex.getMessage(), "");
                }
            }
        }.execute();
    }

    private void onDeletedFileSelected(String filePath, String changeTypeLabel) {
        changesDiffTitleLabel.setText("  " + getFileName(filePath) + "  [" + changeTypeLabel + "]");
        showCard(CARD_CHANGES_DIFF);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getFileContentAtHead(filePath);
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    changesDiffPanel.updateDiff(get(), "");
                } catch (Exception ex) {
                    changesDiffPanel.updateDiff("Error: " + ex.getMessage(), "");
                }
            }
        }.execute();
    }

    private void onUntrackedFileSelected(String filePath, String changeTypeLabel) {
        changesDiffTitleLabel.setText("  " + getFileName(filePath) + "  [" + changeTypeLabel + "]");
        showCard(CARD_CHANGES_DIFF);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getFileContent(filePath);
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    changesDiffPanel.updateDiff("", get());
                } catch (Exception ex) {
                    changesDiffPanel.updateDiff("", "Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ========== Tree Building ==========

    private void buildChangesTree(RepoChanges changes) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");

        List<String> modifiedFiles  = changes.getModifiedFiles();
        List<String> deletedFiles   = changes.getDeletedFiles();
        List<String> untrackedFiles = changes.getUntrackedFiles();

        int changedCount = (modifiedFiles  != null ? modifiedFiles.size()  : 0)
                         + (deletedFiles   != null ? deletedFiles.size()   : 0);
        DefaultMutableTreeNode changedGroup =
                new DefaultMutableTreeNode("Changed (" + changedCount + ")");
        if (modifiedFiles != null) {
            for (String path : modifiedFiles)
                changedGroup.add(new DefaultMutableTreeNode("[M] " + path));
        }
        if (deletedFiles != null) {
            for (String path : deletedFiles)
                changedGroup.add(new DefaultMutableTreeNode("[D] " + path));
        }

        int untrackedCount = untrackedFiles != null ? untrackedFiles.size() : 0;
        DefaultMutableTreeNode untrackedGroup =
                new DefaultMutableTreeNode("Unversioned Files (" + untrackedCount + ")");
        if (untrackedFiles != null) {
            for (String path : untrackedFiles)
                untrackedGroup.add(new DefaultMutableTreeNode("[U] " + path));
        }

        root.add(changedGroup);
        root.add(untrackedGroup);
        changesTree.setModel(new DefaultTreeModel(root));
        expandAll(changesTree);
    }

    // ========== Helpers ==========

    private void showCard(String cardName) {
        rightCardLayout.show(rightCards, cardName);
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private static String getFileName(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    // ========== Inner Classes ==========

    private static final class ChangesCellRenderer extends DefaultTreeCellRenderer {
        private static final Color COLOR_MODIFIED    = new Color(204, 102, 0);
        private static final Color COLOR_DELETED     = new Color(204, 0, 0);
        private static final Color COLOR_UNVERSIONED = new Color(0, 153, 0);

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (!selected && value instanceof DefaultMutableTreeNode) {
                String text = String.valueOf(((DefaultMutableTreeNode) value).getUserObject());
                if      (text.startsWith("[M] ")) setForeground(COLOR_MODIFIED);
                else if (text.startsWith("[D] ")) setForeground(COLOR_DELETED);
                else if (text.startsWith("[U] ")) setForeground(COLOR_UNVERSIONED);
            }
            return this;
        }
    }
}
