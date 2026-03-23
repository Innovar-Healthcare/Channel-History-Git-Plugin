package com.innovarhealthcare.channelHistory.client.dialog;

import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.mirth.connect.client.ui.MirthDialog;
import net.miginfocom.swing.MigLayout;

/**
 * Modal dialog that prompts the user for a commit message, then runs the
 * commit-and-push operation in an embedded SwingWorker.  On success the dialog
 * disposes itself and fires {@code onSuccess}; on failure it displays the error
 * inline so the user can correct the message and retry without reopening.
 *
 * <p>Usage:
 * <pre>
 *     CommitMessageDialog dialog = new CommitMessageDialog(
 *             parent, client, filePaths, userId, () -> reloadChanges());
 *     dialog.setVisible(true);
 * </pre>
 *
 * @author Thai Tran
 */
public class CommitMessageDialog extends MirthDialog {

    private static final Logger logger = LogManager.getLogger(CommitMessageDialog.class);

    private final VersionHistoryServiceClient client;
    private final List<String> filePaths;
    private final String userId;
    private final Runnable onSuccess;

    private JTextArea    messageArea;
    private JLabel       statusLabel;
    private JProgressBar progressBar;
    private JButton      commitButton;
    private JButton      cancelButton;

    public CommitMessageDialog(Frame parent, VersionHistoryServiceClient client,
            List<String> filePaths, String userId, Runnable onSuccess) {
        super(parent, "Commit Message", true);
        this.client    = client;
        this.filePaths = filePaths;
        this.userId    = userId;
        this.onSuccess = onSuccess;
        initComponents();
        initLayout();
        initListeners();
        setSize(500, 300);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    // ========== Initialization ==========

    private void initComponents() {
        messageArea = new JTextArea(4, 40);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);

        statusLabel = new JLabel(" ");
        statusLabel.setVisible(false);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        commitButton = new JButton("Commit & Push");
        commitButton.setEnabled(false);

        cancelButton = new JButton("Cancel");
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 12, novisualpadding, fill",
                "[grow,fill]", "[][grow,fill][][][]"));

        add(new JLabel("Commit Message:"), "wrap");
        add(new JScrollPane(messageArea), "grow, push, wrap");
        add(statusLabel, "wrap");
        add(progressBar, "growx, wrap");

        JPanel buttonPanel = new JPanel(new MigLayout("insets 0, novisualpadding", "push[][]"));
        buttonPanel.add(cancelButton, "w 108!");
        buttonPanel.add(commitButton, "w 108!");
        add(buttonPanel, "growx, wrap");
    }

    private void initListeners() {
        messageArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { syncButton(); }
            @Override public void removeUpdate(DocumentEvent e)  { syncButton(); }
            @Override public void changedUpdate(DocumentEvent e) { syncButton(); }

            private void syncButton() {
                commitButton.setEnabled(!messageArea.getText().trim().isEmpty());
            }
        });

        commitButton.addActionListener(e -> startCommit());

        cancelButton.addActionListener(e -> dispose());

        // ESC closes without committing (when not locked during worker execution)
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (cancelButton.isEnabled()) dispose();
            }
        });

        getRootPane().setDefaultButton(commitButton);
    }

    // ========== Commit Flow ==========

    private void startCommit() {
        commitButton.setEnabled(false);
        cancelButton.setEnabled(false);
        statusLabel.setVisible(false);
        progressBar.setVisible(true);
        new CommitWorker().execute();
    }

    private final class CommitWorker extends SwingWorker<Void, Void> {
        @Override
        protected Void doInBackground() throws Exception {
            client.commitAndPushFiles(filePaths, messageArea.getText().trim(), userId);
            return null;
        }

        @Override
        protected void done() {
            progressBar.setVisible(false);
            progressBar.setIndeterminate(false);
            cancelButton.setEnabled(true);
            commitButton.setEnabled(true);
            try {
                get();
                dispose();
                onSuccess.run();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                String msg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                logger.error("Commit and push failed", ex);
                String safe = msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                statusLabel.setText("<html><font color='#CC0000'>Error: " + safe + "</font></html>");
                statusLabel.setVisible(true);
                // Reset progress bar for potential retry
                progressBar.setIndeterminate(true);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
