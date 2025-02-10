package com.innovarhealthcare.channelHistory.server.service;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;

import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONObject;

import java.io.*;
import java.util.Properties;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class ChannelService extends ModeService {
    protected String DIRECTORY = "channels";

    public ChannelService(GitRepositoryService gitService) {
        super(gitService);
    }

    @Override
    public String getDirectory() {
        return DIRECTORY;
    }

    public String commitAndPush(Channel channel, String message, PersonIdent committer) {
        Git git = this.gitService.git;
        File dir = this.gitService.dir;
        String serverId = this.gitService.serverId;
        ObjectXMLSerializer serializer = this.gitService.serializer;

        JSONObject result = new JSONObject();

        File newDirectory = new File(dir, getDirectory());
        if (!newDirectory.exists()) {
            newDirectory.mkdir();
        }

        if (isNotChanged(channel)) {
            result.put("validate", "fail");
            result.put("body", "Nothing has changed.");

            return result.toString();
        }

        String id = channel.getId();
        String commentMsg = "Channel name: " + channel.getName() + ". Message: " + message + ". Server Id: " + serverId;

        try {
            // write channel to local repo
            String path = getDirectory() + "/" + id;
            String xml = serializer.serialize(channel);

            File f = new File(dir, path);
            FileOutputStream fOut = new FileOutputStream(f);
            fOut.write(xml.getBytes(GitRepositoryService.CHARSET_UTF_8));
            fOut.close();

            // commit channel
            git.add().addFilepattern(path).call();
            RevCommit rc = git.commit().setCommitter(committer).setMessage(commentMsg).call();

            // push channel to remove repo
            this.gitService.pushToRemoteRepo();

            // store commit id at here
            addChannelCommitIdToProperties(id, rc.getName());

            result.put("validate", "success");
            result.put("body", "Commit and push channel to the remote repo successfully!");
        } catch (Exception e) {
            result.put("validate", "fail");
            result.put("body", e);
        }

        return result.toString();
    }

    private void addChannelCommitIdToProperties(String channelId, String commitId) {
        String EXTENSION_NAME = VersionControlConstants.PLUGIN_NAME;
        ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();

        Properties props = new Properties();
        String key = "channel-" + channelId;
        props.setProperty(key, commitId);

        try {
            extensionController.setPluginProperties(EXTENSION_NAME, props, true);
        } catch (ControllerException ignored) {
        }
    }

    private boolean isChanged(Channel channel) {
        File dir = this.gitService.dir;
        ObjectXMLSerializer serializer = this.gitService.serializer;

        String id = channel.getId();
        String path = getDirectory() + "/" + id;

        File file = new File(dir, path);
        if (!file.exists()) {
            return true;
        }

        int length = (int) file.length();

        if (length <= 0) {
            return true;
        }

        byte[] bytes = new byte[length];

        try {
            FileInputStream fis = new FileInputStream(file);
            fis.read(bytes);

            fis.close();
        } catch (IOException e) {
            return true;
        }

        String content = new String(bytes, GitRepositoryService.CHARSET_UTF_8);
        String xml = serializer.serialize((Object) channel);

        return !xml.equals(content);
    }

    private boolean isNotChanged(Channel channel) {
        return !isChanged(channel);
    }
}
