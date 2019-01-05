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
import io.github.ma1uta.mjjb.Transport;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(MatrixAppResource.class);

    private final Jdbi jdbi;
    private final Transport transport;

    public MatrixAppResource(Jdbi jdbi, Transport transport) {
        this.jdbi = jdbi;
        this.transport = transport;
    }

    public Transport getTransport() {
        return transport;
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
        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.debug("Create new user {}", userId);
                getTransport().createUser(userId);
                asyncResponse.resume(Response.ok().build());
            } catch (Exception e) {
                LOGGER.error("Failed create new user.", e);
                asyncResponse.resume(e);
            }
        });
    }
}
