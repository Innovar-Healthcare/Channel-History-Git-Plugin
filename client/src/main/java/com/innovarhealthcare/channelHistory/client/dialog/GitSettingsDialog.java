package com.innovarhealthcare.channelHistory.client.dialog;

import com.innovarhealthcare.channelHistory.shared.interfaces.ChannelHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.model.GitSettings;
import com.innovarhealthcare.channelHistory.shared.model.TransportType;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.WindowConstants;
import javax.swing.JSeparator;

import java.awt.Dimension;
import java.util.Properties;

import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-02-25 4:25 PM
 */
public class GitSettingsDialog extends MirthDialog {
    private JLabel remoteRepositoryUrlLabel;
    private JTextField remoteRepositoryUrlField;

    private JLabel branchNameLabel;
    private JTextField branchNameField;

    private JLabel transportTypeLabel;
    private JRadioButton sshRadioButton;
    private JRadioButton httpsRadioButton;
    private ButtonGroup transportTypeGroup;

    private JLabel sshPrivateKeyLabel;
    private JTextArea sshPrivateKeyField;
    private JScrollPane sshKeyScrollPane;
    private JButton loadButton;

    private JLabel httpsUsernameLabel;
    private JTextField httpsUsernameField;

    private JLabel httpsPatLabel;
    private JPasswordField httpsPatField;

    private JButton saveButton;
    private JButton cancelButton;
    private JButton validateButton;

    private final Frame parent;
    private GitSettings gitSettings;

    public GitSettingsDialog(Frame parent, GitSettings gitSettings) {
        super(parent, true);

        this.parent = parent;
        this.gitSettings = gitSettings;

        initComponents();
        initLayout();

        setSettings(gitSettings);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Git Settings");
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        remoteRepositoryUrlLabel = new JLabel("Remote Repository Url:");
        remoteRepositoryUrlField = new JTextField();
        remoteRepositoryUrlField.setToolTipText("Enter the remote Git repository URL.");

        branchNameLabel = new JLabel("Branch Name:");
        branchNameField = new JTextField();
        branchNameField.setToolTipText("Enter the branch name to use (e.g., main, develop, or feature/xyz).");

        transportTypeLabel = new JLabel("Transport Type:");
        sshRadioButton = new JRadioButton("SSH");
        sshRadioButton.setBackground(UIConstants.BACKGROUND_COLOR);
        sshRadioButton.addActionListener(evt -> updateTransportTypeFields());
        httpsRadioButton = new JRadioButton("HTTPS");
        httpsRadioButton.setBackground(UIConstants.BACKGROUND_COLOR);
        httpsRadioButton.addActionListener(evt -> updateTransportTypeFields());
        transportTypeGroup = new ButtonGroup();
        transportTypeGroup.add(sshRadioButton);
        transportTypeGroup.add(httpsRadioButton);
        sshRadioButton.setSelected(true);

        sshPrivateKeyLabel = new JLabel("SSH Private Key:");
        sshPrivateKeyField = new JTextArea();
        sshPrivateKeyField.setWrapStyleWord(true);
        sshPrivateKeyField.setLineWrap(true);
        sshPrivateKeyField.setToolTipText("Paste your SSH private key, starting with '-----BEGIN' (e.g., '-----BEGIN OPENSSH PRIVATE KEY-----').");

        sshKeyScrollPane = new JScrollPane(sshPrivateKeyField, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sshKeyScrollPane.setPreferredSize(new Dimension(300, 100));

        loadButton = new JButton("Load");
        loadButton.addActionListener(evt -> loadPrivateKey());

        httpsUsernameLabel = new JLabel("Username:");
        httpsUsernameField = new JTextField();
        httpsUsernameField.setToolTipText("Enter your Git username for HTTPS authentication.");

        httpsPatLabel = new JLabel("Personal Access Token:");
        httpsPatField = new JPasswordField();
        httpsPatField.setToolTipText("Enter your Personal Access Token (PAT) for HTTPS authentication.");

        saveButton = new JButton("Save");
        saveButton.addActionListener(evt -> save());

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> close());
        validateButton = new JButton("Validate");
        validateButton.addActionListener(evt -> validateGitRemoteRepository());

    }

    private void initLayout() {
        setLayout(new MigLayout("insets 8, novisualpadding, hidemode 3, fill", "", "[grow][][]"));

        add(remoteRepositoryUrlLabel, "newline, right");
        add(remoteRepositoryUrlField, "w 300!");

        add(branchNameLabel, "newline, right");
        add(branchNameField, "w 100!");

        add(transportTypeLabel, "newline, right");
        add(sshRadioButton, "split 2");
        add(httpsRadioButton);

        add(sshPrivateKeyLabel, "newline, right");
        add(sshKeyScrollPane);
        add(loadButton, "right");

        add(httpsUsernameLabel, "newline, right");
        add(httpsUsernameField, "w 200!");

        add(httpsPatLabel, "newline, right");
        add(httpsPatField, "w 300!");

        add(new JSeparator(), "newline, sx, growx");

        add(saveButton, "newline, sx, right, split 3");
        add(cancelButton);
        add(validateButton);
    }

    private void updateTransportTypeFields() {
        boolean isSsh = sshRadioButton.isSelected();

        sshPrivateKeyLabel.setVisible(isSsh);
        sshKeyScrollPane.setVisible(isSsh);
        loadButton.setVisible(isSsh);

        httpsUsernameLabel.setVisible(!isSsh);
        httpsUsernameField.setVisible(!isSsh);
        httpsPatLabel.setVisible(!isSsh);
        httpsPatField.setVisible(!isSsh);

        pack();
    }

    private void resetComponents() {
        resetInvalidSettings();
    }

    private void setSettings(GitSettings settings) {
        resetComponents();

        remoteRepositoryUrlField.setText(settings.getRemoteRepositoryUrl());
        branchNameField.setText(settings.getBranchName());

        if (settings.getTransportType() == TransportType.HTTPS) {
            httpsRadioButton.setSelected(true);
        } else {
            sshRadioButton.setSelected(true);
        }

        sshPrivateKeyField.setText(settings.getSshPrivateKey());
        httpsUsernameField.setText(settings.getHttpsUsername());
        httpsPatField.setText(settings.getHttpsPersonalAccessToken());

        updateTransportTypeFields();
    }

    private boolean validateSettings() {
        boolean valid = true;
        StringBuilder errorMessage = new StringBuilder();

        // Reset backgrounds
        resetInvalidSettings();

        String url = remoteRepositoryUrlField.getText().trim();
        if (StringUtils.isEmpty(url)) {
            valid = false;
            remoteRepositoryUrlField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a remote repository URL.")
                    .append(System.lineSeparator());
        }

        String branch = branchNameField.getText().trim();
        if (StringUtils.isEmpty(branch)) {
            valid = false;
            branchNameField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a branch name (e.g., main, develop, or feature/xyz).")
                    .append(System.lineSeparator());
        }

        if (sshRadioButton.isSelected()) {
            String sshKey = sshPrivateKeyField.getText().trim();
            if (StringUtils.isEmpty(sshKey)) {
                valid = false;
                sshPrivateKeyField.setBackground(UIConstants.INVALID_COLOR);
                errorMessage.append("Please provide an SSH private key (starts with '-----BEGIN').")
                        .append(System.lineSeparator());
            }
        } else {
            String username = httpsUsernameField.getText().trim();
            if (StringUtils.isEmpty(username)) {
                valid = false;
                httpsUsernameField.setBackground(UIConstants.INVALID_COLOR);
                errorMessage.append("Please provide a username for HTTPS authentication.")
                        .append(System.lineSeparator());
            }

            String pat = new String(httpsPatField.getPassword()).trim();
            if (StringUtils.isEmpty(pat)) {
                valid = false;
                httpsPatField.setBackground(UIConstants.INVALID_COLOR);
                errorMessage.append("Please provide a Personal Access Token (PAT) for HTTPS authentication.")
                        .append(System.lineSeparator());
            }
        }

        if (!valid) {
            showError(errorMessage.toString());
        }

        return valid;
    }

    private void validateGitRemoteRepository() {
        if (validateSettings()) {
            try {
                Client client = parent.mirthClient;

                ChannelHistoryServletInterface servlet = client.getServlet(ChannelHistoryServletInterface.class);
                String ret = servlet.validateSetting(toProperties());

                showInformation(ret);
            } catch (Exception e) {
                PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
            }
        }
    }

    private void loadPrivateKey() {
        String content = this.parent.browseForFileString(null);
        if (content != null) {
            sshPrivateKeyField.setText(content.trim());
        }
    }

    public void resetInvalidSettings() {
        remoteRepositoryUrlField.setBackground(null);
        branchNameField.setBackground(null);
        sshPrivateKeyField.setBackground(null);
        httpsUsernameField.setBackground(null);
        httpsPatField.setBackground(null);
    }

    private void save() {
        if (!validateSettings()) {
            return;
        }

        gitSettings.setRemoteRepositoryUrl(remoteRepositoryUrlField.getText().trim());
        gitSettings.setBranchName(branchNameField.getText().trim());

        if (sshRadioButton.isSelected()) {
            gitSettings.setTransportType(TransportType.SSH);
            gitSettings.setSshPrivateKey(sshPrivateKeyField.getText().trim());
        } else {
            gitSettings.setTransportType(TransportType.HTTPS);
            gitSettings.setHttpsUsername(httpsUsernameField.getText().trim());
            gitSettings.setHttpsPersonalAccessToken(new String(httpsPatField.getPassword()).trim());
        }

        PlatformUI.MIRTH_FRAME.setSaveEnabled(true);

        close();
    }

    private Properties toProperties() {
        Properties properties = new Properties();
        String url = remoteRepositoryUrlField.getText().trim();
        String branch = branchNameField.getText().trim();

        if (!StringUtils.isEmpty(url)) {
            properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_REPO_URL, url);
        }
        if (!StringUtils.isEmpty(branch)) {
            properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_BRANCH, branch);
        }

        if (sshRadioButton.isSelected()) {
            properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_TRANSPORT_TYPE, TransportType.SSH.name());
            String sshKey = sshPrivateKeyField.getText().trim();
            if (!StringUtils.isEmpty(sshKey)) {
                properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_SSH_KEY, sshKey);
            }
        } else {
            properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_TRANSPORT_TYPE, TransportType.HTTPS.name());
            String username = httpsUsernameField.getText().trim();
            String pat = new String(httpsPatField.getPassword()).trim();
            if (!StringUtils.isEmpty(username)) {
                properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_HTTPS_USERNAME, username);
            }
            if (!StringUtils.isEmpty(pat)) {
                properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_HTTPS_PAT, pat);
            }
        }

        return properties;
    }

    private void close() {
        dispose();
    }

    protected void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(this, msg);
    }

    protected void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(this, err);
    }
}
