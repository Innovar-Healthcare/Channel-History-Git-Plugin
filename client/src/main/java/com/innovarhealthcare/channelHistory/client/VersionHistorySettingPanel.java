package com.innovarhealthcare.channelHistory.client;

import com.innovarhealthcare.channelHistory.client.dialog.GitSettingsDialog;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthRadioButton;
import com.mirth.connect.client.ui.components.MirthTextPane;

import com.mirth.connect.model.Channel;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JTextPane;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;

import java.awt.Color;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.Properties;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class VersionHistorySettingPanel extends AbstractSettingsPanel {

    private VersionHistorySettingPlugin plugin;

    private JPanel enabledPanel;
    private JLabel enabledLabel;
    private MirthRadioButton yesEnabledRadio;
    private MirthRadioButton noEnabledRadio;
    private ButtonGroup enabledButtonGroup;

    private JPanel gitSettingsPanel;
    private JLabel gitSettingLabel;
    private JButton gitSettingsBtn;
    private JLabel syncDeleteLabel;
    private MirthRadioButton syncDeleteYes;
    private MirthRadioButton syncDeleteNo;
    private ButtonGroup syncDeleteButtonGroup;

    private JPanel autoCommitPanel;
    private JLabel autoCommitLabel;
    private MirthRadioButton autoCommitYes;
    private MirthRadioButton autoCommitNo;
    private ButtonGroup autoCommitButtonGroup;
    private JLabel promptLabel;
    private MirthRadioButton promptYes;
    private MirthRadioButton promptNo;
    private ButtonGroup promptButtonGroup;
    private JLabel defaultMessageLabel;
    private JTextPane defaultMessageField;
    private JScrollPane defaultMessageScrollPane;
    private Frame parent;

    private Properties backupChannelCommitIds;
    private VersionHistoryProperties versionHistoryProperties;

    public VersionHistorySettingPanel(String tabName, VersionHistorySettingPlugin plugin) {
        super(tabName);

        this.plugin = plugin;
        this.parent = PlatformUI.MIRTH_FRAME;

        versionHistoryProperties = new VersionHistoryProperties();

        initComponents();

        initLayout();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        enabledPanel = new JPanel();
        enabledPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        enabledPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)), "Enable", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", 1, 11)));

        enabledLabel = new JLabel("Enable:");
        yesEnabledRadio = new MirthRadioButton("Yes");
        yesEnabledRadio.setFocusable(false);
        yesEnabledRadio.setBackground(Color.white);
        yesEnabledRadio.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                enabledActionPerformed();
            }
        });

        noEnabledRadio = new MirthRadioButton("No");
        noEnabledRadio.setFocusable(false);
        noEnabledRadio.setBackground(Color.white);
        noEnabledRadio.setSelected(true);
        noEnabledRadio.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                enabledActionPerformed();
            }
        });

        enabledButtonGroup = new ButtonGroup();
        enabledButtonGroup.add(yesEnabledRadio);
        enabledButtonGroup.add(noEnabledRadio);

        gitSettingsPanel = new JPanel();
        gitSettingsPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        gitSettingsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)), "Git", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", 1, 11)));

        gitSettingLabel = new JLabel("Settings:");
        gitSettingsBtn = new JButton(new ImageIcon(Frame.class.getResource("images/wrench.png")));
        gitSettingsBtn.addActionListener(e -> {
            new GitSettingsDialog(parent, versionHistoryProperties.getGitSettings());
        });

        syncDeleteLabel = new JLabel("Sync Delete:");
        syncDeleteYes = new MirthRadioButton("Yes");
        syncDeleteYes.setFocusable(false);
        syncDeleteYes.setBackground(Color.white);

        syncDeleteNo = new MirthRadioButton("No");
        syncDeleteNo.setFocusable(false);
        syncDeleteNo.setBackground(Color.white);
        syncDeleteNo.setSelected(true);
        syncDeleteButtonGroup = new ButtonGroup();
        syncDeleteButtonGroup.add(syncDeleteYes);
        syncDeleteButtonGroup.add(syncDeleteNo);

        autoCommitPanel = new JPanel();
        autoCommitPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        autoCommitPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)), "Auto Commit", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", 1, 11)));

        autoCommitLabel = new JLabel("Enable:");

        autoCommitYes = new MirthRadioButton("Yes");
        autoCommitYes.setFocusable(false);
        autoCommitYes.setBackground(Color.white);
        autoCommitYes.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                autoCommitActionPerformed();
            }
        });

        autoCommitNo = new MirthRadioButton("No");
        autoCommitNo.setFocusable(false);
        autoCommitNo.setBackground(Color.white);
        autoCommitNo.setSelected(true);
        autoCommitNo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                autoCommitActionPerformed();
            }
        });
        autoCommitButtonGroup = new ButtonGroup();
        autoCommitButtonGroup.add(autoCommitYes);
        autoCommitButtonGroup.add(autoCommitNo);

        promptLabel = new JLabel("Prompt:");

        promptYes = new MirthRadioButton("Yes");
        promptYes.setFocusable(false);
        promptYes.setBackground(Color.white);
        promptYes.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                promptYesNoActionPerformed();
            }
        });

        promptNo = new MirthRadioButton("No");
        promptNo.setFocusable(false);
        promptNo.setBackground(Color.white);
        promptNo.setSelected(true);
        promptNo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                promptYesNoActionPerformed();
            }
        });
        promptButtonGroup = new ButtonGroup();
        promptButtonGroup.add(promptYes);
        promptButtonGroup.add(promptNo);

        defaultMessageLabel = new JLabel("Default Message:");
        defaultMessageField = new MirthTextPane();
        defaultMessageScrollPane = new JScrollPane(defaultMessageField, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        defaultMessageScrollPane.setPreferredSize(new Dimension(300, 100));
    }

    private void initLayout() {
        setLayout(new MigLayout("hidemode 3, novisualpadding, insets 12", "[grow]"));

        // enabledPanel: Right-aligned label with 150-pixel first column
        enabledPanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        enabledPanel.add(enabledLabel);
        enabledPanel.add(yesEnabledRadio, "split, gapleft 12");
        enabledPanel.add(noEnabledRadio, "wrap");

        // gitSettingsPanel: Right-aligned labels with 150-pixel first column
        gitSettingsPanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        gitSettingsPanel.add(gitSettingLabel);
        gitSettingsPanel.add(gitSettingsBtn, "gapleft 12, wrap");
        gitSettingsPanel.add(syncDeleteLabel);
        gitSettingsPanel.add(syncDeleteYes, "split, gapleft 12");
        gitSettingsPanel.add(syncDeleteNo, "wrap");

        // autoCommitPanel: Right-aligned labels with 150-pixel first column
        autoCommitPanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        autoCommitPanel.add(autoCommitLabel);
        autoCommitPanel.add(autoCommitYes, "split, gapleft 12");
        autoCommitPanel.add(autoCommitNo, "wrap");
        autoCommitPanel.add(promptLabel);
        autoCommitPanel.add(promptYes, "split, gapleft 12");
        autoCommitPanel.add(promptNo, "wrap");
        autoCommitPanel.add(defaultMessageLabel);
        autoCommitPanel.add(defaultMessageScrollPane, "gapleft 12, wrap");

        add(enabledPanel, "grow, sx, wrap");
        add(gitSettingsPanel, "grow, sx, wrap");
        add(autoCommitPanel, "grow, sx");
    }

    private void enabledActionPerformed() {
        visibleFields(yesEnabledRadio.isSelected());
    }

    public void visibleFields(boolean isVisible) {
        gitSettingsPanel.setVisible(isVisible);
        autoCommitPanel.setVisible(isVisible);
    }

    private void autoCommitActionPerformed() {
        boolean selected = autoCommitYes.isSelected();
        promptYes.setEnabled(selected);
        promptNo.setEnabled(selected);
        defaultMessageField.setEnabled(selected);
    }

    private void promptYesNoActionPerformed() {
//        defaultMessageField.setVisible(promptNo.isSelected());
    }

    public void setProperties(Properties properties) {
        versionHistoryProperties.fromProperties(properties);

        yesEnabledRadio.setSelected(versionHistoryProperties.isEnableVersionHistory());
        noEnabledRadio.setSelected(!versionHistoryProperties.isEnableVersionHistory());

        autoCommitYes.setSelected(versionHistoryProperties.isEnableAutoCommit());
        autoCommitNo.setSelected(!versionHistoryProperties.isEnableAutoCommit());

        promptYes.setSelected(versionHistoryProperties.isEnableAutoCommitPrompt());
        promptNo.setSelected(!versionHistoryProperties.isEnableAutoCommitPrompt());
        defaultMessageField.setText(versionHistoryProperties.getAutoCommitMsg());

        syncDeleteYes.setSelected(versionHistoryProperties.isEnableSyncDelete());
        syncDeleteNo.setSelected(!versionHistoryProperties.isEnableSyncDelete());

        enabledActionPerformed();

        autoCommitActionPerformed();

        backupChannelCommitIdFromProperties(properties);

        repaint();
        this.getFrame().setSaveEnabled(false);
    }

    public void backupChannelCommitIdFromProperties(Properties properties) {
        backupChannelCommitIds = new Properties();

        try {
            for (Channel channel : parent.mirthClient.getAllChannels()) {
                String key = "channel-" + channel.getId();
                if (properties.containsKey(key)) {
                    backupChannelCommitIds.setProperty(key, properties.getProperty(key));
                }
            }
        } catch (ClientException ignored) {

        }
    }

    public Properties getProperties() {
        versionHistoryProperties.setEnableVersionHistory(yesEnabledRadio.isSelected());
        versionHistoryProperties.setEnableAutoCommit(autoCommitYes.isSelected());
        versionHistoryProperties.setEnableAutoCommitPrompt(promptYes.isSelected());
        versionHistoryProperties.setAutoCommitMsg(defaultMessageField.getText().trim());
        versionHistoryProperties.setEnableSyncDelete(syncDeleteYes.isSelected());

        Properties properties = versionHistoryProperties.toProperties();

        if (backupChannelCommitIds != null) {
            properties.putAll(backupChannelCommitIds);
        }

        return properties;
    }

    public boolean validateFields() {
        boolean valid = true;
        StringBuilder errorMessage = new StringBuilder();

        // Reset backgrounds
        resetInvalidSettings();

        if (!yesEnabledRadio.isSelected()) {
            return true;
        }

        if (!versionHistoryProperties.getGitSettings().validate()) {
            valid = false;
            errorMessage.append("Git Settings are invalid.")
                    .append(System.lineSeparator());
        }

        if (autoCommitYes.isSelected()) {
            String url = defaultMessageField.getText().trim();
            if (StringUtils.isEmpty(url)) {
                valid = false;
                defaultMessageField.setBackground(UIConstants.INVALID_COLOR);
                errorMessage.append("Please provide a default message.")
                        .append(System.lineSeparator());
            }
        }

        if (!valid) {
            showError(errorMessage.toString());
        }

        return valid;
    }

    @Override
    public void doRefresh() {
        if (PlatformUI.MIRTH_FRAME.alertRefresh()) {
            return;
        }

        resetInvalidSettings();

        final String workingId = getFrame().startWorking("Loading " + getTabName() + " properties...");

        final Properties serverProperties = new Properties();

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

            public Void doInBackground() {
                try {
                    Properties propertiesFromServer = plugin.getPropertiesFromServer();
                    if (propertiesFromServer != null) {
                        serverProperties.putAll(propertiesFromServer);
                    }
                } catch (Exception e) {
                    getFrame().alertThrowable(getFrame(), e);
                }
                return null;
            }

            @Override
            public void done() {
                setProperties(serverProperties);
                getFrame().stopWorking(workingId);
            }
        };

        worker.execute();
    }

    @Override
    public boolean doSave() {
        if (!validateFields()) {
            return false;
        }

        final String workingId = getFrame().startWorking("Saving " + getTabName() + " properties...");

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

            public Void doInBackground() {
                try {
                    plugin.setPropertiesToServer(getProperties());
                } catch (Exception e) {
                    getFrame().alertThrowable(getFrame(), e);
                }
                return null;
            }

            @Override
            public void done() {
                setSaveEnabled(false);
                getFrame().stopWorking(workingId);
            }
        };

        worker.execute();

        return true;
    }

    public void resetInvalidSettings() {
        defaultMessageField.setBackground(getBackground());
    }

    protected void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(this, err);
    }
}