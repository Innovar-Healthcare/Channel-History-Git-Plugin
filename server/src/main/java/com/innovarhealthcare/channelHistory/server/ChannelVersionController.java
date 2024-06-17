/*
 * This Source Code Form is subject to the terms of the
 * Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.innovarhealthcare.channelHistory.server;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthServerClass;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.model.ServerEventContext;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.plugins.ChannelPlugin;
import com.mirth.connect.plugins.ServicePlugin;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;
import org.apache.log4j.Logger;

import java.util.Properties;

/**
 * @author Jim(Zi Min) Weng
 * @create 2024-04-17 9:25 AM
 */
@MirthServerClass
public class ChannelVersionController extends VersionControllerBase implements ChannelPlugin{

    private static Logger log = Logger.getLogger(ChannelVersionController.class);

    private GitChannelRepository repo;
    
    private ObjectXMLSerializer serializer;

    @Override
    public String getPluginPointName() {
        return VersionControlConstants.PLUGIN_POINTNAME;
    }

    @Override
    public void start() {
        ExtensionController ec = ControllerFactory.getFactory().createExtensionController();
        Properties properties = null;
        try {
            properties = ec.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
        } catch (ControllerException e) {
            throw new RuntimeException(e);
        }
        boolean isEnabled = Boolean.parseBoolean(properties.getProperty(VersionControlConstants.VERSION_HISTORY_ENABLE));
        userController = ControllerFactory.getFactory().createUserController();
        serializer = ObjectXMLSerializer.getInstance();
        if(isEnabled){
            if(repo == null){

                String appDataDir = Donkey.getInstance().getConfiguration().getAppData();

                GitChannelRepository.init(appDataDir, serializer, properties);
                repo = GitChannelRepository.getInstance();
                try {
                    repo.updateSetting(properties);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    @Override
    public void stop() {
        repo.close();
    }

    @Override
    public void save(Channel channel, ServerEventContext sec) {
        repo = GitChannelRepository.getInstance();
        if(repo != null){
            repo.updateChannel(channel, getCommitter(sec));
        }

    }

    @Override
    public void remove(Channel channel, ServerEventContext sec) {
    }

    @Override
    public void deploy(Channel channel, ServerEventContext arg1) {
    }

    @Override
    public void deploy(ServerEventContext sec) {
    }

    @Override
    public void undeploy(ServerEventContext sec) {
    }

    @Override
    public void undeploy(String id, ServerEventContext sec) {
    }


}
