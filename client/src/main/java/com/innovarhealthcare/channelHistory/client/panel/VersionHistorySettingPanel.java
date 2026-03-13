package com.innovarhealthcare.channelHistory.client.panel;

import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import java.util.Properties;

import com.innovarhealthcare.channelHistory.client.plugin.VersionHistorySettingPlugin;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.model.Channel;
import net.miginfocom.swing.MigLayout;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class VersionHistorySettingPanel extends AbstractSettingsPanel {

    private VersionHistorySettingPlugin plugin;
    private Frame parent;

    private Properties backupChannelCommitIds;
    private VersionHistoryProperties versionHistoryProperties;

    private JTabbedPane tabbedPane;
    private GeneralTabPanel generalTabPanel;
    private GitSettingsTabPanel gitSettingsTabPanel;
    private GitBehaviorTabPanel gitBehaviorTabPanel;
    private GitStatusTabPanel gitStatusTabPanel;

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

        tabbedPane = new JTabbedPane();
        tabbedPane.setFocusable(false);

        generalTabPanel = new GeneralTabPanel(versionHistoryProperties);
        gitSettingsTabPanel = new GitSettingsTabPanel(versionHistoryProperties);
        gitBehaviorTabPanel = new GitBehaviorTabPanel(versionHistoryProperties);
        gitStatusTabPanel = new GitStatusTabPanel();

        generalTabPanel.addEnabledActionListener(e -> visibleFields(generalTabPanel.isPluginEnabled()));

        tabbedPane.addTab("General", generalTabPanel);
        tabbedPane.addTab("Git Settings", gitSettingsTabPanel);
        tabbedPane.addTab("Git Behavior", gitBehaviorTabPanel);
        tabbedPane.addTab("Git Status", gitStatusTabPanel);

        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedIndex() == 3) { // Git Status tab
                if (PlatformUI.MIRTH_FRAME.isSaveEnabled()) {
                    showError("You have unsaved Git Settings changes. Please save before viewing Git Status.");
                    tabbedPane.setSelectedIndex(1);
                }
            }
        });
    }

    private void initLayout() {
        setLayout(new MigLayout("hidemode 3, novisualpadding, insets 12", "[grow]"));
        add(tabbedPane, "grow, sx");
    }

    public void visibleFields(boolean isVisible) {
        tabbedPane.setEnabledAt(1, isVisible);
        tabbedPane.setEnabledAt(2, isVisible);
        tabbedPane.setEnabledAt(3, isVisible);
        if (!isVisible && tabbedPane.getSelectedIndex() != 0) {
            tabbedPane.setSelectedIndex(0);
        }
    }

    public void setProperties(Properties properties) {
        versionHistoryProperties.fromProperties(properties);

        generalTabPanel.setProperties();
        gitSettingsTabPanel.setProperties();
        gitBehaviorTabPanel.setProperties();

        visibleFields(generalTabPanel.isPluginEnabled());
        backupChannelCommitIdFromProperties(properties);

        repaint();
        this.getFrame().setSaveEnabled(false);
    }

    public Properties getProperties() {
        generalTabPanel.getProperties();
        gitSettingsTabPanel.getProperties();
        gitBehaviorTabPanel.getProperties();

        Properties properties = versionHistoryProperties.toProperties();
        if (backupChannelCommitIds != null) {
            properties.putAll(backupChannelCommitIds);
        }
        return properties;
    }

    public boolean validateFields() {
        resetInvalidSettings();

        if (!generalTabPanel.isPluginEnabled()) {
            return true;
        }

        boolean valid = true;
        StringBuilder errorMessage = new StringBuilder();

        if (!gitSettingsTabPanel.validateFields()) {
            valid = false;
            errorMessage.append("Git Settings are invalid.").append(System.lineSeparator());
        }

        if (!gitBehaviorTabPanel.validateFields()) {
            valid = false;
            errorMessage.append("Please provide a default commit message.").append(System.lineSeparator());
        }

        if (!valid) {
            showError(errorMessage.toString());
        }

        return valid;
    }

    public void resetInvalidSettings() {
        gitSettingsTabPanel.resetInvalidState();
        gitBehaviorTabPanel.resetInvalidState();
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

    @Override
    public void doRefresh() {
        if (PlatformUI.MIRTH_FRAME.alertRefresh()) {
            return;
        }

        resetInvalidSettings();

        final String workingId = getFrame().startWorking("Loading " + getTabName() + " properties...");

        final Properties serverProperties = new Properties();

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
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

    protected void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(this, err);
    }
}
