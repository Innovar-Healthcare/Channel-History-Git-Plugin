package com.innovarhealthcare.channelHistory.client.panel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.HierarchyEvent;

import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
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

    // Info section
    private JPanel infoPanel;
    private JLabel localRepoPathValueLabel;
    private JLabel remoteUrlValueLabel;
    private JLabel branchValueLabel;
    private JLabel sizeValueLabel;

    // File browser section
    private JPanel fileBrowserPanel;
    private JTree fileTree;
    private JScrollPane treeScrollPane;

    // Status / controls
    private JProgressBar loadingBar;
    private JLabel statusLabel;
    private JButton refreshButton;

    public GitStatusTabPanel() {
        initComponents();
        initLayout();

        // Load data whenever this tab becomes visible (tab selection or first show)
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
        remoteUrlValueLabel = newValueLabel();
        branchValueLabel = newValueLabel();
        sizeValueLabel = newValueLabel();

        // ── File browser panel ───────────────────────────────────────────────
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
        treeScrollPane = new JScrollPane(fileTree,
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
        setLayout(new MigLayout("hidemode 3, novisualpadding, insets 8", "[grow]", "[][grow][]"));

        // Info panel: two-column [label | value]
        infoPanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        infoPanel.add(new JLabel("Local Path:"));
        infoPanel.add(localRepoPathValueLabel, "growx, wrap");
        infoPanel.add(new JLabel("Remote URL:"));
        infoPanel.add(remoteUrlValueLabel, "growx, wrap");
        infoPanel.add(new JLabel("Branch:"));
        infoPanel.add(branchValueLabel, "growx, wrap");
        infoPanel.add(new JLabel("Size:"));
        infoPanel.add(sizeValueLabel, "growx, wrap");

        // File browser panel: tree fills available space
        fileBrowserPanel.setLayout(new MigLayout("fill, insets 0, novisualpadding"));
        fileBrowserPanel.add(treeScrollPane, "grow, push");

        add(infoPanel, "growx, wrap");
        add(fileBrowserPanel, "grow, push, wrap");
        add(loadingBar, "growx, wrap");
        add(statusLabel, "growx, wrap");
        add(refreshButton, "right");
    }

    // ========== Data Loading ==========

    private void loadData() {
        enterLoadingState();
        new LoadRepoInfoWorker().execute();
    }

    private void enterLoadingState() {
        loadingBar.setVisible(true);
        statusLabel.setVisible(false);
        refreshButton.setEnabled(false);
        clearValues();
    }

    private void exitLoadedState(RepoInfo info) {
        loadingBar.setVisible(false);
        statusLabel.setVisible(false);
        refreshButton.setEnabled(true);

        localRepoPathValueLabel.setText(info.getLocalRepoPath());
        remoteUrlValueLabel.setText(info.getRemoteUrl());
        branchValueLabel.setText(info.getBranch());
        sizeValueLabel.setText(formatBytes(info.getTotalSizeBytes()));

        buildTree(info);
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
    }

    // ========== Tree ==========

    private void buildTree(RepoInfo info) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");

        if (info.getFolders() != null) {
            for (RepoFolder folder : info.getFolders()) {
                DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(
                        folder.getName() + "  (" + folder.getFileCount() + " files)");

                if (folder.getFiles() != null) {
                    for (RepoFile file : folder.getFiles()) {
                        folderNode.add(new DefaultMutableTreeNode(
                                file.getName() + "  (" + formatBytes(file.getSizeBytes()) + ")"));
                    }
                }
                root.add(folderNode);
            }
        }

        fileTree.setModel(new DefaultTreeModel(root));
        expandAll();
    }

    private void expandAll() {
        for (int i = 0; i < fileTree.getRowCount(); i++) {
            fileTree.expandRow(i);
        }
    }

    // ========== Helpers ==========

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

    // ========== Background Worker ==========

    private final class LoadRepoInfoWorker extends SwingWorker<RepoInfo, Void> {
        @Override
        protected RepoInfo doInBackground() throws Exception {
            return VersionHistoryServiceClient.getInstance().getRepoInfo();
        }

        @Override
        protected void done() {
            try {
                exitLoadedState(get());
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String msg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                exitErrorState("Failed to load repository info: " + msg);
            }
        }
    }
}
