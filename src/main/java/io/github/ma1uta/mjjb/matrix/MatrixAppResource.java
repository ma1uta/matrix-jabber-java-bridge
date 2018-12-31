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

import io.github.ma1uta.matrix.application.api.ApplicationApi;
import io.github.ma1uta.matrix.application.model.TransactionRequest;
import org.jdbi.v3.core.Jdbi;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

/**
 * Application API endpoint.
 */
@Provider
public class MatrixAppResource implements ApplicationApi {

    private final Jdbi jdbi;

    public MatrixAppResource(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public void transaction(String txnId, TransactionRequest request, UriInfo uriInfo, HttpHeaders httpHeaders,
                            AsyncResponse asyncResponse) {
        asyncResponse.resume(Response.ok().build());
    }

    @Override
    public void rooms(String roomAlias, UriInfo uriInfo, HttpHeaders httpHeaders, @Suspended AsyncResponse asyncResponse) {
        asyncResponse.resume(Response.ok().build());
    }

    @Override
    public void users(String userId, UriInfo uriInfo, HttpHeaders httpHeaders, @Suspended AsyncResponse asyncResponse) {
        asyncResponse.resume(Response.ok().build());
    }
}
