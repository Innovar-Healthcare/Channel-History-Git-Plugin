package com.innovarhealthcare.channelHistory.client.dialog;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Map;

import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Dialog for committing and pushing global scripts to remote repository.
 * Uses JOptionPane-style layout with icon on left, content on right.
 * Shows input form, processing state, and result with professional UX.
 */
public class CommitPushGlobalScriptsDialog extends JDialog {

    private static final Logger logger = LogManager.getLogger(CommitPushGlobalScriptsDialog.class);

    // State constants
    private static final String STATE_INPUT = "INPUT";
    private static final String STATE_PROCESSING = "PROCESSING";
    private static final String STATE_RESULT = "RESULT";

    private final Frame parent;

    // Input state components
    private JLabel inputIconLabel;
    private JLabel commentLabel;
    private JTextArea commentField;
    private JScrollPane commentScrollPane;
    private JButton saveButton;
    private JButton cancelButton;

    // Processing state components
    private JLabel processingIconLabel;
    private JLabel processingMessageLabel;
    private JProgressBar progressBar;

    // Result state components
    private JLabel resultIconLabel;
    private JLabel resultMessageLabel;
    private JButton closeButton;

    // Panels for each state
    private JPanel inputPanel;
    private JPanel processingPanel;
    private JPanel resultPanel;

    // Data
    private String commitMessage;
    private boolean saveSucceeded = false;

    /**
     * Creates and shows the Commit & Push Global Scripts dialog
     *
     * @param parent Parent frame
     */
    public CommitPushGlobalScriptsDialog(Frame parent) {
        super(parent, true);

        this.parent = parent;

        initComponents();
        initLayout();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Commit & Push Global Scripts");
        pack();
        setLocationRelativeTo(parent);

        // Show input state initially
        showState(STATE_INPUT);

        setVisible(true);
    }

    /**
     * Initializes all UI components
     */
    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        // ========== INPUT STATE COMPONENTS ==========
        inputIconLabel = new JLabel(UIManager.getIcon("OptionPane.questionIcon"));

        commentLabel = new JLabel("Commit Message:");

        commentField = new JTextArea(5, 30);
        commentField.setWrapStyleWord(true);
        commentField.setLineWrap(true);
        commentField.setToolTipText("Enter a commit message describing the changes to the global scripts.");

        commentScrollPane = new JScrollPane(commentField);
        commentScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        commentScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        saveButton = new JButton("Commit & Push");
        saveButton.addActionListener(evt -> onCommitPushClicked());

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> dispose());

        // ========== PROCESSING STATE COMPONENTS ==========

        processingIconLabel = new JLabel(UIManager.getIcon("OptionPane.informationIcon"));

        processingMessageLabel = new JLabel("<html>Committing and pushing global scripts to repository...<br>Please wait...</html>");
        processingMessageLabel.setFont(processingMessageLabel.getFont().deriveFont(Font.PLAIN, 12f));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(300, 20));

        // ========== RESULT STATE COMPONENTS ==========

        resultIconLabel = new JLabel(); // Icon set dynamically (success vs error)

        resultMessageLabel = new JLabel();
        resultMessageLabel.setFont(resultMessageLabel.getFont().deriveFont(Font.PLAIN, 12f));

        closeButton = new JButton("Close");
        closeButton.addActionListener(evt -> dispose());
    }

    /**
     * Initializes layout for all state panels
     */
    private void initLayout() {
        // Use CardLayout to switch between states
        setLayout(new CardLayout());

        // Create panels for each state
        inputPanel = createInputPanel();
        processingPanel = createProcessingPanel();
        resultPanel = createResultPanel();

        // Add panels to card layout
        add(inputPanel, STATE_INPUT);
        add(processingPanel, STATE_PROCESSING);
        add(resultPanel, STATE_RESULT);
    }

    /**
     * Creates the input panel with JOptionPane-style layout
     * Icon on left, content on right
     */
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, novisualpadding, hidemode 3, fill", "[]10[grow]",  // icon column | gap | content column
                "[][]push[]"   // rows
        ));
        panel.setBackground(getBackground());

        // Icon (left side, spans multiple rows)
        panel.add(inputIconLabel, "spany 2, aligny top");

        // Comment label (right side)
        panel.add(commentLabel, "wrap");

        // Comment text area (skip icon column, take full width)
        panel.add(commentScrollPane, "skip 1, growx, wrap");

        // Separator
        panel.add(new JSeparator(), "skip 1, growx, gaptop 10, wrap");

        // Buttons (right-aligned)
        panel.add(saveButton, "skip 1, split 2, right, gaptop 5");
        panel.add(cancelButton, "");

        return panel;
    }

    /**
     * Creates the processing panel with JOptionPane-style layout
     */
    private JPanel createProcessingPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, novisualpadding, hidemode 3, fill", "[]10[grow]",  // icon | gap | content
                "[]10[]"       // message | progress bar
        ));
        panel.setBackground(getBackground());

        // Icon (left side, spans rows)
        panel.add(processingIconLabel, "spany 2, aligny top");

        // Message (right side)
        panel.add(processingMessageLabel, "wrap");

        // Progress bar (skip icon column)
        panel.add(progressBar, "skip 1, growx");

        return panel;
    }

    /**
     * Creates the result panel with JOptionPane-style layout
     */
    private JPanel createResultPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, novisualpadding, hidemode 3, fill", "[]10[grow]",  // icon | gap | content
                "[]push[]"     // message | buttons
        ));
        panel.setBackground(getBackground());

        // Icon (left side)
        panel.add(resultIconLabel, "aligny top");

        // Message (right side)
        panel.add(resultMessageLabel, "wrap");

        // Close button (right-aligned, skip icon column)
        panel.add(closeButton, "skip 1, right, gaptop 10");

        return panel;
    }

    /**
     * Switches to the specified state panel
     */
    private void showState(String state) {
        CardLayout layout = (CardLayout) getContentPane().getLayout();
        layout.show(getContentPane(), state);

        // Resize dialog for new panel
        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Called when Commit & Push button is clicked
     */
    private void onCommitPushClicked() {
        // Get and validate commit message
        commitMessage = StringUtils.trim(commentField.getText());

        if (commitMessage.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a commit message.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            commentField.requestFocus();
            return;
        }

        // Switch to processing state
        showState(STATE_PROCESSING);

        // Execute save in background
        executeSaveAsync();
    }

    /**
     * Executes the save operation asynchronously using SwingWorker
     */
    private void executeSaveAsync() {
        SwingWorker<SaveResult, Void> worker = new SwingWorker<SaveResult, Void>() {

            @Override
            protected SaveResult doInBackground() throws Exception {
                // This runs on background thread - don't touch UI!
                return performSave();
            }

            @Override
            protected void done() {
                // This runs on EDT - safe to update UI
                try {
                    SaveResult result = get();

                    if (result.success) {
                        showSuccessResult(result.details);

                        // Auto-close after 2 seconds
                        Timer closeTimer = new Timer(2000, e -> dispose());
                        closeTimer.setRepeats(false);
                        closeTimer.start();

                    } else {
                        showErrorResult(result.error);
                    }

                } catch (Exception e) {
                    logger.error("Error in save worker", e);
                    showErrorResult(e);
                }
            }
        };

        worker.execute();
    }

    /**
     * Performs the actual save operation (runs on background thread)
     */
    private SaveResult performSave() {
        try {
            // Get current user ID
            String userId = String.valueOf(parent.mirthClient.getCurrentUser().getId());

            // Get global scripts from MC configuration
            Map<String, String> globalScripts = parent.globalScriptsPanel.exportAllScripts();

            if (globalScripts == null || globalScripts.isEmpty()) {
                throw new RuntimeException("No global scripts found to save");
            }

            // Call service to save
            String operationDetails = VersionHistoryServiceClient.getInstance().commitAndPushGlobalScripts(globalScripts, commitMessage, userId);

            logger.info("Global scripts saved successfully: " + operationDetails);

            return new SaveResult(true, operationDetails, null);

        } catch (Exception e) {
            logger.error("Failed to save global scripts", e);
            return new SaveResult(false, null, e);
        }
    }

    /**
     * Shows success result state
     */
    private void showSuccessResult(String details) {
        saveSucceeded = true;

        // Use information icon for success (blue i)
        resultIconLabel.setIcon(UIManager.getIcon("OptionPane.informationIcon"));

        resultMessageLabel.setText("Global scripts committed and pushed successfully!");
        resultMessageLabel.setForeground(new Color(0, 120, 0));

        showState(STATE_RESULT);
    }

    /**
     * Shows error result state
     */
    private void showErrorResult(Exception error) {
        saveSucceeded = false;

        // Use error icon for errors (red X)
        resultIconLabel.setIcon(UIManager.getIcon("OptionPane.errorIcon"));

        String errorMsg = error.getMessage();
        if (errorMsg == null || errorMsg.isEmpty()) {
            errorMsg = "An unknown error occurred";
        }

        resultMessageLabel.setText("<html>Failed to save global scripts:<br>" + errorMsg + "</html>");
        resultMessageLabel.setForeground(new Color(180, 0, 0));

        showState(STATE_RESULT);
    }

    /**
     * Returns whether the save operation succeeded
     */
    public boolean isSaveSucceeded() {
        return saveSucceeded;
    }

    /**
     * Result holder for save operation
     */
    private static class SaveResult {
        final boolean success;
        final String details;
        final Exception error;

        SaveResult(boolean success, String details, Exception error) {
            this.success = success;
            this.details = details;
            this.error = error;
        }
    }
}
