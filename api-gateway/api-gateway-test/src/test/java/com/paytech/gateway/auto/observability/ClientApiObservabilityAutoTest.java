package com.paytech.gateway.auto.observability;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.gateway.test.ClientApiGatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore.Response;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.micrometer.core.instrument.MeterRegistry;

class ClientApiObservabilityAutoTest extends ClientApiGatewayAutoTestBase {
    @Autowired
    private MeterRegistry meters;

    @Test
    void test_logsAndMetricsNeverContainRawCredentialsIdentitiesOrScopes() {
        Capture capture = new Capture();
        capture.start();
        Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(capture);
        try {
            GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write", "scope.canary");
            GatewayAutoTestRuntime.clientApiOwnerSimulator().scenarios().expect(HttpMethod.POST,
                GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH, Response.json(200, "{}"));
            var result = authenticatedRequest().exchange().expectBody().returnResult();
            assertEquals(200, result.getStatus().value());
            Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> capture.accessEvents().size() == 1);
            ILoggingEvent event = capture.accessEvents().getFirst();
            var fields = event.getKeyValuePairs().stream().map(pair -> pair.key).toList();
            assertTrue(fields.containsAll(List.of("client_digest", "subject_digest", "channel", "profile")));
            String logs = capture.events.stream().map(value -> value.getFormattedMessage() + value.getKeyValuePairs())
                .collect(java.util.stream.Collectors.joining("\n"));
            String metricIds = meters.getMeters().stream().map(meter -> meter.getId().toString())
                .collect(java.util.stream.Collectors.joining("\n"));
            for (String canary : List.of(PlatformCredentialValidationSimulator.CREDENTIAL,
                PlatformCredentialValidationSimulator.CLIENT, PlatformCredentialValidationSimulator.TENANT,
                PlatformCredentialValidationSimulator.CREDENTIAL_ID, "scope.canary", "orders.write")) {
                assertFalse(logs.contains(canary), "日志不得记录原始认证材料");
                assertFalse(metricIds.contains(canary), "指标不得记录原始认证材料");
                assertFalse(GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().entries().toString().contains(canary));
            }
        } finally {
            root.detachAppender(capture);
            capture.stop();
        }
    }

    private static final class Capture extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
        @Override protected void append(ILoggingEvent event) { events.add(event); }
        List<ILoggingEvent> accessEvents() {
            return events.stream().filter(event -> event.getLoggerName().equals("gateway.access")).toList();
        }
    }
}
