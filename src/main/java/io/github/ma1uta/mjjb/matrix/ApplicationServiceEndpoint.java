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
import io.github.ma1uta.mjjb.transaction.MatrixTransaction;
import io.github.ma1uta.mjjb.transport.TransportPool;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDateTime;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

/**
 * AS interface.
 */
public class ApplicationServiceEndpoint implements ApplicationApi {

    private final TransportPool pool;
    private final Jdbi jdbi;

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
    public EmptyResponse transaction(String txnId, TransactionRequest request, HttpServletRequest servletRequest,
                                     HttpServletResponse servletResponse) {
        getJdbi().useTransaction(handle -> {
            MatrixTransactionDao dao = handle.attach(MatrixTransactionDao.class);
            if (dao.exist(txnId) == 0) {
                request.getEvents().forEach(event -> getPool().event(event));

                MatrixTransaction transaction = new MatrixTransaction();
                transaction.setId(txnId);
                transaction.setProcessed(LocalDateTime.now());

                dao.save(txnId, LocalDateTime.now());
            }
        });
        return new EmptyResponse();
    }

    @Override
    public EmptyResponse rooms(String roomAlias, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        getPool().createTransport(roomAlias);
        return new EmptyResponse();
    }

    @Override
    public EmptyResponse users(String userId, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        throw new MatrixException(ErrorResponse.Code.M_FORBIDDEN, "Not supported.", Response.Status.FORBIDDEN.getStatusCode());
    }
}
