package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.client.model.CodeTemplateRepoTableModel;
import com.innovarhealthcare.channelHistory.shared.interfaces.channelHistoryServletInterface;

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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Thai Tran
 * @create 2024-11-20 2:30 PM
 */
public class ImportCodeTemplateDialog extends MirthDialog {
    private JLabel libraryLabel;
    private JComboBox<String> libraryComboBox;

    private MirthTable codeTemplateRepoTable;
    private JScrollPane codeTemplateScrollPane;

    private JButton okButton;
    private JButton cancelButton;

    private channelHistoryServletInterface gitServlet;
    private final Frame parent;

    public ImportCodeTemplateDialog(Frame parent) {
        super(parent, true);

        this.parent = parent;

        initComponents();
        initLayout();

        // start thread to load channels on repo
        SwingUtilities.invokeLater(new LoadCodeTemplateInRepoRunnable());

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Import Code Template From Repo");
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        Map<String, CodeTemplateLibrary> codeTemplateLibraries = parent.codeTemplatePanel.getCachedCodeTemplateLibraries();

        libraryLabel = new JLabel("Library:");
        libraryComboBox = new JComboBox<String>();
        List<String> libraryNames = new ArrayList<>();
        for (CodeTemplateLibrary library : codeTemplateLibraries.values()) {
            libraryNames.add(library.getName());
        }
        libraryComboBox.setModel(new DefaultComboBoxModel<String>(libraryNames.toArray(new String[libraryNames.size()])));

        codeTemplateRepoTable = new MirthTable() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        codeTemplateRepoTable.setRowSelectionAllowed(true);
        codeTemplateRepoTable.setColumnSelectionAllowed(false);

        codeTemplateScrollPane = new JScrollPane(codeTemplateRepoTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        codeTemplateScrollPane.setPreferredSize(new Dimension(600, 300));

        okButton = new JButton("Import");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                int row = codeTemplateRepoTable.getSelectedRow();

                if (row < 0) {
                    PlatformUI.MIRTH_FRAME.alertInformation(parent, "You should select at least one code template!");
                } else {
                    String libraryName = (String) libraryComboBox.getSelectedItem();
                    CodeTemplateLibrary matchLibrary = null;
                    for (CodeTemplateLibrary library : codeTemplateLibraries.values()) {
                        if (library.getName().equalsIgnoreCase(libraryName)) {
                            matchLibrary = library;
                            break;
                        }
                    }

                    if (matchLibrary == null) {
                        PlatformUI.MIRTH_FRAME.alertError(parent, "Library is not found");
                    }

                    CodeTemplateRepoTableModel model = (CodeTemplateRepoTableModel) codeTemplateRepoTable.getModel();
                    CodeTemplate template = model.getCodeTemplateAt(row);

                    if (template != null) {
                        try {
                            if (doAddCodeTemplate(template, matchLibrary)) {
                                dispose();

                                parent.codeTemplatePanel.doRefreshCodeTemplates();
                            }
                        } catch (ClientException e) {
                            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
                        }
                    } else {
                        PlatformUI.MIRTH_FRAME.alertError(parent, "Code Template is null");
                    }
                }
            }
        });

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                dispose();
            }
        });
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 8, novisualpadding, hidemode 3, fill", "", "[grow][][]"));

        add(libraryLabel, "newline, left, split");
        add(libraryComboBox, "w 120!");

        add(codeTemplateScrollPane, "newline, grow, push");

        add(new JSeparator(), "newline, growx");

        add(okButton, "newline, w 120!, sx, right, split");
        add(cancelButton, "w 51!");
    }

    private class LoadCodeTemplateInRepoRunnable implements Runnable {
        LoadCodeTemplateInRepoRunnable() {
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
                codeTemplateRepoTable.setModel(new CodeTemplateRepoTableModel(gitServlet.loadCodeTemplateOnRepo()));
            } catch (Exception e) {
                PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
            }
        }
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
}