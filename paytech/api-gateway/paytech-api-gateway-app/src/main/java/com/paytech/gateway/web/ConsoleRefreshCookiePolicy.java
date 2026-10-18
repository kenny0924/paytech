package com.paytech.gateway.web;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.RefreshCookieResponsePolicy;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

/** 将 BFF Refresh Cookie 作为不可信协议解析、校验并重建为固定外部格式。 */
@Component
public final class ConsoleRefreshCookiePolicy {
    private static final CookieProfile CLIENT = new CookieProfile("__Secure-client-refresh", "/api/client/auth/v1",
        "Lax", 7 * 24 * 60 * 60, true);
    private final CookieProfile operations;

    public ConsoleRefreshCookiePolicy(OperationsRoutingProperties properties) {
        boolean secure = !properties.testHttpEnabled();
        operations = new CookieProfile(secure ? GatewayProtocol.REFRESH_COOKIE : GatewayProtocol.TEST_REFRESH_COOKIE,
            GatewayPaths.OPERATIONS_AUTH_PREFIX, "Strict", 8 * 60 * 60, secure);
    }

    public String cookieName(GatewayRouteChannel channel) {
        CookieProfile profile = profile(channel);
        return profile == null ? null : profile.name();
    }

    private CookieProfile profile(GatewayRouteChannel channel) {
        return switch (channel) {
            case OPERATIONS -> operations;
            case CLIENT -> CLIENT;
            default -> null;
        };
    }

    public void validateAndRewrite(HttpHeaders headers, RefreshCookieResponsePolicy responsePolicy,
        GatewayRouteChannel channel) {
        List<String> cookies = headers.getOrEmpty(HttpHeaders.SET_COOKIE);
        if (cookies.isEmpty()) {
            return;
        }
        CookieProfile profile = profile(channel);
        if (profile == null || responsePolicy == RefreshCookieResponsePolicy.NONE || cookies.size() != 1) {
            throw new InvalidUpstreamResponseException();
        }
        headers.set(HttpHeaders.SET_COOKIE, normalize(cookies.getFirst(), responsePolicy, profile));
    }

    private String normalize(String cookie, RefreshCookieResponsePolicy responsePolicy, CookieProfile profile) {
        String[] segments = cookie.split(";", -1);
        CookiePair cookiePair = parseCookiePair(segments[0].trim(), profile);
        ParsedAttributes attributes = parseAttributes(segments, profile);
        validateCookie(cookiePair, attributes.maxAge(), responsePolicy, profile);
        return profile.name() + '=' + cookiePair.value() + "; Path=" + profile.path() + "; Max-Age="
            + attributes.maxAge() + (profile.secureOnly() ? "; Secure" : "") + "; HttpOnly; SameSite=" + profile.sameSite();
    }

    private CookiePair parseCookiePair(String segment, CookieProfile profile) {
        int separator = segment.indexOf('=');
        if (separator < 0 || !profile.name().equals(segment.substring(0, separator))) {
            throw new InvalidUpstreamResponseException();
        }
        String value = segment.substring(separator + 1);
        if (!isValidOpaqueValue(value)) {
            throw new InvalidUpstreamResponseException();
        }
        return new CookiePair(value, isEmptyValue(value));
    }

    private ParsedAttributes parseAttributes(String[] segments, CookieProfile profile) {
        Set<String> requiredAttributes = profile.secureOnly() ? Set.of("path", "max-age", "secure", "httponly", "samesite")
            : Set.of("path", "max-age", "httponly", "samesite");
        Set<String> names = new HashSet<>();
        Long maxAge = null;
        for (int index = 1; index < segments.length; index++) {
            ParsedAttribute attribute = parseAttribute(segments[index].trim());
            if (!requiredAttributes.contains(attribute.name()) || !names.add(attribute.name())) {
                throw new InvalidUpstreamResponseException();
            }
            maxAge = validateAttribute(attribute, maxAge, profile);
        }
        if (!names.equals(requiredAttributes) || maxAge == null) {
            throw new InvalidUpstreamResponseException();
        }
        return new ParsedAttributes(maxAge);
    }

    private ParsedAttribute parseAttribute(String segment) {
        if (segment.isEmpty()) {
            throw new InvalidUpstreamResponseException();
        }
        int separator = segment.indexOf('=');
        String name = (separator < 0 ? segment : segment.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String value = separator < 0 ? null : segment.substring(separator + 1).trim();
        return new ParsedAttribute(name, value);
    }

    private Long validateAttribute(ParsedAttribute attribute, Long currentMaxAge, CookieProfile profile) {
        return switch (attribute.name()) {
            case "path" -> {
                requireExactValue(attribute.value(), profile.path());
                yield currentMaxAge;
            }
            case "max-age" -> parseMaxAge(attribute.value());
            case "secure", "httponly" -> {
                requireExactValue(attribute.value(), null);
                yield currentMaxAge;
            }
            case "samesite" -> {
                requireExactValue(attribute.value(), profile.sameSite());
                yield currentMaxAge;
            }
            default -> throw new InvalidUpstreamResponseException();
        };
    }

    private long parseMaxAge(String value) {
        if (value == null || value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
            throw new InvalidUpstreamResponseException();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new InvalidUpstreamResponseException();
        }
    }

    private void validateCookie(CookiePair cookiePair, long maxAge, RefreshCookieResponsePolicy responsePolicy, CookieProfile profile) {
        if (maxAge > profile.maxSessionAgeSeconds() || cookiePair.empty() && maxAge != 0) {
            throw new InvalidUpstreamResponseException();
        }
        boolean rejectsClear = responsePolicy == RefreshCookieResponsePolicy.ISSUE_ONLY && maxAge == 0;
        boolean rejectsIssue = responsePolicy == RefreshCookieResponsePolicy.CLEAR_ONLY && maxAge > 0;
        if (rejectsClear || rejectsIssue) {
            throw new InvalidUpstreamResponseException();
        }
    }

    private boolean isValidOpaqueValue(String value) {
        if (value.isEmpty()) {
            return true;
        }
        if (value.charAt(0) == '"') {
            return value.length() >= 2 && value.charAt(value.length() - 1) == '"'
                && hasOnlyCookieOctets(value, 1, value.length() - 1);
        }
        return hasOnlyCookieOctets(value, 0, value.length());
    }

    private boolean isEmptyValue(String value) {
        return value.isEmpty() || "\"\"".equals(value);
    }

    private boolean hasOnlyCookieOctets(String value, int start, int end) {
        for (int index = start; index < end; index++) {
            char current = value.charAt(index);
            if (current != 0x21 && !(current >= 0x23 && current <= 0x2B) && !(current >= 0x2D && current <= 0x3A)
                && !(current >= 0x3C && current <= 0x5B) && !(current >= 0x5D && current <= 0x7E)) {
                return false;
            }
        }
        return true;
    }

    private void requireExactValue(String actual, String expected) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new InvalidUpstreamResponseException();
        }
    }

    private record CookieProfile(String name, String path, String sameSite, long maxSessionAgeSeconds, boolean secureOnly) {
    }

    private record CookiePair(String value, boolean empty) {
    }

    private record ParsedAttribute(String name, String value) {
    }

    private record ParsedAttributes(long maxAge) {
    }
}
