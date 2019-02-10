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

import io.github.ma1uta.matrix.EmptyResponse;
import io.github.ma1uta.matrix.ErrorResponse;
import io.github.ma1uta.matrix.Id;
import io.github.ma1uta.matrix.application.api.ApplicationApi;
import io.github.ma1uta.matrix.application.model.TransactionRequest;
import io.github.ma1uta.matrix.client.MatrixClient;
import io.github.ma1uta.matrix.client.model.account.RegisterRequest;
import io.github.ma1uta.matrix.impl.exception.MatrixException;
import io.github.ma1uta.mjjb.RouterFactory;
import io.github.ma1uta.mjjb.db.TransactionDao;
import io.github.ma1uta.mjjb.db.UserDao;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
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
    private final RouterFactory routerFactory;
    private final MatrixClient matrixClient;

    public MatrixAppResource(Jdbi jdbi, RouterFactory routerFactory, MatrixClient matrixClient) {
        this.jdbi = jdbi;
        this.routerFactory = routerFactory;
        this.matrixClient = matrixClient;
    }

    public RouterFactory getRouterFactory() {
        return routerFactory;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public MatrixClient getMatrixClient() {
        return matrixClient;
    }

    @Override
    public void transaction(String txnId, TransactionRequest request, UriInfo uriInfo, HttpHeaders httpHeaders,
                            @Suspended AsyncResponse asyncResponse) {
        CompletableFuture.runAsync(() -> {
            try {
                getJdbi().useTransaction(h -> {
                    TransactionDao dao = h.attach(TransactionDao.class);
                    if (dao.exist(txnId) == 0) {
                        dao.start(txnId, LocalDateTime.now());

                        request.getEvents().parallelStream().forEach(event -> {
                            try {
                                getRouterFactory().process(event);
                            } catch (Exception e) {
                                LOGGER.error("Failed process event.", e);
                            }
                        });

                        dao.finish(txnId, LocalDateTime.now());
                    }
                });
            } catch (Exception e) {
                LOGGER.error(String.format("Failed process transaction %s", txnId), e);
            }
            asyncResponse.resume(Response.ok(new EmptyResponse()).build());
        });
    }

    @Override
    public void rooms(Id roomAlias, UriInfo uriInfo, HttpHeaders httpHeaders, @Suspended AsyncResponse asyncResponse) {
        asyncResponse.resume(Response.ok(new EmptyResponse()).build());
    }

    @Override
    public void users(Id userId, UriInfo uriInfo, HttpHeaders httpHeaders, @Suspended AsyncResponse asyncResponse) {
        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.debug("Create new user {}", userId);
                createUser(userId);
                asyncResponse.resume(Response.ok(new EmptyResponse()).build());
            } catch (Exception e) {
                LOGGER.error("Failed create new user.", e);
                asyncResponse.resume(e);
            }
        });
    }

    private void createUser(Id userId) {
        getJdbi().useTransaction(h -> {
            UserDao userDao = h.attach(UserDao.class);

            if (userDao.exist(userId.getLocalpart()) > 0) {
                return;
            }

            RegisterRequest request = new RegisterRequest();
            request.setUsername(userId.getLocalpart());
            request.setInhibitLogin(false);

            getMatrixClient().account().register(request).whenCompleteAsync((resp, exc) -> {
                if (exc != null) {
                    LOGGER.error(String.format("Failed create new user: %s", userId), exc);
                    throw new MatrixException(ErrorResponse.Code.M_UNKNOWN, exc.getMessage());
                }
                userDao.create(resp.getUserId().getLocalpart());
            }).join();
        });
    }
}
