package com.innovarhealthcare.channelHistory.server.service;

import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class CodeTemplateService extends ModeService {
    protected String DIRECTORY = "codetemplates";

    public CodeTemplateService(GitRepositoryService gitService) {
        super(gitService);
    }

    @Override
    public String getDirectory() {
        return DIRECTORY;
    }


    public String commitAndPush(CodeTemplate template, String message, PersonIdent committer) {
        Git git = this.gitService.git;
        File dir = this.gitService.dir;
        String serverId = this.gitService.serverId;
        ObjectXMLSerializer serializer = this.gitService.serializer;

        JSONObject result = new JSONObject();

        if (isNotChanged(template)) {
            result.put("validate", "fail");
            result.put("body", "Nothing has changed.");

            return result.toString();
        }

        String id = template.getId();
        String commentMsg = "Code Template name: " + template.getName() + ". Message: " + message + ". Server Id: " + serverId;

        try {
            // write code template to local repo
            String path = getDirectory() + "/" + id;
            String xml = serializer.serialize((Object) template);

            File f = new File(dir, path);
            FileOutputStream fOut = new FileOutputStream(f);
            fOut.write(xml.getBytes(GitRepositoryService.CHARSET_UTF_8));
            fOut.close();

            // commit channel
            git.add().addFilepattern(path).call();
            git.commit().setCommitter(committer).setMessage(commentMsg).call();

            // push channel to remove repo
            this.gitService.pushToRemoteRepo();

            result.put("validate", "success");
            result.put("body", "Commit and push code template to the remote repo successfully!");
        } catch (Exception e) {
            result.put("validate", "fail");
            result.put("body", e);
        }

        return result.toString();
    }

    private boolean isChanged(CodeTemplate template) {
        File dir = this.gitService.dir;
        ObjectXMLSerializer serializer = this.gitService.serializer;

        String id = template.getId();
        String path = getDirectory() + "/" + id;

        File file = new File(dir, path);
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
        String xml = serializer.serialize((Object) template);

        return !xml.equals(content);
    }

    private boolean isNotChanged(CodeTemplate template) {
        return !isChanged(template);
    }
}
