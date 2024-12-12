/*
 * Copyright 2021 Kaur Palang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.innovarhealthcare.channelHistory.shared.interfaces;


import com.kaurpalang.mirth.annotationsplugin.annotation.MirthApiProvider;
import com.kaurpalang.mirth.annotationsplugin.type.ApiProviderType;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.Operation;
import com.mirth.connect.client.core.Permissions;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;
import com.mirth.connect.model.User;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

@Path("/innovarChannelHistory")
@Tag(name = "Innovar Channel History Plugin")
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@MirthApiProvider(type = ApiProviderType.SERVLET_INTERFACE)
public interface channelHistoryServletInterface extends BaseServletInterface {


    @GET
    @Path("/history")
    @ApiResponse(responseCode = "200", description = "Found the information",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "getHistory", display = "Get all revisions of a file", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.ASYNC, auditable = false)
    public List<String> getHistory(@Param("fileName") @Parameter(description = "The name of the file", required = true) @QueryParam("fileName") String fileName,
                                   @Param("mode") @Parameter(description = "channel or code template", required = true) @QueryParam("mode") String mode) throws ClientException;


    @GET
    @Path("/content")
    @ApiResponse(responseCode = "200", description = "Found the information",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "getContent", display = "Get the content of the file at a specific revision", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String getContent(@Param("fileName") @Parameter(description = "The name of the file", required = true) @QueryParam("fileName") String fileName,
                             @Param("revision") @Parameter(description = "The value of revision", required = true) @QueryParam("revision") String revision,
                             @Param("mode") @Parameter(description = "channel or code template", required = true) @QueryParam("mode") String mode) throws ClientException;

    @POST
    @Path("/updateSetting")
    @ApiResponse(responseCode = "200", description = "update repo setting",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "updateSetting", display = "update git repo setting", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String updateSetting() throws ClientException;

    @POST
    @Path("/validateSetting")
    @ApiResponse(responseCode = "200", description = "validate git repo setting",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "validateSetting", display = "validate git repo setting", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String validateSetting(
            @Param("properties") @RequestBody(description = "description", content = {
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = Properties.class), examples = {
                            @ExampleObject(name = "propertiesObject", ref = "../apiexamples/properties_xml")}),
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Properties.class), examples = {
                            @ExampleObject(name = "propertiesObject", ref = "../apiexamples/properties_json")})}) Properties properties
    ) throws ClientException;

    @POST
    @Path("/commitAndPushChannel")
    @ApiResponse(responseCode = "200", description = "commit and push channel",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "commitAndPushChannel", display = "commit and push channel", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String commitAndPushChannel(
            @Param("channelId") @Parameter(description = "channel id", required = true) @QueryParam("channelId") String channelId,
            @Param("message") @Parameter(description = "message", required = true) @QueryParam("message") String message,
            @Param("userId") @Parameter(description = "user id", required = true) @QueryParam("userId") String userId) throws ClientException;

    @GET
    @Path("/channel_on_repo")
    @ApiResponse(responseCode = "200", description = "Load channels on repo",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "loadChannelOnRepo", display = "load the channels on repo", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public List<String> loadChannelOnRepo() throws ClientException;

    @GET
    @Path("/code_template_on_repo")
    @ApiResponse(responseCode = "200", description = "Load code templates on repo",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "loadChannelOnRepo", display = "load the code templates on repo", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public List<String> loadCodeTemplateOnRepo() throws ClientException;

    @POST
    @Path("/commitAndPushCodeTemplate")
    @ApiResponse(responseCode = "200", description = "commit and push channel",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "commitAndPushCodeTemplate", display = "commit and push code template", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String commitAndPushCodeTemplate(
            @Param("codeTemplateId") @Parameter(description = "code template id", required = true) @QueryParam("codeTemplateId") String codeTemplateId,
            @Param("message") @Parameter(description = "message", required = true) @QueryParam("message") String message,
            @Param("userId") @Parameter(description = "user id", required = true) @QueryParam("userId") String userId) throws ClientException;

}
