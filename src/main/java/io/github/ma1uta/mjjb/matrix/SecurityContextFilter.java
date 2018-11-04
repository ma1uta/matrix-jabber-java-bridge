package io.github.ma1uta.mjjb.matrix;

import io.github.ma1uta.matrix.ErrorResponse;
import io.github.ma1uta.matrix.impl.exception.MatrixException;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;

public class SecurityContextFilter implements ContainerRequestFilter {

    private final String accessToken;

    public SecurityContextFilter(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String accessToken = requestContext.getUriInfo().getQueryParameters().getFirst("access_token");
        if (StringUtils.isBlank(accessToken)) {
            throw new MatrixException("_UNAUTHORIZED", "", Response.Status.UNAUTHORIZED.getStatusCode());
        }

        if (!getAccessToken().equals(accessToken)) {
            throw new MatrixException(ErrorResponse.Code.M_FORBIDDEN, "", Response.Status.FORBIDDEN.getStatusCode());
        }
    }

    public String getAccessToken() {
        return accessToken;
    }
}
