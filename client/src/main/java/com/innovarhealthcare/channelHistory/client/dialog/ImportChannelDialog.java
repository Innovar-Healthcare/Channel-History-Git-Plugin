package com.innovarhealthcare.channelHistory.client.dialog;

import com.innovarhealthcare.channelHistory.client.model.ChannelRepoTableModel;
import com.innovarhealthcare.channelHistory.client.table.ChannelRepoTable;
import com.innovarhealthcare.channelHistory.client.util.VersionControlUtil;
import com.innovarhealthcare.channelHistory.shared.interfaces.ChannelHistoryServletInterface;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTable;
import com.mirth.connect.model.Channel;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
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
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ImportChannelDialog extends MirthDialog {
    private MirthTable channelRepoTable;
    private JScrollPane channelsScrollPane;

    private ChannelRepoTableModel model;
    private TableRowSorter<ChannelRepoTableModel> sorter;

    private JTextField searchField;
    private JButton clearSearchButton;

    private JButton okButton;
    private JButton cancelButton;

    // Footer-left loader
    private JProgressBar loadingBar;
    private JLabel loadingLabel;
    private boolean loading = false;

    private ChannelHistoryServletInterface gitServlet;
    private final Frame parent;

    public ImportChannelDialog(Frame parent) {
        super(parent, true);
        this.parent = parent;

        initComponents();
        initLayout();

        enterLoadingState();

        new LoadChannelsWorker().execute();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Import Channel From Repo");
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        channelRepoTable = new ChannelRepoTable();
        this.model = (ChannelRepoTableModel) channelRepoTable.getModel();

        sorter = new TableRowSorter<>(this.model);
        channelRepoTable.setRowSorter(sorter);

        reapplySortKeys();

        channelsScrollPane = new JScrollPane(
                channelRepoTable,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        channelsScrollPane.setPreferredSize(new Dimension(600, 300));

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(100, 25));
        searchField.getDocument().addDocumentListener(new SimpleDoc(this::applyFilter));

        clearSearchButton = new JButton("X");
        clearSearchButton.setMargin(new Insets(2, 8, 2, 8));
        clearSearchButton.addActionListener(e -> {
            searchField.setText("");
            sorter.setRowFilter(null);
            searchField.requestFocusInWindow();
        });

        // --- Footer-left loading indicator ---
        loadingLabel = new JLabel("Loading channels…");
        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);

        // --- Buttons ---
        okButton = new JButton("Import");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                int viewRow = channelRepoTable.getSelectedRow();
                if (viewRow < 0) {
                    PlatformUI.MIRTH_FRAME.alertInformation(parent, "You should select at least one channel!");
                    return;
                }
                int modelRow = channelRepoTable.convertRowIndexToModel(viewRow);
                ChannelRepoTableModel m = (ChannelRepoTableModel) channelRepoTable.getModel();
                Channel channel = m.getChannelAt(modelRow);
                if (channel == null) {
                    PlatformUI.MIRTH_FRAME.alertError(parent, "Channel is null");
                    return;
                }
                if (doAddChannel(channel)) {
                    VersionControlUtil.setChannelCommitId(
                            parent.mirthClient, channel.getId(), m.getLastCommitIdAt(modelRow)
                    );
                    dispose();
                }
            }
        });

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> dispose());
    }

    private void initLayout() {
        setLayout(new MigLayout(
                "insets 8, novisualpadding, hidemode 3, fillx",
                "[pref][grow,fill][pref]",
                "[] [grow] []"
        ));

        // Search row
        add(new JLabel("Search:"), "cell 0 0, alignx left");
        add(searchField, "cell 1 0, growx, pushx, split 2");
        add(clearSearchButton, "gapleft 0, wrap");

        // Table
        add(channelsScrollPane, "cell 0 1 3 1, grow, push, wrap");

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
        okButton.setEnabled(channelRepoTable.getRowCount() > 0);
    }

    private void setLoadingVisible(boolean visible) {
        loadingBar.setVisible(visible);
        loadingLabel.setVisible(visible);
        revalidate();
        repaint();
    }

    // ----- Background fetch -----
    private final class LoadChannelsWorker extends SwingWorker<List<String>, Void> {
        @Override
        protected List<String> doInBackground() throws Exception {
            if (gitServlet == null) {
                gitServlet = parent.mirthClient.getServlet(ChannelHistoryServletInterface.class);
            }
            return gitServlet.loadChannelOnRepo();
        }

        @Override
        protected void done() {
            try {
                List<String> channels = get();
                ChannelRepoTableModel newModel = new ChannelRepoTableModel(channels);

                // Swap model and keep sorter working
                channelRepoTable.setModel(newModel);

                ImportChannelDialog.this.model = newModel;

                sorter.setModel(newModel);
                channelRepoTable.setRowSorter(sorter);

                reapplySortKeys();

                applyFilter();

                exitLoadingState();
            } catch (Exception ex) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Failed to load channels in repository");

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

        int cols = channelRepoTable.getColumnModel().getColumnCount();
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

        sorter.setRowFilter(new RowFilter<ChannelRepoTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends ChannelRepoTableModel, ? extends Integer> entry) {
                // Adjust indices if different: assuming 0=ID, 1=Name
                String id = Optional.ofNullable(entry.getValue(0)).map(Object::toString).orElse("").toLowerCase();
                String name = Optional.ofNullable(entry.getValue(1)).map(Object::toString).orElse("").toLowerCase();
                return id.contains(needle) || name.contains(needle);
            }
        });
    }

    // ----- Import helpers (unchanged) -----
    private boolean doAddChannel(Channel channel) {
        try {
            String channelName = channel.getName();
            String channelId = channel.getId();
            Channel idChannelMatch = getChannelById(channelId);
            Channel nameChannelMatch = getChannelByName(channelName);

            if (idChannelMatch != null) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Channel \"" + channelId + "\" already exists.");
                return false;
            }
            if (nameChannelMatch != null) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Channel \"" + channelName + "\" already exists.");
                return false;
            }
            parent.channelPanel.importChannel(channel, true);
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertError(parent, e.getMessage());
            return false;
        }
        return true;
    }

    private Channel getChannelById(String id) throws ClientException {
        Client client = parent.mirthClient;
        for (Channel channel : client.getAllChannels()) {
            if (channel.getId().equalsIgnoreCase(id)) {
                return channel;
            }
        }
        return null;
    }

    private Channel getChannelByName(String name) throws ClientException {
        Client client = parent.mirthClient;
        for (Channel channel : client.getAllChannels()) {
            if (channel.getName().equalsIgnoreCase(name)) {
                return channel;
            }
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


