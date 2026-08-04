package com.fullsteam.controller;

import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

@ServerFilter(Filter.MATCH_ALL_PATTERN)
public class SecurityHeadersFilter {

    private static final String CONTENT_SECURITY_POLICY = String.join(";",
            "default-src 'self'",
            // 'unsafe-eval' is required by PixiJS (it generates shader/batch code via
            // new Function); without it Pixi aborts with an "unsafe-eval" error.
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdnjs.cloudflare.com",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "connect-src 'self'",
            "base-uri 'self'",
            "object-src 'none'",
            "frame-ancestors 'none'");

    @ResponseFilter
    public void addSecurityHeaders(MutableHttpResponse<?> response) {
        var headers = response.getHeaders();
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-Frame-Options", "DENY");
        headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        headers.add("Content-Security-Policy", CONTENT_SECURITY_POLICY);
    }
}
