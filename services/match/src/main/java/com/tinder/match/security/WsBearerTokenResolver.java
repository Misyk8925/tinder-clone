package com.tinder.match.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.StringUtils;

/**
 * Allows Bearer tokens via query param for WebSocket handshake only.
 */
public class WsBearerTokenResolver implements BearerTokenResolver {

    private static final String WS_PATH_PREFIX = "/ws";
    private static final String QUERY_PARAM = "access_token";

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String token = headerResolver.resolve(request);
        if (StringUtils.hasText(token)) {
            return token;
        }

        String path = request.getRequestURI();
        if (path != null && path.startsWith(WS_PATH_PREFIX)) {
            String queryToken = request.getParameter(QUERY_PARAM);
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }
        }

        return null;
    }
}
