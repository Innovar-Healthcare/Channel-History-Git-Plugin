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


import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Properties;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthApiProvider;
import com.kaurpalang.mirth.annotationsplugin.type.ApiProviderType;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.Operation;
import com.mirth.connect.client.core.Permissions;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.glassfish.jersey.media.multipart.FormDataParam;

//@formatter:off
@Path("/plugins/version-history")
@Tag(name = "Version History Plugin")
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@MirthApiProvider(type = ApiProviderType.SERVLET_INTERFACE)
public interface VersionHistoryServletInterface extends BaseServletInterface {
    @GET
    @Path("/history")
    @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved all commit revisions of the file",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getHistory",
            display = "Get all commit revisions of a file",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.ASYNC,
            auditable = false
    )
    public String getHistory(
            @Param("fileName")
            @Parameter(description = "The file name (UUID) of the channel or code template", required = true)
            @QueryParam("fileName") String fileName,

            @Param("mode")
            @Parameter(description = "The type of item: 'channel' or 'codetemplate'", required = true)
            @QueryParam("mode") String mode
    ) throws ClientException;

    @GET
    @Path("/content")
    @ApiResponse(
            responseCode = "200",
            description = "Retrieved entity content from repository at specific revision",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(
            name = "getContentAtRevision",
            display = "Get entity content from repository at specific revision",
            permission = Permissions.CHANNELS_VIEW,
            type = Operation.ExecuteType.SYNC,
            auditable = false
    )
    public String getContentAtRevision(
            @Param("id")
            @Parameter(description = "The entity ID (channel, library, or code template)", required = true)
            @QueryParam("id") String id,

            @Param("revision")
            @Parameter(description = "The Git revision/commit hash or ref (e.g., 'HEAD', commit SHA)", required = true)
            @QueryParam("revision") String revision,

            @Param("mode")
            @Parameter(description = "The entity type: 'channel', 'library', or 'codetemplate'", required = true)
            @QueryParam("mode") String mode
    ) throws ClientException;

    @POST
    @Path("/validateSetting")
    @ApiResponse(responseCode = "200", description = "validate git repo setting", content = {@Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)), @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))})
    @MirthOperation(name = "validateSetting", display = "validate git repo setting", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String validateSetting(@Param("properties") @RequestBody(description = "description", content = {@Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = Properties.class), examples = {@ExampleObject(name = "propertiesObject", ref = "../apiexamples/properties_xml")}), @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Properties.class), examples = {@ExampleObject(name = "propertiesObject", ref = "../apiexamples/properties_json")})}) Properties properties) throws ClientException;

    @POST
    @Path("/commitAndPushChannel")
    @ApiResponse(responseCode = "200", description = "commit and push channel", content = {@Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)), @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))})
    @MirthOperation(name = "commitAndPushChannel", display = "commit and push channel", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String commitAndPushChannel(@Param("channel") @RequestBody(description = "The Channel object to create.", required = true, content = {@Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = Channel.class), examples = {@ExampleObject(name = "channel", ref = "../apiexamples/channel_xml")}), @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Channel.class), examples = {@ExampleObject(name = "channel", ref = "../apiexamples/channel_json")})}) Channel channel, @Param("message") @Parameter(description = "message", required = true) @QueryParam("message") String message, @Param("userId") @Parameter(description = "user id", required = true) @QueryParam("userId") String userId) throws ClientException;

    @GET
    @Path("/channel_on_repo")
    @ApiResponse(responseCode = "200", description = "Load channels on repo", content = {@Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)), @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))})
    @MirthOperation(name = "loadChannelsMetadata", display = "load the channels on repo", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String loadChannelsMetadata() throws ClientException;

    @GET
    @Path("/code_template_on_repo")
    @ApiResponse(responseCode = "200", description = "Load code templates on repo", content = {@Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)), @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))})
    @MirthOperation(name = "loadCodeTemplatesMetadata", display = "load the code templates on repo", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String loadCodeTemplatesMetadata() throws ClientException;

    @POST
    @Path("/commitAndPushCodeTemplate")
    @ApiResponse(responseCode = "200", description = "commit and push channel", content = {@Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)), @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))})
    @MirthOperation(name = "commitAndPushCodeTemplate", display = "commit and push code template", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String commitAndPushCodeTemplate(@Param("codeTemplateId") @Parameter(description = "code template id", required = true) @QueryParam("codeTemplateId") String codeTemplateId, @Param("message") @Parameter(description = "message", required = true) @QueryParam("message") String message, @Param("userId") @Parameter(description = "user id", required = true) @QueryParam("userId") String userId) throws ClientException;

    @POST
    @Path("/saveLibraries")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ApiResponse(
            responseCode = "200",
            description = "Save code template libraries to repository",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            }
    )
    @MirthOperation(name = "saveLibraries", display = "Save libraries to repository", permission = Permissions.CODE_TEMPLATES_MANAGE)
    public String saveLibraries(
            @Param("libraries")
            @Parameter(
                    description = "The list of code template libraries to save to repository. Each library will be exported as an XML file in the Libraries folder.",
                    schema = @Schema(
                            description = "List of CodeTemplateLibrary objects containing library structure and template references"
                    ),
                    required = true
            )
            @FormDataParam("libraries")  List<CodeTemplateLibrary> libraries,

            @Param("message")
            @Parameter(
                    description = "message",
                    required = true)
            @QueryParam("message") String message,

            @Param("userId")
            @Parameter(
                    description = "user id",
                    required = true)
            @QueryParam("userId") String userId
            ) throws ClientException;
}

//@formatter:on
