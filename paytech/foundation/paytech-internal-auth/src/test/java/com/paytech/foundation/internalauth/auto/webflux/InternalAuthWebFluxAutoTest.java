package com.paytech.foundation.internalauth.auto.webflux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.nimbusds.jose.jwk.ECKey;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.CallerIdentityHolder;
import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalPurpose;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;
import com.paytech.foundation.internalauth.core.inbound.InternalCallerAuthenticator;
import com.paytech.foundation.internalauth.core.supplier.StaticKeyInternalTokenSupport;
import com.paytech.foundation.internalauth.core.token.InternalTokenVerifierImpl;
import com.paytech.foundation.internalauth.test.TestKeys;
import com.paytech.foundation.internalauth.webflux.InternalAuthWebFilter;
import com.paytech.foundation.internalauth.webflux.InternalTokenExchangeFilterFunction;
import com.paytech.foundation.testsupport.api.MockHttpServer;

/**
 * WebFlux 入站（WebTestClient）三种模式与出站 WebClient Token 注入（供 Gateway 使用）。
 *
 * @see InternalAuthWebFilter#filter(org.springframework.web.server.ServerWebExchange,
 *      org.springframework.web.server.WebFilterChain)
 * @see InternalTokenExchangeFilterFunction#filter(org.springframework.web.reactive.function.client.ClientRequest,
 *      org.springframework.web.reactive.function.client.ExchangeFunction)
 */
class InternalAuthWebFluxAutoTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ECKey key = TestKeys.generate("kid-1");
    private final StaticKeyInternalTokenSupport issuer = new StaticKeyInternalTokenSupport(key, key.toPublicJWK(),
        TestKeys.CALLER, TestKeys.INSTANCE, CLOCK);
    private final InternalTokenVerifierImpl verifier = new InternalTokenVerifierImpl(issuer, TestKeys.TARGET,
        TestKeys.INSTANCE, CLOCK);
    private final HeaderController controller = new HeaderController();
    private final MockHttpServer server = new MockHttpServer(controller);

    @AfterEach
    void closeServer() {
        server.assertNoUnexpectedRequests();
        server.close();
    }

    @Test
    void test_normalFlow_requiredAcceptsValidToken() {
        WebTestClient client = client(InternalAuthMode.REQUIRED);

        client.get().uri("/internal/echo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
            .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo(TestKeys.CALLER + "|GENERAL|true");
    }

    @Test
    void test_businessRuleViolation_requiredRejectsMissingAndInvalidToken() {
        WebTestClient client = client(InternalAuthMode.REQUIRED);

        client.get().uri("/internal/echo").header("X-Request-Id", "req-1").exchange().expectStatus().isUnauthorized()
            .expectHeader().valueEquals("X-Request-Id", "req-1")
            .expectHeader().contentType("application/problem+json")
            .expectBody().jsonPath("$.code").isEqualTo("INTERNAL_TOKEN_INVALID")
            .jsonPath("$.type").isEqualTo("about:blank").jsonPath("$.requestId").isEqualTo("req-1")
            .jsonPath("$.timestamp").isEqualTo("2026-10-17T10:00:00.000000Z");
        client.get().uri("/internal/echo").header(HttpHeaders.AUTHORIZATION, "Bearer broken").exchange()
            .expectStatus().isUnauthorized();
        client.get().uri("/internal/echo").header("X-Caller-Service", "payment-service").exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void test_normalFlow_dualFallsBackToLegacyHeader() {
        WebTestClient client = client(InternalAuthMode.DUAL);

        client.get().uri("/internal/echo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token()).exchange()
            .expectStatus().isOk().expectBody(String.class).isEqualTo(TestKeys.CALLER + "|GENERAL|true");
        client.get().uri("/internal/echo").header("X-Caller-Service", "risk-service").exchange().expectStatus().isOk()
            .expectBody(String.class).isEqualTo("risk-service|null|false");
        client.get().uri("/internal/echo").header(HttpHeaders.AUTHORIZATION, "Bearer broken")
            .header("X-Caller-Service", "risk-service").exchange().expectStatus().isOk()
            .expectBody(String.class).isEqualTo("risk-service|null|false");
        client.get().uri("/internal/echo").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("none");
    }

    @Test
    void test_edgeCase_offAndExemptPaths() {
        client(InternalAuthMode.OFF).get().uri("/internal/echo").exchange().expectStatus().isOk()
            .expectBody(String.class).isEqualTo("none");
        WebTestClient required = client(InternalAuthMode.REQUIRED);
        required.get().uri("/actuator/health").exchange().expectStatus().isOk();
        required.get().uri("/public/ping").exchange().expectStatus().isOk();
    }

    @Test
    void test_businessRuleViolation_keySourceUnavailableReturns503() {
        InternalTokenVerifierImpl unavailable = new InternalTokenVerifierImpl(keyId -> {
            throw new InternalTokenUnavailableException(null);
        }, TestKeys.TARGET, TestKeys.INSTANCE, CLOCK);
        WebTestClient client = WebTestClient.bindToRouterFunction(routes())
            .webFilter(new InternalAuthWebFilter(new InternalCallerAuthenticator(InternalAuthMode.REQUIRED, unavailable,
                List.of("/actuator")), CLOCK)).build();

        client.get().uri("/internal/echo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token()).exchange()
            .expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void test_normalFlow_outboundInjectsTokenWithPurpose() {
        WebClient client = WebClient.builder().baseUrl(server.baseUrl()).clientConnector(new JdkClientHttpConnector())
            .filter(new InternalTokenExchangeFilterFunction(issuer, TestKeys.TARGET)).build();

        client.get().uri("/outbound").retrieve().bodyToMono(String.class).block();
        client.get().uri("/outbound").attribute(InternalPurpose.REQUEST_ATTRIBUTE, "CHANNEL_CARD_MATERIAL_REVEAL")
            .retrieve().bodyToMono(String.class).block();

        assertEquals(2, controller.authorizations.size());
        CallerIdentity general = verifier.verify(controller.authorizations.get(0).substring("Bearer ".length()));
        CallerIdentity reveal = verifier.verify(controller.authorizations.get(1).substring("Bearer ".length()));
        assertEquals("GENERAL", general.purpose());
        assertEquals("CHANNEL_CARD_MATERIAL_REVEAL", reveal.purpose());
    }

    @Test
    void test_businessRuleViolation_outboundTokenUnavailableSendsNothing() {
        InternalTokenSupplier failing = (target, purpose) -> {
            throw new InternalTokenUnavailableException(null);
        };
        WebClient client = WebClient.builder().baseUrl(server.baseUrl()).clientConnector(new JdkClientHttpConnector())
            .filter(new InternalTokenExchangeFilterFunction(failing, TestKeys.TARGET)).build();

        assertThrows(InternalTokenUnavailableException.class,
            () -> client.get().uri("/outbound").retrieve().bodyToMono(String.class).block());
        assertTrue(controller.authorizations.isEmpty());
    }

    private WebTestClient client(InternalAuthMode mode) {
        InternalCallerAuthenticator authenticator = new InternalCallerAuthenticator(mode, verifier,
            List.of("/actuator", "/public/"));
        return WebTestClient.bindToRouterFunction(routes()).webFilter(new InternalAuthWebFilter(authenticator, CLOCK))
            .build();
    }

    private static org.springframework.web.reactive.function.server.RouterFunction<ServerResponse> routes() {
        return RouterFunctions.route()
            .GET("/internal/echo", request -> {
                CallerIdentity identity = (CallerIdentity) request.exchange().getAttributes()
                    .get(CallerIdentityHolder.REQUEST_ATTRIBUTE);
                String body = identity == null ? "none"
                    : identity.caller() + "|" + identity.purpose() + "|" + identity.verified();
                return ServerResponse.ok().bodyValue(body);
            })
            .GET("/actuator/health", request -> ServerResponse.ok().bodyValue("UP"))
            .GET("/public/ping", request -> ServerResponse.ok().bodyValue("pong"))
            .build();
    }

    private String token() {
        return issuer.token(TestKeys.TARGET, "GENERAL");
    }

    /** 记录出站请求 Authorization 头的测试端点。 */
    @RestController
    static final class HeaderController {

        private final List<String> authorizations = new CopyOnWriteArrayList<>();

        @GetMapping("/outbound")
        String outbound(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
            authorizations.add(authorization);
            return "ok";
        }
    }
}
