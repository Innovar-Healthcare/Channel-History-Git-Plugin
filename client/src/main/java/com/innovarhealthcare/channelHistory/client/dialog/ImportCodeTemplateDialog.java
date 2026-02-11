package com.innovarhealthcare.channelHistory.client.dialog;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.dto.response.LibrariesAndTemplatesResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.LibraryMetadata;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.util.JsonUtils;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrarySaveResult;
import net.miginfocom.swing.MigLayout;

/**
 * Dialog for importing code templates from Git repository
 * Displays templates grouped by library in a tree structure
 *
 * @author Thai Tran
 * @create 2024-11-20 2:30 PM
 */
public class ImportCodeTemplateDialog extends MirthDialog {

    // UI Components
    private JTree codeTemplateTree;
    private JScrollPane treeScrollPane;
    private JTextField searchField;
    private JButton clearSearchButton;
    private JButton okButton;
    private JButton cancelButton;
    private JProgressBar loadingBar;
    private JLabel loadingLabel;

    // Data
    private Map<String, CodeTemplateLibrary> codeTemplateLibraries;
    private List<LibraryMetadata> librariesMetadata;
    private List<RepoItemMetadata> templatesMetadata;
    private DefaultMutableTreeNode fullTreeRoot;

    // State
    private boolean loading = false;
    private boolean hasTemplates = false;

    private final Frame parent;

    public ImportCodeTemplateDialog(Frame parent) {
        super(parent, true);

        this.parent = parent;
        this.codeTemplateLibraries = parent.codeTemplatePanel.getCachedCodeTemplateLibraries();

        initComponents();
        initLayout();

        enterLoadingState();

        // Start thread to load libraries and templates from repo
        new LoadLibrariesAndTemplatesWorker().execute();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Import Code Template From Repo");
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        // Create tree with loading message
        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode("Loading...");
        codeTemplateTree = new JTree(loadingNode);
        codeTemplateTree.setRootVisible(false);
        codeTemplateTree.setShowsRootHandles(true);
        codeTemplateTree.setCellRenderer(new CodeTemplateTreeCellRenderer());
        codeTemplateTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        treeScrollPane = new JScrollPane(codeTemplateTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        treeScrollPane.setPreferredSize(new Dimension(650, 400));

        // Search field
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(100, 25));
        searchField.getDocument().addDocumentListener(new SimpleDoc(this::applyFilter));

        clearSearchButton = new JButton("X");
        clearSearchButton.setMargin(new Insets(2, 8, 2, 8));
        clearSearchButton.addActionListener(e -> {
            searchField.setText("");
            applyFilter();
            searchField.requestFocusInWindow();
        });

        // Loading indicator
        loadingLabel = new JLabel("Loading libraries and code templates…");
        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);

        // Buttons
        okButton = new JButton("Import");
        okButton.addActionListener(evt -> onOkImport(evt));
        okButton.setEnabled(false);

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> dispose());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 8, novisualpadding, hidemode 3, fillx", "[pref][grow,fill][pref]", "[] [grow] []"));

        // Search row
        add(new JLabel("Search:"), "cell 0 0, alignx left");
        add(searchField, "cell 1 0, growx, pushx, split 2");
        add(clearSearchButton, "gapleft 0, wrap");

        // Tree
        add(treeScrollPane, "cell 0 1 3 1, grow, push, wrap");

        // Left side (progress + text)
        add(loadingBar, "cell 0 2, alignx left");
        add(loadingLabel, "cell 0 2, gapleft 8, alignx left");

        // Right side (buttons)
        add(okButton, "cell 2 2, split 2, alignx right, w 120!");
        add(cancelButton, "w 70!");
    }

    // ========== Loading State Management ==========

    private void enterLoadingState() {
        loading = true;
        okButton.setEnabled(false);
        setLoadingVisible(true);
    }

    private void exitLoadingState() {
        loading = false;
        setLoadingVisible(false);
        okButton.setEnabled(hasTemplates);
    }

    private void setLoadingVisible(boolean visible) {
        loadingBar.setVisible(visible);
        loadingLabel.setVisible(visible);
        revalidate();
        repaint();
    }

    // ========== Background Worker ==========

    private final class LoadLibrariesAndTemplatesWorker extends SwingWorker<LibrariesAndTemplatesResponse, Void> {
        @Override
        protected LibrariesAndTemplatesResponse doInBackground() throws Exception {
            return VersionHistoryServiceClient.getInstance().loadLibrariesAndTemplateMetadata();
        }

        @Override
        protected void done() {
            try {
                LibrariesAndTemplatesResponse response = get();

                System.out.println("Response JSON: " + JsonUtils.toJson(response));

                librariesMetadata = response.getLibraries();
                templatesMetadata = response.getTemplates();

                buildTreeModel();
                applyFilter();

                hasTemplates = !templatesMetadata.isEmpty();
                exitLoadingState();

            } catch (Exception ex) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Failed to load libraries and code templates from repository: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown error"));
                okButton.setEnabled(false);
                setLoadingVisible(false);
            }
        }
    }

    // ========== Tree Building ==========

    private void buildTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");

        // Build map: templateId -> metadata
        Map<String, RepoItemMetadata> templateMap = new HashMap<>();
        for (RepoItemMetadata meta : templatesMetadata) {
            templateMap.put(meta.getId(), meta);
        }

        // Track which templates have been added to libraries
        Set<String> addedTemplateIds = new HashSet<>();

        // ✅ Sort libraries by name (case-insensitive)
        List<LibraryMetadata> sortedLibraries = new ArrayList<>(librariesMetadata);
        sortedLibraries.sort(Comparator.comparing(lib -> {
            String name = lib.getName();
            return name != null ? name.toLowerCase() : "";
        }));

        // Add libraries with their templates
        for (LibraryMetadata library : sortedLibraries) {
            DefaultMutableTreeNode libraryNode = new DefaultMutableTreeNode(new LibraryTreeNode(library.getId(), library.getName()));

            // Add templates for this library
            List<String> templateIds = library.getCodeTemplateIds();
            if (templateIds != null) {
                List<RepoItemMetadata> libraryTemplates = new ArrayList<>();
                for (String templateId : templateIds) {
                    RepoItemMetadata templateMeta = templateMap.get(templateId);
                    if (templateMeta != null) {
                        libraryTemplates.add(templateMeta);
                        addedTemplateIds.add(templateId);
                    }
                }

                // Sort templates by name
                libraryTemplates.sort(Comparator.comparing(t -> {
                    String name = t.getName();
                    return name != null ? name.toLowerCase() : "";
                }));

                for (RepoItemMetadata templateMeta : libraryTemplates) {
                    DefaultMutableTreeNode templateNode = new DefaultMutableTreeNode(new TemplateTreeNode(templateMeta));
                    libraryNode.add(templateNode);
                }
            }

            // Always add library node (even if empty)
            root.add(libraryNode);
        }

        // Add "Unknown Library" for templates not in any library
        DefaultMutableTreeNode unknownLibraryNode = new DefaultMutableTreeNode(new LibraryTreeNode(null, "Unknown Library"));

        for (RepoItemMetadata templateMeta : templatesMetadata) {
            if (!addedTemplateIds.contains(templateMeta.getId())) {
                DefaultMutableTreeNode templateNode = new DefaultMutableTreeNode(new TemplateTreeNode(templateMeta));
                unknownLibraryNode.add(templateNode);
            }
        }

        // Add "Unknown Library" if it has templates
        if (unknownLibraryNode.getChildCount() > 0) {
            root.add(unknownLibraryNode);
        }

        // Store full tree for filtering
        fullTreeRoot = root;

        // Set tree model
        codeTemplateTree.setModel(new DefaultTreeModel(root));

        // Expand all libraries by default
        expandAll();
    }

    // ========== Filtering ==========

    private void applyFilter() {
        if (fullTreeRoot == null) {
            return;
        }

        String text = Optional.ofNullable(searchField.getText()).orElse("").trim();

        if (text.isEmpty()) {
            // No filter - show all
            codeTemplateTree.setModel(new DefaultTreeModel(fullTreeRoot));
            expandAll();
            return;
        }

        String needle = text.toLowerCase();

        // Build filtered tree
        DefaultMutableTreeNode filteredRoot = new DefaultMutableTreeNode("Root");

        // Iterate through libraries in full tree
        for (int i = 0; i < fullTreeRoot.getChildCount(); i++) {
            DefaultMutableTreeNode libraryNode = (DefaultMutableTreeNode) fullTreeRoot.getChildAt(i);
            LibraryTreeNode libraryData = (LibraryTreeNode) libraryNode.getUserObject();

            DefaultMutableTreeNode filteredLibraryNode = new DefaultMutableTreeNode(libraryData);

            // Check each template in this library
            for (int j = 0; j < libraryNode.getChildCount(); j++) {
                DefaultMutableTreeNode templateNode = (DefaultMutableTreeNode) libraryNode.getChildAt(j);
                TemplateTreeNode templateData = (TemplateTreeNode) templateNode.getUserObject();

                // Match against template name or ID
                String name = templateData.metadata.getName().toLowerCase();
                String id = templateData.metadata.getId().toLowerCase();

                if (name.contains(needle) || id.contains(needle)) {
                    filteredLibraryNode.add(new DefaultMutableTreeNode(templateData));
                }
            }

            // Only add library if it has matching templates
            if (filteredLibraryNode.getChildCount() > 0) {
                filteredRoot.add(filteredLibraryNode);
            }
        }

        codeTemplateTree.setModel(new DefaultTreeModel(filteredRoot));
        expandAll();
    }

    private void expandAll() {
        for (int i = 0; i < codeTemplateTree.getRowCount(); i++) {
            codeTemplateTree.expandRow(i);
        }
    }

    // ========== Import Action ==========

    private void onOkImport(ActionEvent evt) {
        TreePath selectedPath = codeTemplateTree.getSelectionPath();

        if (selectedPath == null) {
            PlatformUI.MIRTH_FRAME.alertInformation(parent, "Please select a code template to import!");
            return;
        }

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        Object userObject = selectedNode.getUserObject();

        // Must select a template node, not a library node
        if (!(userObject instanceof TemplateTreeNode)) {
            PlatformUI.MIRTH_FRAME.alertInformation(parent, "Please select a code template (not a library) to import!");
            return;
        }

        TemplateTreeNode templateNode = (TemplateTreeNode) userObject;
        RepoItemMetadata metadata = templateNode.metadata;

        // Choose a library to import into
        CodeTemplateLibrary selectedLib = promptForLibrarySelectionByIndex();
        if (selectedLib == null) {
            return;
        }

        try {
            // Load full code template content from repository
            CodeTemplate template = VersionHistoryServiceClient.getInstance().loadCodeTemplateFromRepo(metadata);

            if (template == null) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Failed to load code template content");
                return;
            }

            if (doAddCodeTemplate(template, selectedLib)) {
                dispose();
                parent.codeTemplatePanel.doRefreshCodeTemplates();
            }
        } catch (ClientException e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }
    }

    /**
     * Shows a modal combo (sorted by name) and returns the actual library object (id-safe).
     */
    private CodeTemplateLibrary promptForLibrarySelectionByIndex() {
        if (codeTemplateLibraries == null || codeTemplateLibraries.isEmpty()) {
            PlatformUI.MIRTH_FRAME.alertError(parent, "No Code Template Libraries available.");
            return null;
        }

        // Stable list used for both display and selection → index maps directly to object
        List<CodeTemplateLibrary> libs = new ArrayList<>(codeTemplateLibraries.values());
        libs.sort(Comparator.comparing(l -> {
            String n = l.getName();
            return n == null ? "" : n.toLowerCase();
        }));

        String[] names = libs.stream().map(l -> l.getName() == null ? "(unnamed)" : l.getName()).toArray(String[]::new);

        JComboBox<String> combo = new JComboBox<>(names);
        int result = JOptionPane.showConfirmDialog(this, combo, "Choose Library to Import Into", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            int idx = combo.getSelectedIndex();
            return (idx >= 0 && idx < libs.size()) ? libs.get(idx) : null;
        }

        return null;
    }

    private boolean doAddCodeTemplate(CodeTemplate template, CodeTemplateLibrary selectedLibrary) throws ClientException {
        try {
            Client client = parent.mirthClient;

            String templateId = template.getId();
            CodeTemplate idTemplateMatch = getTemplateById(templateId);

            if (idTemplateMatch != null) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Template \"" + template.getName() + "\" (ID: " + templateId + ") already exists.");
                return false;
            }

            Map<String, CodeTemplateLibrary> libraryMap = new HashMap<>();
            for (CodeTemplateLibrary library : client.getCodeTemplateLibraries(null, false)) {
                libraryMap.put(library.getId(), library);
            }

            List<CodeTemplateLibrary> libraries = new ArrayList<>();
            selectedLibrary.getCodeTemplates().add(template);
            libraries.add(selectedLibrary);

            Map<String, CodeTemplate> codeTemplateMap = new HashMap<>();
            for (CodeTemplateLibrary library : libraries) {
                library = new CodeTemplateLibrary(library);

                CodeTemplateLibrary matchingLibrary = libraryMap.get(library.getId());
                if (matchingLibrary != null) {
                    library.getEnabledChannelIds().addAll(matchingLibrary.getEnabledChannelIds());
                    library.getDisabledChannelIds().addAll(matchingLibrary.getDisabledChannelIds());
                    library.getDisabledChannelIds().removeAll(library.getEnabledChannelIds());

                    for (CodeTemplate serverCodeTemplate : matchingLibrary.getCodeTemplates()) {
                        boolean found = false;
                        for (CodeTemplate codeTemplate : library.getCodeTemplates()) {
                            if (serverCodeTemplate.getId().equals(codeTemplate.getId())) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            library.getCodeTemplates().add(serverCodeTemplate);
                        }
                    }
                }

                for (CodeTemplate codeTemplate : library.getCodeTemplates()) {
                    if (codeTemplate.getName() != null) {
                        codeTemplateMap.put(codeTemplate.getId(), codeTemplate);
                    }
                }

                libraryMap.put(library.getId(), library);
            }

            CodeTemplateLibrarySaveResult updateSummary = client.updateLibrariesAndTemplates(new ArrayList<>(libraryMap.values()), new HashSet<>(), new ArrayList<>(codeTemplateMap.values()), new HashSet<>(), true);

            String message = "";
            if (!updateSummary.isOverrideNeeded()) {
                if (updateSummary.isLibrariesSuccess()) {
                    List<CodeTemplate> failedCodeTemplates = new ArrayList<>();
                    Throwable firstCause = null;

                    for (Entry<String, CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult> entry : updateSummary.getCodeTemplateResults().entrySet()) {
                        if (!entry.getValue().isSuccess()) {
                            failedCodeTemplates.add(codeTemplateMap.get(entry.getKey()));
                            if (firstCause == null) {
                                firstCause = entry.getValue().getCause();
                            }
                        }
                    }

                    if (failedCodeTemplates.isEmpty()) {
                        // Successfully imported code template
                        return true;
                    }

                    if (firstCause != null) {
                        message = firstCause.getMessage();
                    } else {
                        message = "unknown error";
                    }
                    PlatformUI.MIRTH_FRAME.alertError(parent, message);
                } else {
                    PlatformUI.MIRTH_FRAME.alertError(parent, updateSummary.getLibrariesCause().getMessage());
                }
            } else {
                message = "One or more code templates or libraries is outdated " + "(use the \"overwrite\" option to import them anyway).";
                PlatformUI.MIRTH_FRAME.alertError(parent, message);
            }
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }

        return false;
    }

    /**
     * Checks to see if the passed in template id already exists
     */
    private CodeTemplate getTemplateById(String id) throws ClientException {
        Client client = parent.mirthClient;
        try {
            for (CodeTemplate template : client.getAllCodeTemplates()) {
                if (template.getId().equalsIgnoreCase(id)) {
                    return template;
                }
            }
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }

        return null;
    }

    // ========== Tree Node Classes ==========

    /**
     * Represents a library node in the tree
     */
    private static class LibraryTreeNode {
        String id;
        String name;

        LibraryTreeNode(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Represents a template node in the tree
     */
    private static class TemplateTreeNode {
        RepoItemMetadata metadata;

        TemplateTreeNode(RepoItemMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public String toString() {
            return metadata.getName();
        }
    }

    // ========== Custom Tree Cell Renderer ==========

    /**
     * Custom renderer to display libraries and templates with icons and formatting
     */
    private static class CodeTemplateTreeCellRenderer extends DefaultTreeCellRenderer {
        private Icon libraryIcon;
        private Icon templateIcon;

        public CodeTemplateTreeCellRenderer() {
            // Use default Swing icons
            libraryIcon = UIManager.getIcon("Tree.closedIcon");
            templateIcon = UIManager.getIcon("FileView.fileIcon");
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();

                if (userObject instanceof LibraryTreeNode) {
                    LibraryTreeNode libNode = (LibraryTreeNode) userObject;
                    setIcon(libraryIcon);

                    // Show template count
                    int count = node.getChildCount();
                    setText(libNode.name + " (" + count + ")");

                    // Gray out "Unknown Library"
                    if (libNode.id == null) {
                        setForeground(Color.GRAY);
                    }
                } else if (userObject instanceof TemplateTreeNode) {
                    TemplateTreeNode tempNode = (TemplateTreeNode) userObject;
                    setIcon(templateIcon);

                    // ✅ Show name with shortened ID
                    String displayText = tempNode.metadata.getName();
                    String id = tempNode.metadata.getId();

                    if (id != null && !id.isEmpty()) {
                        displayText += " (" + id + ")";
                    }

                    setText(displayText);
                }
            }

            return this;
        }
    }

    // ========== Helper Classes ==========

    /**
     * Simple DocumentListener helper
     */
    private static final class SimpleDoc implements DocumentListener {
        private final Runnable r;

        SimpleDoc(Runnable r) {
            this.r = r;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            r.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            r.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            r.run();
        }
    }
}