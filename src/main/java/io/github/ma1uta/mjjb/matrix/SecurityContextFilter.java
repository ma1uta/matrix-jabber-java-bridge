package io.github.ma1uta.mjjb.matrix;

import io.github.ma1uta.matrix.ErrorResponse;
import io.github.ma1uta.matrix.impl.exception.MatrixException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Security filter.
 */
@Provider
public class SecurityContextFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityContextFilter.class);

    private final String accessToken;

    public SecurityContextFilter(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String accessToken = requestContext.getUriInfo().getQueryParameters().getFirst("access_token");
        if (accessToken == null || accessToken.trim().isEmpty()) {
            LOGGER.error("Missing access token.");
            throw new MatrixException("_UNAUTHORIZED", "", Response.Status.UNAUTHORIZED.getStatusCode());
        }

        if (!getAccessToken().equals(accessToken)) {
            LOGGER.error("Wrong access token.");
            throw new MatrixException(ErrorResponse.Code.M_FORBIDDEN, "", Response.Status.FORBIDDEN.getStatusCode());
        }
    }

    public String getAccessToken() {
        return accessToken;
    }
}
