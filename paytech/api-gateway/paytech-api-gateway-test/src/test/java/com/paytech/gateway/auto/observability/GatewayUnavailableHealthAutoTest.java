package com.paytech.gateway.auto.observability;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import com.paytech.gateway.test.RedisUnavailableGatewayAutoTestBase;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Redis 不可用时 Gateway 健康组的 Fail-Closed 边界测试。 */
class GatewayUnavailableHealthAutoTest extends RedisUnavailableGatewayAutoTestBase {
    private static final String REQUEST_ID_CANARY = "caller-management-request-id";
    private static final String RESERVED_HEADER_CANARY = "caller-management-subject";

    @Test
    void test_privateManagementPrometheusIsReachableWithoutExposingOtherActuatorEndpoints() {
        managementClient().get().uri("/actuator/prometheus").exchange().expectStatus().isOk().expectBody(String.class)
            .value(body -> Assertions.assertTrue(body.contains("gateway_snapshot_valid")));

        managementClient().get().uri("/actuator/health").exchange().expectStatus().isUnauthorized();
        managementClient().get().uri("/actuator/info").exchange().expectStatus().isUnauthorized();
        managementClient().get().uri("/actuator").exchange().expectStatus().isUnauthorized();
        gatewayClient().get().uri("/actuator/prometheus").exchange().expectStatus().isNotFound();
        gatewayClient().get().uri("/actuator/health/liveness").exchange().expectStatus().isNotFound();
        gatewayClient().get().uri("/actuator/health/readiness").exchange().expectStatus().isNotFound();
    }

    @Test
    void test_unavailableRedisMakesReadinessDownWithoutAffectingLiveness() {
        managementClient().get().uri("/actuator/health/readiness").exchange().expectStatus().isEqualTo(503).expectBody()
            .jsonPath("$.status").isEqualTo("DOWN").jsonPath("$.components.gatewayBootstrap.status").isEqualTo("UP")
            .jsonPath("$.components.gatewayRedis.status").isEqualTo("DOWN");

        managementClient().get().uri("/actuator/health/liveness").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void test_managementRequestRetainsTrustedRequestContextWithoutClientIp() {
        Logger accessLogger = (Logger)LoggerFactory.getLogger("gateway.access");
        Level originalLevel = accessLogger.getLevel();
        ListAppender<ILoggingEvent> accessProbe = new ListAppender<>();
        accessProbe.start();
        accessLogger.setLevel(Level.INFO);
        accessLogger.addAppender(accessProbe);
        try {
            EntityExchangeResult<byte[]> response = managementClient().get().uri("/actuator/health/readiness")
                .header("X-Request-Id", REQUEST_ID_CANARY).header("X-Gateway-Actor-Subject", RESERVED_HEADER_CANARY)
                .exchange().expectStatus().isEqualTo(503).expectBody().returnResult();

            String requestId = response.getResponseHeaders().getFirst("X-Request-Id");
            Assertions.assertNotNull(requestId);
            Assertions.assertNotEquals(REQUEST_ID_CANARY, requestId);
            Assertions.assertDoesNotThrow(() -> UUID.fromString(requestId));
            Awaitility.await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> Assertions.assertEquals(1, accessProbe.list.size()));
            ILoggingEvent event = accessProbe.list.getFirst();
            Map<String, String> fields = event.getKeyValuePairs().stream()
                .collect(java.util.stream.Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
            Assertions.assertEquals(requestId, fields.get("request_id"));
            Assertions.assertEquals("unavailable", fields.get("ip_digest"));
            Assertions.assertFalse(fields.containsKey("subject_digest"));
            Assertions.assertFalse((event.getFormattedMessage() + fields).contains(REQUEST_ID_CANARY));
            Assertions.assertFalse((event.getFormattedMessage() + fields).contains(RESERVED_HEADER_CANARY));
        } finally {
            accessLogger.detachAppender(accessProbe);
            accessLogger.setLevel(originalLevel);
            accessProbe.stop();
        }
    }
}
