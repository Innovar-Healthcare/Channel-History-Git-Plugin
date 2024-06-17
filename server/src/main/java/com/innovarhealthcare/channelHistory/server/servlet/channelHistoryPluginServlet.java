/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.innovarhealthcare.channelHistory.server.servlet;

/**
 * @author Jim(Zi Min) Weng
 * @create 2023-10-20 3:31 PM
 */
import com.innovarhealthcare.channelHistory.server.GitChannelRepository;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.interfaces.channelHistoryServletInterface;
import com.kaurpalang.mirth.annotationsplugin.annotation.MirthApiProvider;
import com.kaurpalang.mirth.annotationsplugin.type.ApiProviderType;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@MirthApiProvider(type = ApiProviderType.SERVER_CLASS)
public class channelHistoryPluginServlet extends MirthServlet implements channelHistoryServletInterface {
    private static Logger log = Logger.getLogger(channelHistoryPluginServlet.class);

    private GitChannelRepository repo;
    private ObjectXMLSerializer serializer;

    public channelHistoryPluginServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, VersionControlConstants.PLUGIN_POINTNAME);

    }

    @Override
    public List<String> getHistory(String fileName, String mode) throws ClientException {
        try {
            this.repo = GitChannelRepository.getInstance();
            if(null == this.repo){
                return new ArrayList<>();
            }else {
                return repo.getHistory(fileName, mode);
            }
        }
        catch(Exception e) {
            log.warn("failed to get the history of file " + fileName, e);
            throw new ClientException(e);
        }
    }

    @Override
    public String getContent(String fileName, String revision, String mode) throws ClientException {
        try {
            this.repo = GitChannelRepository.getInstance();
            return repo.getContent(fileName, revision, mode);
        }
        catch(Exception e) {
            log.warn("failed to get the content of file " + fileName + " at revision " + revision, e);
            throw new ClientException(e);
        }
    }
    @Override
    public String updateSetting(){
        JSONObject res = new JSONObject();
        try{
            ExtensionController ec = ControllerFactory.getFactory().createExtensionController();
            Properties properties = ec.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
            boolean isEnabled = Boolean.parseBoolean(properties.getProperty(VersionControlConstants.VERSION_HISTORY_ENABLE));

            if(isEnabled){
                if(repo == null){
                    serializer = ObjectXMLSerializer.getInstance();
                    String appDataDir = Donkey.getInstance().getConfiguration().getAppData();
                    GitChannelRepository.init(appDataDir, serializer, properties);
                    this.repo = GitChannelRepository.getInstance();
                }
                repo.updateSetting(properties);
            }else{
                this.repo = GitChannelRepository.getInstance();
                repo.close();
                res.put("status","success");
            }


        } catch (ControllerException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        return res.toString();
    }
    @Override
    public String validateSetting() throws Exception {
        ExtensionController ec = ControllerFactory.getFactory().createExtensionController();
        Properties properties = ec.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
        boolean isEnabled = Boolean.parseBoolean(properties.getProperty(VersionControlConstants.VERSION_HISTORY_ENABLE));
        if(repo == null){
            serializer = ObjectXMLSerializer.getInstance();
            String appDataDir = Donkey.getInstance().getConfiguration().getAppData();
            GitChannelRepository.init(appDataDir, serializer, properties);
            this.repo = GitChannelRepository.getInstance();
        }
        return repo.validateSetting(properties);
    }
}
