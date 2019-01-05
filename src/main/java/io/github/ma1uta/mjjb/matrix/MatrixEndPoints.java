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

import io.github.ma1uta.mjjb.Loggers;
import io.github.ma1uta.mjjb.Transport;
import io.github.ma1uta.mjjb.config.MatrixConfig;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * All Matrix endpoints.
 */
@ApplicationPath("")
public class MatrixEndPoints extends Application {

    private final Set<Object> resources = new HashSet<>();

    public MatrixEndPoints(Jdbi jdbi, MatrixConfig config, Transport transport) {
        MatrixAppResource appResource = new MatrixAppResource(jdbi, transport);
        resources.add(appResource);
        resources.add(new LegacyMatrixAppResource(appResource));
        resources.add(new SecurityContextFilter(config.getAsToken()));
        resources.add(new MatrixExceptionHandler());
        if (LoggerFactory.getLogger(Loggers.REQUEST_LOGGER).isDebugEnabled()) {
            resources.add(new LoggingFilter());
        }
    }

    @Override
    public Set<Object> getSingletons() {
        return this.resources;
    }
}
