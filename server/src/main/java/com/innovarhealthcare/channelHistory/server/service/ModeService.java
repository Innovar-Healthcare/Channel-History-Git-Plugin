package com.innovarhealthcare.channelHistory.server.service;

import org.apache.commons.lang3.StringUtils;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public abstract class ModeService {
    public final GitRepositoryService gitService;

    public abstract String getDirectory();

    public ModeService(GitRepositoryService gitService) {
        this.gitService = gitService;
    }

    public List<String> getHistory(String fileName) throws Exception {
        List<String> lst = new ArrayList<>();

        Repository repo = this.gitService.git.getRepository();
        if (repo.resolve(Constants.HEAD) != null) {
            Iterator<RevCommit> rcItr;
            String path = getDirectory() + "/" + fileName;
            rcItr = this.gitService.git.log().addPath(path).call().iterator();
            while (rcItr.hasNext()) {
                RevCommit rc = rcItr.next();
                lst.add(toRevisionInfo(rc));
            }
        }

        return lst;
    }

    public String getContent(String fileName, String revision) throws Exception {
        String content = null;
        if (StringUtils.isBlank(fileName) || StringUtils.isBlank(revision)) {
            return content;
        }

        Repository repo = this.gitService.git.getRepository();
        String path = getDirectory() + "/" + fileName;

        try (TreeWalk tw = new TreeWalk(repo)) {
            ObjectId rcid = repo.resolve(revision);
            if (rcid != null) {
                RevCommit rc = repo.parseCommit(rcid);

                tw.setRecursive(true);
                tw.setFilter(PathFilter.create(path));

                tw.addTree(rc.getTree());
                if (tw.next()) {
                    ObjectLoader objLoader = repo.open(tw.getObjectId(0));
                    ObjectStream stream = objLoader.openStream();
                    byte[] buf = new byte[1024];
                    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                    while (true) {
                        int len = stream.read(buf);
                        if (len <= 0) {
                            break;
                        }
                        byteOut.write(buf, 0, len);
                    }
                    stream.close();

                    content = new String(byteOut.toByteArray(), GitRepositoryService.CHARSET_UTF_8);
                }
            }
        } catch (Exception e) {
//            logger.debug("commit " + revision + " not found for file " + fileName, e);
        }

        return content;
    }

    public List<String> load() throws Exception {
        List<String> lst = new ArrayList<>();

        Repository repo = this.gitService.git.getRepository();
        String path = getDirectory() + "/";

        ObjectId lastCommitId = repo.resolve(Constants.HEAD);
        RevWalk revWalk = new RevWalk(repo);
        RevCommit commit = revWalk.parseCommit(lastCommitId);
        RevTree tree = commit.getTree();

        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(false);
        treeWalk.setFilter(PathFilter.create(path));

        while (treeWalk.next()) {
            if (treeWalk.isSubtree()) {
                treeWalk.enterSubtree();
            } else {
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repo.open(objectId);
                String content = new String(loader.getBytes(), GitRepositoryService.CHARSET_UTF_8);

                lst.add(content);
            }
        }

        return lst;
    }

    private String toRevisionInfo(RevCommit rc) {
        JSONObject ri = new JSONObject();

        PersonIdent committer = rc.getCommitterIdent();
        ri.put("CommitterEmail", committer.getEmailAddress());
        ri.put("CommitterName", committer.getName());
        ri.put("Hash", rc.getName());
        ri.put("Message", rc.getFullMessage());
        ri.put("Time", committer.getWhen().getTime());

        return ri.toString();
    }
}
