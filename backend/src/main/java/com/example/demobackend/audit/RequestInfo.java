package com.example.demobackend.audit;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * HTTP request details of the audited operation; all null when not running inside a request.
 *
 * @param route the matched mapping pattern, e.g. {@code /api/documents/{id}}
 * @param path  the concrete request path, e.g. {@code /api/documents/3f2a...}; identifies the
 *              target even when a denied or failed request changed nothing
 */
record RequestInfo(String method, String route, String path, String clientIp, String requestId) {

    static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final RequestInfo NONE = new RequestInfo(null, null, null, null, null);

    static RequestInfo current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return NONE;
        }
        HttpServletRequest request = attributes.getRequest();
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return new RequestInfo(request.getMethod(), route != null ? route.toString() : null, request.getRequestURI(), request.getRemoteAddr(),
                requestId != null ? requestId : UUID.randomUUID().toString());
    }
}
