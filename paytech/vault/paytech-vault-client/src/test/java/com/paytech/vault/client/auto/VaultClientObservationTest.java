package com.paytech.vault.client.auto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.restclient.RestClientCustomizer;

import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;
import com.paytech.vault.client.test.ClientContext;
import com.paytech.vault.client.test.VaultSimulator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class VaultClientObservationTest {
    @Test
    void test_bootObservationAndDebugLogsContainNoSensitiveValues() throws Exception {
        String problemDetail = "REMOTE_PROBLEM_OBSERVATION_DETAIL_CANARY";
        String problemResponseId = "problem-observation-response-id";
        String legacyMessage = "REMOTE_LEGACY_OBSERVATION_MESSAGE_CANARY";
        String legacyResponseId = "~legacy-observation-response-id";
        String invalidDetail = "REMOTE_INVALID_OBSERVATION_DETAIL_CANARY";
        String invalidResponseId = "invalid-observation-response-id";
        String[] sensitiveCanaries = {
            VaultClientSafetyTest.TOKEN, VaultClientSafetyTest.VALUE, VaultClientSafetyTest.KEY,
            problemDetail, problemResponseId, legacyMessage, legacyResponseId, invalidDetail, invalidResponseId
        };
        var observations = new ArrayList<Observation.Context>();
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override public boolean supportsContext(Observation.Context context) { return true; }
            @Override public void onStop(Observation.Context context) { observations.add(context); }
        });
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level original = root.getLevel();
        var originalAppenders = new ArrayList<ch.qos.logback.core.Appender<ILoggingEvent>>();
        root.iteratorForAppenders().forEachRemaining(originalAppenders::add);
        originalAppenders.forEach(root::detachAppender);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); root.addAppender(logs); root.setLevel(Level.DEBUG);
        try (var simulator = new VaultSimulator(); var other = new VaultSimulator()) {
            ClientContext.direct(simulator).withBean(RestClientCustomizer.class, () -> builder -> builder.observationRegistry(registry)
                            .uriBuilderFactory(new org.springframework.web.util.DefaultUriBuilderFactory(other.url()))
                            .defaultRequest(spec -> spec.httpRequest(request -> {
                                request.getHeaders().set("X-Caller-Service", "callback-caller");
                                request.getHeaders().set("X-Request-Id", "callback-id");
                                request.getHeaders().set("Idempotency-Key", "callback-key");
                            })).requestInitializer(request -> request.getHeaders().add("Idempotency-Key", "initializer-key")))
                    .run(context -> {
                        var client = context.getBean(VaultClient.class);
                        simulator.respond(200, "{\"token\":\"" + VaultClientSafetyTest.TOKEN + "\"}");
                        client.protect(new ProtectCommand("A", TokenMode.STABLE, VaultClientSafetyTest.VALUE, "P", VaultClientSafetyTest.KEY));
                        for (String body : List.of("{\"value\":\"" + VaultClientSafetyTest.VALUE + "\",\"unexpected\":true}",
                                "{\"value\":\"" + VaultClientSafetyTest.VALUE + "\",", "{\"value\":\"\\ud800\"}")) {
                            simulator.respond(200, body);
                            assertThatThrownBy(() -> client.reveal(new RevealQuery(VaultClientSafetyTest.TOKEN, "P")))
                                    .hasNoCause().hasMessageNotContaining(VaultClientSafetyTest.VALUE);
                        }
                        simulator.respond(500, "{\"type\":\"about:blank\",\"title\":\"Internal Server Error\","
                                + "\"detail\":\"" + problemDetail + "\",\"code\":\"INTERNAL_ERROR\","
                                + "\"requestId\":\"" + problemResponseId
                                + "\",\"timestamp\":\"2026-09-06T10:20:30.123456Z\"}",
                                "application/problem+json", List.of(problemResponseId));
                        assertThatThrownBy(() -> client.reveal(new RevealQuery(VaultClientSafetyTest.TOKEN, "P")))
                                .hasNoCause().hasNoSuppressedExceptions().hasMessageNotContaining(problemDetail)
                                .hasMessageNotContaining(problemResponseId);

                        simulator.respond(503, "{\"code\":\"DEPENDENCY_UNAVAILABLE\",\"message\":\""
                                + legacyMessage + "\",\"requestId\":\"" + legacyResponseId
                                + "\",\"timestamp\":\"2026-09-06T10:20:30.123Z\"}",
                                "application/json", List.of(legacyResponseId));
                        assertThatThrownBy(() -> client.reveal(new RevealQuery(VaultClientSafetyTest.TOKEN, "P")))
                                .hasNoCause().hasNoSuppressedExceptions().hasMessageNotContaining(legacyMessage)
                                .hasMessageNotContaining(legacyResponseId);

                        simulator.respond(400, "{\"type\":\"wrong\",\"title\":\"Bad Request\",\"detail\":\""
                                + invalidDetail + "\",\"code\":\"INVALID_REQUEST\",\"requestId\":\""
                                + invalidResponseId + "\",\"timestamp\":\"2026-09-06T10:20:30.123456Z\"}",
                                "application/problem+json", List.of(invalidResponseId));
                        assertThatThrownBy(() -> client.reveal(new RevealQuery(VaultClientSafetyTest.TOKEN, "P")))
                                .hasNoCause().hasNoSuppressedExceptions().hasMessageNotContaining(invalidDetail)
                                .hasMessageNotContaining(invalidResponseId);
                    });
            assertThat(observations).hasSize(7);
            assertThat(other.requests).isEmpty();
            assertThat(simulator.requests).extracting(request -> request.path()).containsExactly(
                "/vault/v1/records/protect", "/vault/v1/records/reveal", "/vault/v1/records/reveal",
                "/vault/v1/records/reveal", "/vault/v1/records/reveal", "/vault/v1/records/reveal",
                "/vault/v1/records/reveal");
            assertThat(simulator.requests.getFirst().headers().get("Idempotency-Key")).containsExactly(VaultClientSafetyTest.KEY);
            for (var request : simulator.requests) {
                assertThat(request.headers().get("X-Caller-Service")).containsExactly("commerce-service");
                if (request.path().equals("/vault/v1/records/reveal")) {
                    assertThat(request.headers().get("Idempotency-Key")).isNull();
                }
            }
            assertThat(observations.stream().filter(value -> value.getError() != null)).hasSize(6);
            for (var context : observations) {
                String captured = context.getAllKeyValues().toString() + context.getError();
                if (context instanceof org.springframework.http.client.observation.ClientRequestObservationContext http) {
                    captured += http.getCarrier().getAttributes();
                }
                assertThat(captured).doesNotContain(sensitiveCanaries);
                if (context.getError() != null) {
                    assertThat(context.getError()).hasNoCause().hasNoSuppressedExceptions();
                }
            }
            for (var event : logs.list) {
                String captured = event.getFormattedMessage()
                        + java.util.Arrays.toString(event.getArgumentArray()) + event.getThrowableProxy();
                assertThat(captured).doesNotContain(sensitiveCanaries);
            }
        } finally {
            root.setLevel(original);
            root.detachAppender(logs);
            originalAppenders.forEach(root::addAppender);
            logs.stop();
        }
    }
}
