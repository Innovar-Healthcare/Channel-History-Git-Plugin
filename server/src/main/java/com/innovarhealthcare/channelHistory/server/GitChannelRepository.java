/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.innovarhealthcare.channelHistory.server;


import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.File;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * @author Jim(Zi Min) Weng
 * @create 2024-04-17 9:25 AM
 */
public class GitChannelRepository {
    
    private static GitChannelRepository channelRepo;

    private Repository repo;

    private Git git;
    
    private File dir;

    private ObjectXMLSerializer serializer;

    private Charset utf8 = StandardCharsets.UTF_8;

    private static Logger log = LoggerFactory.getLogger(GitChannelRepository.class);


    public static final String DATA_DIR = "InnovarHealthcare-version-control";
    private String serverId;
    private String remoteGitRepoUrl;
    private String remoteGitBranch;
    private byte[] SshKeybytes =new byte[0];
    private SshSessionFactory sshSessionFactory;
    private GitChannelRepository() {

    }
    
    public static synchronized void init(String parentDir, ObjectXMLSerializer serializer, Properties properties) {
        if(channelRepo == null) {
            channelRepo = new GitChannelRepository();
            try {
                log.info("initializing channel and code template repository...");
                if(parentDir == null) {
                    parentDir = Donkey.getInstance().getConfiguration().getAppData();
                }
                channelRepo.serverId = Donkey.getInstance().getConfiguration().getServerId();
                channelRepo.dir = new File(parentDir, DATA_DIR);
                channelRepo.serializer = serializer;


            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public static GitChannelRepository getInstance() {

        return channelRepo;

    }
    
    public File getDir() {
        return dir;
    }

    public String validateSetting(Properties properties) throws Exception {
        channelRepo.remoteGitRepoUrl = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_REPO_URL);
        channelRepo.SshKeybytes = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_SSH_KEY).getBytes(utf8);
        channelRepo.remoteGitBranch = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_BRANCH);

        sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }
            @Override
            protected JSch createDefaultJSch(FS fs ) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch( fs );
                defaultJSch.addIdentity("mirthVersionHistoryKey", channelRepo.SshKeybytes, (byte[]) null, (byte[]) null);
                return defaultJSch;
            }

        };
        JSONObject result = new JSONObject();
        if(null == channelRepo.repo){
            try {
                getGitRepo();
                result.put("validate", "success");
                result.put("body", "Connect to the remote repo successfully!");

            }catch (Exception e){
                result.put("validate", "fail");
                result.put("body", e.toString());
                return result.toString();
            }
        }
        try {
            channelRepo.git = Git.open( new File(channelRepo.dir, ".git") );
        }catch (Exception e){
            result.put("validate", "fail");
            result.put("body", e.toString());
            return result.toString();
        }
        final LsRemoteCommand lsCmd = channelRepo.git.lsRemote();
        lsCmd.setRemote(channelRepo.remoteGitRepoUrl);
        lsCmd.setTransportConfigCallback( new TransportConfigCallback() {
            @Override
            public void configure( Transport transport ) {
                SshTransport sshTransport = (SshTransport)transport;
                sshTransport.setSshSessionFactory( sshSessionFactory );
            }
        } );

        try {
            lsCmd.call();
            result.put("validate", "success");
            result.put("body", "Connect to the remote repo successfully!");

        }catch (Exception e){
            result.put("validate", "fail");
            result.put("body", e.toString());
            return result.toString();
        }

        return result.toString();
    }
    public void getGitRepo() throws Exception{
        if(channelRepo.dir.exists()){
            //if repo exist on local, do a pull to get latest commit
            if(Files.exists(new File(channelRepo.dir, ".git").toPath())) {
                Git git = Git.open( new File(channelRepo.dir, ".git") );
                PullCommand pullCommand = git.pull();
                pullCommand.setRemote(channelRepo.remoteGitBranch);
                pullCommand.setRemoteBranchName(channelRepo.remoteGitBranch);
                pullCommand.setTransportConfigCallback( new TransportConfigCallback() {
                    @Override
                    public void configure( Transport transport ) {
                        SshTransport sshTransport = (SshTransport)transport;
                        sshTransport.setSshSessionFactory( sshSessionFactory );
                    }
                } );
                pullCommand.call();
            } else {
                throw new Exception("The folder "+channelRepo.dir+" is not a Git repo");
            }
        }else {
            //if repot does not exist, do a clone to build the repo
            CloneCommand cloneCommand = Git.cloneRepository();
            cloneCommand.setURI(channelRepo.remoteGitRepoUrl);
            cloneCommand.setDirectory(channelRepo.dir);
            cloneCommand.setBranch(channelRepo.remoteGitBranch);
            cloneCommand.setRemote(channelRepo.remoteGitBranch);
            cloneCommand.setTransportConfigCallback(new TransportConfigCallback() {
                @Override
                public void configure(Transport transport) {
                    SshTransport sshTransport = (SshTransport) transport;
                    sshTransport.setSshSessionFactory(sshSessionFactory);
                }
            });
            cloneCommand.call();
        }

        Git git = Git.init().setDirectory(channelRepo.dir).call();
        channelRepo.repo = git.getRepository();
        channelRepo.git = git;
    }
    public void updateSetting(Properties properties) throws Exception {
        channelRepo.SshKeybytes = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_SSH_KEY).getBytes(utf8);
        channelRepo.remoteGitRepoUrl = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_REPO_URL);
        channelRepo.remoteGitBranch = properties.getProperty(VersionControlConstants.VERSION_HISTORY_REMOTE_BRANCH);
        JSONObject result = new JSONObject();
        sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }
            @Override
            protected JSch createDefaultJSch(FS fs ) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch( fs );
                defaultJSch.addIdentity("mirthVersionHistoryKey", channelRepo.SshKeybytes, (byte[]) null, (byte[]) null);
                return defaultJSch;
            }

        };
        try {
            getGitRepo();
        }catch (Exception e){
            log.error(e.toString());
        }


    }

    public void updateChannel(Channel channel, PersonIdent committer) {
        updateFile(channel.getId(), channel, committer);
    }

    private void updateFile(String id, Object obj, PersonIdent committer) {
        try {
            if(obj instanceof Channel){
                Channel channel = (Channel) obj;
                File cDir = new File(dir, "channels");
                if(!cDir.exists()){
                    Boolean isCreated = cDir.mkdirs();
                    if(isCreated){
                        log.error("Error - can not create folder channels in InnovarHealthcare-version-control");
                    }
                }
                File f = new File(dir, "channels/"+id);
                String xml = serializer.serialize(obj);
                FileOutputStream fout = new FileOutputStream(f);
                fout.write(xml.getBytes(utf8));
                fout.close();
                git.add().addFilepattern("channels/"+id).call();
                git.commit().setCommitter(committer).setMessage(channel.getName() +" auto-committed by Innovar Healthcare Git Plugin on Server Id "+serverId).call();
            } else if(obj instanceof CodeTemplate){
                CodeTemplate codeTemplate = (CodeTemplate) obj;
                File ctDir = new File(dir, "codetemplates");
                if(!ctDir.exists()){
                    Boolean isCreated = ctDir.mkdirs();
                    if(isCreated){
                        log.error("Error - can not create folder codetemplates in InnovarHealthcare-version-control");
                    }
                }
                File f = new File(dir, "codetemplates/"+id);
                String xml = serializer.serialize(obj);
                FileOutputStream fout = new FileOutputStream(f);
                fout.write(xml.getBytes(utf8));
                fout.close();
                git.add().addFilepattern("codetemplates/"+id).call();
                git.commit().setCommitter(committer).setMessage(codeTemplate.getName()+ " auto-committed by Innovar Healthcare Git Plugin on Server Id "+serverId).call();

            }
            PushToRemoteRepo();

        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void PushToRemoteRepo() throws Exception {
        // add remote repo:
        RemoteAddCommand remoteAddCommand = git.remoteAdd();
        remoteAddCommand.setName("origin");
        remoteAddCommand.setUri(new URIish(remoteGitRepoUrl));
        PushCommand pushCommand = git.push();
        pushCommand.setTransportConfigCallback( new TransportConfigCallback() {
            @Override
            public void configure( Transport transport ) {
                SshTransport sshTransport = (SshTransport)transport;
                sshTransport.setSshSessionFactory( sshSessionFactory );
            }
        } );

        // you can add more settings here if needed
        remoteAddCommand.call();
        pushCommand.call();
    }

    public void removeChannel(Channel channel, PersonIdent committer) {
        removeFile(channel.getId(), committer);
    }

    private void removeFile(String id, PersonIdent committer) {
        try {
            git.rm().addFilepattern(id).call();
            git.commit().setCommitter(committer).setMessage("").call();
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void updateCodeTemplate(CodeTemplate ct, PersonIdent committer) {
        updateFile(ct.getId(), ct, committer);
    }

    public void removeCodeTemplate(CodeTemplate ct, PersonIdent committer) {
        removeFile(ct.getId(), committer);        
    }
    
    public void close() {
        repo.close();
        channelRepo = null;
    }


    public List<String> getHistory(String fileName, String mode) throws Exception {
        List<String> lst = new ArrayList<>();

        if(repo.resolve(Constants.HEAD) != null) {
            Iterator<RevCommit> rcItr;
            if(Objects.equals(mode, "Channel")){
                rcItr = git.log().addPath("channels/" +fileName).call().iterator();
            }else{
                rcItr = git.log().addPath("codetemplates/" +fileName).call().iterator();
            }
            while(rcItr.hasNext()) {
                RevCommit rc = rcItr.next();
                lst.add(toRevisionInfo(rc));
            }
        }

        return lst;

    }

    
    public String getContent(String fileName, String revision, String mode) throws Exception {
        String content = null;
        if(StringUtils.isBlank(fileName) || StringUtils.isBlank(revision)) {
            return content;
        }

        try(TreeWalk tw = new TreeWalk(repo)) {
            ObjectId rcid = repo.resolve(revision);
            if(rcid != null) {
                RevCommit rc = repo.parseCommit(rcid);

                tw.setRecursive(true);
                if(Objects.equals(mode, "Channel")){
                    tw.setFilter(PathFilter.create("channels/"+fileName));
                }else{
                    tw.setFilter(PathFilter.create("codetemplates/"+fileName));
                }

                tw.addTree(rc.getTree());
                if(tw.next()) {
                    ObjectLoader objLoader = repo.open(tw.getObjectId(0));
                    ObjectStream stream = objLoader.openStream();
                    byte[] buf = new byte[1024];
                    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                    while(true) {
                        int len = stream.read(buf);
                        if(len <= 0) {
                            break;
                        }
                        byteOut.write(buf, 0, len);
                    }
                    stream.close();

                    content = new String(byteOut.toByteArray(), utf8);
                }
            }
        }
        catch(Exception e) {
            log.debug("commit " + revision + " not found for file " + fileName, e);
        }
        return content;
    }


    private String toRevisionInfo(RevCommit rc) {
        JSONObject ri = new JSONObject();

        PersonIdent committer = rc.getCommitterIdent();
        ri.put("CommitterEmail", committer.getEmailAddress());
        ri.put("CommitterName",committer.getName());
        ri.put("Hash",rc.getName());
        ri.put("Message", rc.getFullMessage());
        ri.put("Time",committer.getWhen().getTime());

        return ri.toString();
    }
}
