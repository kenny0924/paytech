package com.paytech.gateway.web;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

/** Client Owner 响应 Header 固定白名单；Location 是仅 201 可用的受限例外。 */
public final class ClientApiResponseHeaderPolicy {
    private static final Set<String> ALLOWED = Set.of("content-type", "content-length", "content-encoding",
        "content-language", "content-disposition", "etag", "last-modified", "retry-after");

    public void validateAndRewrite(HttpStatusCode status, HttpHeaders headers) {
        boolean created = status != null && status.value() == 201;
        if (created) {
            var locations = headers.getOrEmpty(HttpHeaders.LOCATION);
            if (locations.size() != 1 || !validLocation(locations.getFirst())) {
                throw new InvalidUpstreamResponseException();
            }
        }
        new HashSet<>(headers.headerNames()).forEach(name -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            // Owner Problem 校验仍需看到它；真正外发值由终端 Writer 重建。
            if (!ALLOWED.contains(normalized) && !(created && "location".equals(normalized))
                && !GatewayProtocol.REQUEST_ID.equalsIgnoreCase(name)) {
                headers.remove(name);
            }
        });
    }

    private boolean validLocation(String value) {
        if (!value.startsWith("/api/v1/") || value.chars().anyMatch(c -> c <= 32 || c >= 127 || c == '\\')) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.getScheme() == null && uri.getRawAuthority() == null && uri.getRawQuery() == null
                && uri.getRawFragment() == null && value.equals(uri.getRawPath());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
