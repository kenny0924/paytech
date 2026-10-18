package com.paytech.gateway.auto.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;

import com.paytech.gateway.security.clientapi.ClientApiAuthenticationToken;
import com.paytech.gateway.security.clientapi.ClientApiCredentialServerAuthenticationConverter;
import com.paytech.gateway.security.clientapi.ClientApiIdentity;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;

/** 只测试已解析 Header 的纯语法边界，不替换任何远程调用。 */
class ClientApiCredentialConverterAutoTest {
    @Test
    void test_ownerMinimumMaximumAndParsedWhitespaceBoundaries() {
        var converter = new ClientApiCredentialServerAuthenticationConverter();
        for (String credential : List.of("p".repeat(ApiCredentialConstraints.MIN_KEY_PREFIX_LENGTH) + "."
            + "s".repeat(ApiCredentialConstraints.MIN_SECRET_LENGTH),
            "p".repeat(ApiCredentialConstraints.MAX_KEY_PREFIX_LENGTH) + "."
                + "s".repeat(ApiCredentialConstraints.MAX_SECRET_LENGTH))) {
            var exchange = exchange("Bearer " + credential);
            var token = (ClientApiAuthenticationToken)converter.convert(exchange).block();
            assertNotNull(token);
            assertFalse(token.toString().contains(credential));
            assertTrue(credential.equals(token.getCredentials()));
            token.eraseCredentials();
            assertNull(token.getCredentials());
            assertThrows(IllegalArgumentException.class, () -> token.setAuthenticated(true));
            for (String invalid : List.of("Bearer " + credential + " ", "Bearer\t" + credential,
                "Bearer " + credential + "\t", "Bearer " + credential + "\u0001")) {
                assertThrows(BadCredentialsException.class, () -> converter.convert(exchange(invalid)).block());
            }
        }
    }

    @Test
    void test_authenticatedTokenAndIdentityDiagnosticStringsAreRedacted() {
        var identity = new ClientApiIdentity("91", "92", "93", Set.of("scope.secret"));
        var token = ClientApiAuthenticationToken.authenticated(identity);
        assertTrue(token.isAuthenticated());
        assertNull(token.getCredentials());
        assertEquals("93", identity.principalKey());
        assertEquals("91", identity.tenantKey().orElseThrow());
        for (String canary : List.of("91", "92", "93", "scope.secret")) {
            assertFalse(identity.toString().contains(canary));
            assertFalse(token.toString().contains(canary));
        }
        assertThrows(UnsupportedOperationException.class, () -> identity.effectiveScopes().add("new.scope"));
    }

    private MockServerWebExchange exchange(String authorization) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/test")
            .header(HttpHeaders.AUTHORIZATION, authorization));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "converter-test");
        return exchange;
    }
}
