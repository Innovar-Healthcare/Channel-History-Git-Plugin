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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.util.List;

@Path("/innovarChannelHistory")
@Tag(name = "Innovar Channel History Plugin")
@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
@MirthApiProvider(type = ApiProviderType.SERVLET_INTERFACE)
public interface channelHistoryServletInterface extends BaseServletInterface {


    @GET
    @Path("/history")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
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
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
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
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ApiResponse(responseCode = "200", description = "update repo setting",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "updateSetting", display = "update git repo setting", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String updateSetting() throws Exception;

    @GET
    @Path("/validateSetting")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @ApiResponse(responseCode = "200", description = "validate git repo setting",
            content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
                    @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))
            })
    @MirthOperation(name = "validateSetting", display = "validate git repo setting", permission = Permissions.CHANNELS_VIEW, type = Operation.ExecuteType.SYNC, auditable = false)
    public String validateSetting() throws Exception;


}
