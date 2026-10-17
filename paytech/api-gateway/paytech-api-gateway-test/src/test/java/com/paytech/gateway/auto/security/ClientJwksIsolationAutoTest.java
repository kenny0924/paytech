package com.paytech.gateway.auto.security;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.security.ReactiveJwksProvider;
import com.sun.net.httpserver.HttpServer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.scheduler.Schedulers;

import static org.junit.jupiter.api.Assertions.*;

/** 同 kid、不同公钥的真实 HTTP 验证，两个 Realm 不能共享缓存或查询路径。 */
class ClientJwksIsolationAutoTest {
    @Test
    void sameIssuerCannotEnableBothHumanRealms() {
        var client = new com.paytech.gateway.security.ClientSecurityProperties("https://identity.example.test", "client-console");
        var operations = new com.paytech.gateway.security.OperationsSecurityProperties("https://identity.example.test", "operations-console");
        assertThrows(IllegalArgumentException.class,
            () -> new com.paytech.gateway.security.ClientSecurityConfiguration(client, operations));
    }

    @Test
    void sameKidIsResolvedOnlyWithinItsRealmAndCachedIndependently() throws Exception {
        var operationsKey = new RSAKeyGenerator(2048).keyID("shared-kid").keyUse(KeyUse.SIGNATURE)
            .keyOperations(Set.of(KeyOperation.VERIFY)).algorithm(JWSAlgorithm.RS256).generate().toPublicJWK();
        var customerKey = new RSAKeyGenerator(2048).keyID("shared-kid").keyUse(KeyUse.SIGNATURE)
            .keyOperations(Set.of(KeyOperation.VERIFY)).algorithm(JWSAlgorithm.RS256).generate().toPublicJWK();
        var operationsCalls = new AtomicInteger();
        var customerCalls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/platform-support/authentication/v1/realms/", exchange -> {
            boolean customer = exchange.getRequestURI().getPath().endsWith("/CUSTOMER/jwks");
            boolean operations = exchange.getRequestURI().getPath().endsWith("/OPERATIONS/jwks");
            if (!customer && !operations) { exchange.sendResponseHeaders(404, -1); exchange.close(); return; }
            (customer ? customerCalls : operationsCalls).incrementAndGet();
            byte[] body = new JWKSet(customer ? customerKey : operationsKey).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        var scheduler = Schedulers.newSingle("client-jwks-fixture");
        var meters = new SimpleMeterRegistry();
        try {
            var client = WebClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
            var metrics = new GatewayMetrics(meters);
            var operations = new ReactiveJwksProvider(client, Clock.systemUTC(), scheduler, metrics);
            var customer = new ReactiveJwksProvider(client, Clock.systemUTC(), scheduler, metrics, "CUSTOMER");
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("shared-kid").build(),
                new JWTClaimsSet.Builder().subject("fixture").build());
            for (int i = 0; i < 2; i++) {
                assertEquals(operationsKey, operations.select(jwt).single().block(Duration.ofSeconds(5)));
                assertEquals(customerKey, customer.select(jwt).single().block(Duration.ofSeconds(5)));
            }
            assertEquals(1, operationsCalls.get());
            assertEquals(1, customerCalls.get());
            assertThrows(IllegalArgumentException.class,
                () -> new ReactiveJwksProvider(client, Clock.systemUTC(), scheduler, metrics, "CLIENT"));
        } finally { meters.close(); scheduler.dispose(); server.stop(0); }
    }
}
