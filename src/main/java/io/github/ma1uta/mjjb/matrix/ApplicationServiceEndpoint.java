/*
 * Copyright sablintolya@gmail.com
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

import io.github.ma1uta.matrix.EmptyResponse;
import io.github.ma1uta.matrix.ErrorResponse;
import io.github.ma1uta.matrix.application.api.ApplicationApi;
import io.github.ma1uta.matrix.application.model.TransactionRequest;
import io.github.ma1uta.matrix.impl.exception.MatrixException;
import io.github.ma1uta.mjjb.dao.MatrixTransactionDao;
import io.github.ma1uta.mjjb.transport.TransportPool;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * AS interface.
 */
public class ApplicationServiceEndpoint implements ApplicationApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationServiceEndpoint.class);

    private final TransportPool pool;
    private final Jdbi jdbi;
    private final Executor executor = Executors.newCachedThreadPool();

    public ApplicationServiceEndpoint(TransportPool pool, Jdbi jdbi) {
        this.pool = pool;
        this.jdbi = jdbi;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public TransportPool getPool() {
        return pool;
    }

    @Override
    public void transaction(String txnId, TransactionRequest request, @Context UriInfo uriInfo, @Context HttpHeaders httpHeaders,
                            @Suspended AsyncResponse asyncResponse) {
        executor.execute(() -> {
            try {
                getJdbi().useTransaction(handle -> {
                    MatrixTransactionDao dao = handle.attach(MatrixTransactionDao.class);
                    LOGGER.debug("Transaction: {}", txnId);
                    if (dao.exist(txnId) == 0) {
                        try {
                            request.getEvents().parallelStream().forEach(event -> {
                                try {
                                    getPool().event(event, handle);
                                } catch (Exception e) {
                                    LOGGER.error("Cannot process event", e);
                                }
                            });
                        } catch (Exception e) {
                            LOGGER.error("Failed process events", e);
                        } finally {
                            dao.save(txnId, LocalDateTime.now());
                        }
                    } else {
                        LOGGER.warn("");
                    }
                });
                asyncResponse.resume(new EmptyResponse());
            } catch (Exception e) {
                LOGGER.error("Unexpected exception", e);
                asyncResponse.resume(e);
            }
        });
    }

    @Override
    public void rooms(String roomAlias, @Context UriInfo uriInfo, @Context HttpHeaders httpHeaders,
                      @Suspended AsyncResponse asyncResponse) {
        executor.execute(() -> {
            getPool().createTransport(roomAlias);
            asyncResponse.resume(new EmptyResponse());
        });
    }

    @Override
    public void users(String userId, @Context UriInfo uriInfo, @Context HttpHeaders httpHeaders, @Suspended AsyncResponse asyncResponse) {
        asyncResponse
            .resume(new MatrixException(ErrorResponse.Code.M_FORBIDDEN, "Not supported.", Response.Status.FORBIDDEN.getStatusCode()));
    }
}
