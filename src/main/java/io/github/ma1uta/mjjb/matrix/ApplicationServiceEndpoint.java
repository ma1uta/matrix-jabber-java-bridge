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

import io.github.ma1uta.jeon.exception.MatrixException;
import io.github.ma1uta.matrix.EmptyResponse;
import io.github.ma1uta.matrix.ErrorResponse;
import io.github.ma1uta.matrix.application.api.ApplicationApi;
import io.github.ma1uta.matrix.application.model.TransactionRequest;
import io.github.ma1uta.mjjb.dao.MatrixTransactionDao;
import io.github.ma1uta.mjjb.transport.TransportPool;
import org.apache.commons.lang3.StringUtils;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

/**
 * AS interface.
 */
public class ApplicationServiceEndpoint implements ApplicationApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationServiceEndpoint.class);

    private final TransportPool pool;
    private final Jdbi jdbi;
    private final String hsToken;

    public ApplicationServiceEndpoint(TransportPool pool, Jdbi jdbi, String hsToken) {
        this.pool = pool;
        this.jdbi = jdbi;
        this.hsToken = hsToken;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public TransportPool getPool() {
        return pool;
    }

    public String getHsToken() {
        return hsToken;
    }

    @Override
    public EmptyResponse transaction(String txnId, TransactionRequest request, HttpServletRequest servletRequest,
                                     HttpServletResponse servletResponse) {
        validateAsToken(servletRequest);
        getJdbi().useTransaction(handle -> {
            MatrixTransactionDao dao = handle.attach(MatrixTransactionDao.class);
            if (dao.exist(txnId) == 0) {
                request.getEvents().parallelStream().forEach(event -> {
                    try {
                        getPool().event(event);
                    } catch (Exception e) {
                        LOGGER.error("Cannot process event", e);
                    }
                });
                dao.save(txnId, LocalDateTime.now());
            }
        });
        return new EmptyResponse();
    }

    @Override
    public EmptyResponse rooms(String roomAlias, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        validateAsToken(servletRequest);
        getPool().createTransport(roomAlias);
        return new EmptyResponse();
    }

    @Override
    public EmptyResponse users(String userId, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        validateAsToken(servletRequest);
        throw new MatrixException(ErrorResponse.Code.M_FORBIDDEN, "Not supported.", Response.Status.FORBIDDEN.getStatusCode());
    }

    protected void validateAsToken(HttpServletRequest servletRequest) {
        String accessToken = servletRequest.getParameter("access_token");
        if (StringUtils.isBlank(accessToken)) {
            throw new MatrixException("_UNAUTHORIZED", "", HttpServletResponse.SC_UNAUTHORIZED);
        }

        if (!getHsToken().equals(accessToken)) {
            throw new MatrixException(ErrorResponse.Code.M_FORBIDDEN, "", HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
