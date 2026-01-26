package com.innovarhealthcare.channelHistory.client.dialog;

import com.innovarhealthcare.channelHistory.client.model.CodeTemplateRepoTableModel;
import com.innovarhealthcare.channelHistory.client.table.CodeTemplateRepoTable;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTable;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrarySaveResult;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
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

/**
 * @author Thai Tran
 * @create 2024-11-20 2:30 PM
 */
public class ImportCodeTemplateDialog extends MirthDialog {
    private MirthTable codeTemplateRepoTable;
    private JScrollPane codeTemplateScrollPane;

    private CodeTemplateRepoTableModel model;
    private TableRowSorter<CodeTemplateRepoTableModel> sorter;

    private JTextField searchField;
    private JButton clearSearchButton;

    private JButton okButton;
    private JButton cancelButton;

    // Footer-left loader
    private JProgressBar loadingBar;
    private JLabel loadingLabel;
    private boolean loading = false;
    private Map<String, CodeTemplateLibrary> codeTemplateLibraries;

    private VersionHistoryServletInterface gitServlet;
    private final Frame parent;

    public ImportCodeTemplateDialog(Frame parent) {
        super(parent, true);

        this.parent = parent;
        this.codeTemplateLibraries = parent.codeTemplatePanel.getCachedCodeTemplateLibraries();
        initComponents();
        initLayout();

        // start thread to load channels on repo
        new ImportCodeTemplateDialog.LoadCodeTemplateWorker().execute();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Import Code Template From Repo");
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        codeTemplateRepoTable = new CodeTemplateRepoTable();
        this.model = (CodeTemplateRepoTableModel) codeTemplateRepoTable.getModel();

        sorter = new TableRowSorter<>(this.model);
        codeTemplateRepoTable.setRowSorter(sorter);

        reapplySortKeys();

        codeTemplateScrollPane = new JScrollPane(codeTemplateRepoTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        codeTemplateScrollPane.setPreferredSize(new Dimension(600, 300));

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(100, 25));
        searchField.getDocument().addDocumentListener(new ImportCodeTemplateDialog.SimpleDoc(this::applyFilter));

        clearSearchButton = new JButton("X");
        clearSearchButton.setMargin(new Insets(2, 8, 2, 8));
        clearSearchButton.addActionListener(e -> {
            searchField.setText("");
            sorter.setRowFilter(null);
            searchField.requestFocusInWindow();
        });

        // --- Footer-left loading indicator ---
        loadingLabel = new JLabel("Loading code templates…");
        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);

        okButton = new JButton("Import");
        okButton.addActionListener(evt -> onOkImport(evt));

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> dispose());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 8, novisualpadding, hidemode 3, fillx", "[pref][grow,fill][pref]", "[] [grow] []"));

        // Search row
        add(new JLabel("Search:"), "cell 0 0, alignx left");
        add(searchField, "cell 1 0, growx, pushx, split 2");
        add(clearSearchButton, "gapleft 0, wrap");

        // Table
        add(codeTemplateScrollPane, "cell 0 1 3 1, grow, push, wrap");

        // Left side (progress + text)
        add(loadingBar, "cell 0 2, alignx left");
        add(loadingLabel, "cell 0 2, gapleft 8, alignx left");

        // Right side (buttons)
        add(okButton, "cell 2 2, split 2, alignx right, w 120!");
        add(cancelButton, "w 70!");
    }

    // ----- Loading state -----
    private void enterLoadingState() {
        loading = true;
        okButton.setEnabled(false);
        setLoadingVisible(true);
    }

    private void exitLoadingState() {
        loading = false;
        setLoadingVisible(false);
        okButton.setEnabled(codeTemplateRepoTable.getRowCount() > 0);
    }

    private void setLoadingVisible(boolean visible) {
        loadingBar.setVisible(visible);
        loadingLabel.setVisible(visible);
        revalidate();
        repaint();
    }

    // ----- Background fetch -----
    private final class LoadCodeTemplateWorker extends SwingWorker<List<String>, Void> {
        @Override
        protected List<String> doInBackground() throws Exception {
            if (gitServlet == null) {
                gitServlet = parent.mirthClient.getServlet(VersionHistoryServletInterface.class);
            }
            return gitServlet.loadCodeTemplateOnRepo();
        }

        @Override
        protected void done() {
            try {
                List<String> templates = get();
                CodeTemplateRepoTableModel newModel = new CodeTemplateRepoTableModel(templates);

                // Swap model and keep sorter working
                codeTemplateRepoTable.setModel(newModel);

                ImportCodeTemplateDialog.this.model = newModel;

                sorter.setModel(newModel);
                codeTemplateRepoTable.setRowSorter(sorter);

                reapplySortKeys();

                applyFilter();

                exitLoadingState();
            } catch (Exception ex) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Failed to load code templates in repository");

                okButton.setEnabled(false);

                setLoadingVisible(false);
            }
        }
    }

    // ----- Sort & Filter -----
    private void reapplySortKeys() {
        if (sorter == null) {
            return;
        }

        int cols = codeTemplateRepoTable.getColumnModel().getColumnCount();
        List<RowSorter.SortKey> keys = new ArrayList<>();

        if (cols > 1) {
            keys.add(new RowSorter.SortKey(1, SortOrder.ASCENDING));
        }// Name

        if (cols > 0) {
            keys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
        } // ID

        try {
            sorter.setSortKeys(keys.isEmpty() ? null : keys);
        } catch (IllegalArgumentException ignore) {
            // Columns not ready—skip
        }
    }

    private void applyFilter() {
        String text = Optional.ofNullable(searchField.getText()).orElse("").trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }
        final String needle = text.toLowerCase();

        sorter.setRowFilter(new RowFilter<CodeTemplateRepoTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends CodeTemplateRepoTableModel, ? extends Integer> entry) {
                // Adjust indices if different: assuming 0=ID, 1=Name
                String id = Optional.ofNullable(entry.getValue(0)).map(Object::toString).orElse("").toLowerCase();
                String name = Optional.ofNullable(entry.getValue(1)).map(Object::toString).orElse("").toLowerCase();
                return id.contains(needle) || name.contains(needle);
            }
        });
    }

    private void onOkImport(ActionEvent evt) {
        int viewRow = codeTemplateRepoTable.getSelectedRow();
        if (viewRow < 0) {
            PlatformUI.MIRTH_FRAME.alertInformation(parent, "You should select at least one code template!");
            return;
        }

        int modelRow = codeTemplateRepoTable.convertRowIndexToModel(viewRow);
        CodeTemplateRepoTableModel model = (CodeTemplateRepoTableModel) codeTemplateRepoTable.getModel();
        CodeTemplate template = model.getCodeTemplateAt(modelRow);
        if (template == null) {
            PlatformUI.MIRTH_FRAME.alertError(parent, "Code Template is null");
            return;
        }

        // Choose a library
        CodeTemplateLibrary selectedLib = promptForLibrarySelectionByIndex();
        if (selectedLib == null) {
            return;
        }

        try {
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
        int result = JOptionPane.showConfirmDialog(this, combo, "Choose Library", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

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
                PlatformUI.MIRTH_FRAME.alertError(parent, "Template \"" + templateId + "\" already exists.");
                return false;
            }

            Map<String, CodeTemplateLibrary> libraryMap = new HashMap<String, CodeTemplateLibrary>();
            for (CodeTemplateLibrary library : client.getCodeTemplateLibraries(null, false)) {
                libraryMap.put(library.getId(), library);
            }

            List<CodeTemplateLibrary> libraries = new ArrayList<CodeTemplateLibrary>();
            selectedLibrary.getCodeTemplates().add(template);
            libraries.add(selectedLibrary);

            Map<String, CodeTemplate> codeTemplateMap = new HashMap<String, CodeTemplate>();
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

            CodeTemplateLibrarySaveResult updateSummary = client.updateLibrariesAndTemplates(new ArrayList<CodeTemplateLibrary>(libraryMap.values()), new HashSet<String>(), new ArrayList<CodeTemplate>(codeTemplateMap.values()), new HashSet<String>(), true);

            String message = "";
            if (!updateSummary.isOverrideNeeded()) {
                if (updateSummary.isLibrariesSuccess()) {
                    List<CodeTemplate> failedCodeTemplates = new ArrayList<CodeTemplate>();
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
                        // successfully imported code template
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
                message = "One or more code templates or libraries is outdated (use the \"overwrite\" option to import them anyway).";
                PlatformUI.MIRTH_FRAME.alertError(parent, message);
            }
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }

        return false;
    }

    /**
     * Checks to see if the passed in channel id already exists
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

    // ----- Small helper -----
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