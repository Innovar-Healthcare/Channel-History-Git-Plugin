package com.innovarhealthcare.channelHistory.client.dialog;

import com.innovarhealthcare.channelHistory.client.DiffWindow;
import com.innovarhealthcare.channelHistory.client.model.CommitMetaDataTableModel;
import com.innovarhealthcare.channelHistory.client.table.CommitMetaDataTable;
import com.innovarhealthcare.channelHistory.client.util.VersionControlUtil;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.interfaces.ChannelHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;

import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import org.json.JSONObject;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JLabel;
import javax.swing.JDialog;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import java.awt.Window;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Jim(Zi Min) Weng
 * @create 2024-05-07 8:46 AM
 */
public class CodeTemplateHistoryDialog extends JDialog {
    private final String MODE = VersionControlConstants.MODE_CODE_TEMPLATE;
    private static Logger logger = Logger.getLogger(CodeTemplateHistoryDialog.class);

    private JPanel actionPanel;
    private JPanel historyPanel;

    private JButton differenceButton;
    private JButton commitPushButton;
    private JButton pullButton;

    private CommitMetaDataTable tblCommitMetaData;
    private JScrollPane historyScrollPane;

    private ChannelHistoryServletInterface gitServlet;
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
        SwingUtilities.invokeLater(new CodeTemplateHistoryDialog.LoadGitHistoryRunnable(shouldNotifyOnComplete));
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

            CodeTemplate leftCodeTemplate = client.getCodeTemplate(codeTemplateId);
            String left = ObjectXMLSerializer.getInstance().serialize(leftCodeTemplate);

            String right = gitServlet.getContent(codeTemplateId, lastChange.getHash(), MODE);
            CodeTemplate rightCodeTemplate = parse(right, lastChange.getShortHash());

            String leftLabel = leftCodeTemplate.getName() + " - Current - Editing by " + currentUserName;
            String rightLabel = leftCodeTemplate.getName() + " - Time: " + df.format(new Date(lastChange.getTimestamp())) + " - Committed by " + lastChange.getCommitter();

            DiffWindow dw = DiffWindow.create("Code Template Diff", leftLabel, rightLabel, leftCodeTemplate, rightCodeTemplate, left, right, this);
            dw.setSize(parent.getWidth() - 10, parent.getHeight() - 10);
            dw.setVisible(true);
        } catch (Exception e) {
            showError("Failed to show difference in code template");
        }
    }

    private void showDiffWindow() {
        popupMenu.setVisible(false);
        int[] rows = tblCommitMetaData.getSelectedRows();
        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
        CommitMetaData ri1 = model.getCommitMetaDataAt(rows[0]);
        CommitMetaData ri2 = model.getCommitMetaDataAt(rows[1]);

        try {
            String left = gitServlet.getContent(codeTemplateId, ri1.getHash(), MODE);
            CodeTemplate leftCodeTemplate = parse(left, ri1.getShortHash());
            String right = gitServlet.getContent(codeTemplateId, ri2.getHash(), MODE);
            CodeTemplate rightCodeTemplate = parse(right, ri2.getShortHash());

            String labelPrefix = leftCodeTemplate.getName();
            String leftLabel = labelPrefix + " Time: " + df.format(new Date(ri1.getTimestamp())) + " Committed by " + ri1.getCommitter();
            String rightLabel = labelPrefix + " Time: " + df.format(new Date(ri2.getTimestamp())) + " Committed by " + ri1.getCommitter();

            DiffWindow dw = DiffWindow.create("Code Template Diff", leftLabel, rightLabel, leftCodeTemplate, rightCodeTemplate, left, right, this);
            dw.setSize(parent.getWidth() - 10, parent.getHeight() - 10);
            dw.setVisible(true);
        } catch (Exception e) {
            showError("Failed to show difference in code template");
        }
    }

    private CodeTemplate parse(String xml, String rev) {
        return ObjectXMLSerializer.getInstance().deserialize(xml, CodeTemplate.class);
    }

    private void revert(String codeTemplateId, String rev) {
        int option = JOptionPane.showConfirmDialog(this, "Would you like to revert code template to this revision?", "Select an Option", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            Client client = parent.mirthClient;

            try {
                String xml = gitServlet.getContent(codeTemplateId, rev, MODE);
                CodeTemplate codeTemplate = parse(xml, rev);
                if (codeTemplate == null) {
                    showError("Code Template is null");
                    return;
                }

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

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "Commit & Push",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        SwingUtilities.invokeLater(new CommitThenPushCodeTemplateRunnable(StringUtils.trim(textArea.getText())));
    }

    private class LoadGitHistoryRunnable implements Runnable {
        private final boolean shouldNotifyOnComplete;

        LoadGitHistoryRunnable(boolean shouldNotifyOnComplete) {
            this.shouldNotifyOnComplete = shouldNotifyOnComplete;
        }

        @Override
        public void run() {
            try {
                // initialize once
                // doing here because do not want to delay the startup of MC client which takes several seconds to start.
                if (gitServlet == null) {
                    gitServlet = parent.mirthClient.getServlet(ChannelHistoryServletInterface.class);
                }

                // then fetch revisions
                List<String> revisions = gitServlet.getHistory(codeTemplateId, MODE);
                CommitMetaDataTableModel model = new CommitMetaDataTableModel(revisions);
                tblCommitMetaData.setModel(model);

                if (shouldNotifyOnComplete) {
                    showInformation("History refreshed!");
                }
            } catch (Exception e) {
                CommitMetaDataTableModel model = new CommitMetaDataTableModel(new ArrayList<>());
                tblCommitMetaData.setModel(model);

                if (shouldNotifyOnComplete) {
                    showError("Failed to pull code template from repository. Error: " + e.getMessage());
                }
            }
        }
    }

    private class CommitThenPushCodeTemplateRunnable implements Runnable {
        private final String message;

        CommitThenPushCodeTemplateRunnable(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            try {
                Client client = parent.mirthClient;

                // initialize once
                // doing here because do not want to delay the startup of MC client which takes several seconds to start.
                if (gitServlet == null) {
                    gitServlet = client.getServlet(ChannelHistoryServletInterface.class);
                }

                // then fetch revisions
                String userId = String.valueOf(client.getCurrentUser().getId());
                String response = gitServlet.commitAndPushCodeTemplate(codeTemplateId, message, userId);

                JSONObject resObj = new JSONObject(response);
                if (resObj.get("validate").equals("success")) {
                    showInformation((String) (resObj.get("body")));

                    // fetch history panel again at here
                    loadHistory(false);
                } else {
                    showError("Error: " + resObj.get("body"));
                }
            } catch (Exception e) {
                showError("Failed to commit and push code template to repository");
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