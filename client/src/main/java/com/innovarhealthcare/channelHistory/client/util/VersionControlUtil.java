package com.innovarhealthcare.channelHistory.client.util;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;

import java.util.Properties;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 */
public class VersionControlUtil {

    public static boolean isEnableVersionControl(Client client) {
        Properties properties = null;
        try {
            properties = client.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
        } catch (ClientException e) {
            return false;
        }

        return Boolean.parseBoolean(properties.getProperty(VersionControlConstants.VERSION_HISTORY_ENABLE));
    }

    public static boolean isDisableVersionControl(Client client) {
        return !isEnableVersionControl(client);
    }

    public static boolean isAutoCommitEnable(Client client) {
        Properties properties = null;
        try {
            properties = client.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
        } catch (ClientException e) {
            return false;
        }

        return Boolean.parseBoolean(properties.getProperty(VersionControlConstants.VERSION_HISTORY_AUTO_COMMIT_ENABLE));
    }

    public static String getChannelCommitId(Client client, String channelId) {
        Properties properties = null;
        try {
            properties = client.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
        } catch (ClientException e) {
            return null;
        }

        String key = "channel-" + channelId;

        return properties.getProperty(key);
    }

    public static void setChannelCommitId(Client client, String channelId, String commitId) {
        try {
            String key = "channel-" + channelId;
            Properties properties = new Properties();

            properties.setProperty(key, commitId);

            client.setPluginProperties(VersionControlConstants.PLUGIN_NAME, properties, true);
        } catch (ClientException ignored) {
        }
    }

    public static boolean isAutoCommitDisable(Client client) {
        return !isAutoCommitEnable(client);
    }

    public static String getAlertText() {
        return "Version Control is disabled";
    }
}
