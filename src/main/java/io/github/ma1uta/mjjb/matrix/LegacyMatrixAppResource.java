/*
 * Copyright sablintolya@gmai.com
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

package io.github.ma1uta.mjjb.matrix;

import io.github.ma1uta.matrix.application.model.TransactionRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

/**
 * Legacy Application API.
 * <p/>
 * Delegates all requests to the {@link MatrixAppResource}.
 */
@Provider
@Path("")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LegacyMatrixAppResource {

    private final MatrixAppResource resource;

    public LegacyMatrixAppResource(MatrixAppResource resource) {
        this.resource = resource;
    }

    /**
     * See {@link
     * io.github.ma1uta.matrix.application.api.ApplicationApi#transaction(String, TransactionRequest, UriInfo, HttpHeaders, AsyncResponse)}.
     *
     * @param txnId         Required. The transaction ID for this set of events. Homeservers generate these IDs and they are used to
     *                      ensure idempotency of requests.
     * @param request       JSON body request.
     * @param uriInfo       Information about the request.
     * @param httpHeaders   Http headers.
     * @param asyncResponse Asynchronous response.
     */
    @PUT
    @Path("/transactions/{txnId}")
    public void transaction(
        @PathParam("txnId") String txnId,
        TransactionRequest request,

        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @Suspended AsyncResponse asyncResponse
    ) {
        this.resource.transaction(txnId, request, uriInfo, httpHeaders, asyncResponse);
    }

    /**
     * See {@link io.github.ma1uta.matrix.application.api.ApplicationApi#rooms(String, UriInfo, HttpHeaders, AsyncResponse)}.
     *
     * @param roomAlias     Required. The room alias being queried.
     * @param uriInfo       Information about the request.
     * @param httpHeaders   Http headers.
     * @param asyncResponse Asynchronous response.
     */
    @GET
    @Path("/rooms/{roomAlias}")
    public void rooms(
        @PathParam("roomAlias") String roomAlias,

        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @Suspended AsyncResponse asyncResponse
    ) {
        this.resource.rooms(roomAlias, uriInfo, httpHeaders, asyncResponse);
    }

    /**
     * See {@link io.github.ma1uta.matrix.application.api.ApplicationApi#users(String, UriInfo, HttpHeaders, AsyncResponse)}.
     *
     * @param userId        Required. The user ID being queried.
     * @param uriInfo       Information about the request.
     * @param httpHeaders   Http headers.
     * @param asyncResponse Asynchronous response.
     */
    @GET
    @Path("/users/{userId}")
    public void users(
        @PathParam("userId") String userId,

        @Context UriInfo uriInfo,
        @Context HttpHeaders httpHeaders,
        @Suspended AsyncResponse asyncResponse
    ) {
        this.resource.users(userId, uriInfo, httpHeaders, asyncResponse);
    }
}
