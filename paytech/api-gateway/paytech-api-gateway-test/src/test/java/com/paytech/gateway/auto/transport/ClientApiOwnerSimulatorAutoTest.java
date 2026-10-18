package com.paytech.gateway.auto.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerSimulator;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore.Outcome;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** Owner 测试协议设施必须存在，并与宽松 BFF Journal 隔离。 */
@ExtendWith(OutputCaptureExtension.class)
class ClientApiOwnerSimulatorAutoTest {
    @Test
    void rawScenarioOwnsExactChunksHeaderMultiplicityAndRequestIdReflection() {
        String requestId = "client-owner-simulator-1";
        String canary = "RAW_OWNER_RESPONSE_CANARY";
        byte[] first = ("{\"requestId\":\"" + ClientApiOwnerScenarioStore.REQUEST_ID_TOKEN + "\",")
            .getBytes(StandardCharsets.UTF_8);
        byte[] second = ("\"detail\":\"" + canary + "\"}").getBytes(StandardCharsets.UTF_8);
        var scenario = ClientApiOwnerScenarioStore.Response.rawChunks(422, List.of(first, second),
            "application/problem+json", "application/json")
            .withHeader("X-Request-Id", ClientApiOwnerScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId();
        assertFalse(scenario.toString().contains(canary));
        try (var simulator = new ClientApiOwnerSimulator()) {
            simulator.start();
            simulator.scenarios().expect(HttpMethod.GET, "/client/raw", scenario);

            RawResponse response = HttpClient.create().baseUrl(simulator.uri().toString())
                .headers(headers -> headers.set("X-Request-Id", requestId)).get().uri("/client/raw")
                .responseSingle((head, body) -> body.asByteArray().defaultIfEmpty(new byte[0])
                    .map(bytes -> new RawResponse(head.status().code(), head.responseHeaders().getAll("Content-Type"),
                        head.responseHeaders().getAll("X-Request-Id"), bytes)))
                .block(Duration.ofSeconds(3));

            assertNotNull(response);
            assertEquals(422, response.status());
            assertEquals(List.of("application/problem+json", "application/json"), response.contentTypes());
            assertEquals(List.of(requestId), response.requestIds());
            assertArrayEquals((new String(first, StandardCharsets.UTF_8)
                .replace(ClientApiOwnerScenarioStore.REQUEST_ID_TOKEN, requestId)
                + new String(second, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8), response.body());
            simulator.verifyComplete();
        }
    }

    @Test
    void operationsSimulatorReplaysRawChunksAndReflectedResponseMetadata() {
        String requestId = "operations-owner-simulator-1";
        String raw = "{\"requestId\":\"{{requestId}}\",\"extension\":true}";
        int split = raw.indexOf(',') + 1;
        try (var simulator = new OperationsBffSimulator()) {
            simulator.start();
            simulator.scenarios().expect(HttpMethod.POST, "/auth/raw",
                BffScenarioStore.Response.rawChunks(409,
                    List.of(raw.substring(0, split).getBytes(StandardCharsets.UTF_8),
                        raw.substring(split).getBytes(StandardCharsets.UTF_8)),
                    "application/problem+json")
                    .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId());

            RawResponse response = HttpClient.create().baseUrl(simulator.uri().toString()).headers(headers -> {
                headers.set("X-Request-Id", requestId);
                headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
            }).post().uri("/auth/raw").send(reactor.netty.ByteBufFlux.fromString(Mono.just("{}")))
                .responseSingle((head, body) -> body.asByteArray().defaultIfEmpty(new byte[0])
                    .map(bytes -> new RawResponse(head.status().code(), head.responseHeaders().getAll("Content-Type"),
                        head.responseHeaders().getAll("X-Request-Id"), bytes)))
                .block(Duration.ofSeconds(3));

            assertNotNull(response);
            assertEquals(409, response.status());
            assertEquals(List.of("application/problem+json"), response.contentTypes());
            assertEquals(List.of(requestId), response.requestIds());
            assertArrayEquals(raw.replace(BffScenarioStore.REQUEST_ID_TOKEN, requestId)
                .getBytes(StandardCharsets.UTF_8), response.body());
            simulator.verifyComplete();
        }
    }

    @Test
    void identityAndUnknownReservedHeadersLeaveOnlyBoundedPresenceSignals(CapturedOutput output) {
        String client = "client-identity-canary";
        String credential = "credential-identity-canary";
        String unknown = "unknown-reserved-value-canary";
        String unknownName = "unknown-name-canary";
        try (var simulator = new ClientApiOwnerSimulator()) {
            simulator.start();
            String path = "/client/" + client;
            simulator.scenarios().expect(HttpMethod.GET, path, Outcome.ACCEPTED);
            int status = HttpClient.create().baseUrl(simulator.uri().toString()).headers(headers -> {
                headers.set("X-Gateway-Client-Id", client);
                headers.set("X-Gateway-Credential-Id", credential);
                headers.set("x-GaTeWaY-" + unknownName, unknown);
                headers.set("X-FoRwArDeD-" + unknownName, unknown);
                headers.set("Idempotency-Key", credential);
                headers.set("X-Request-Id", unknown);
            }).get().uri(path).responseSingle((response, body) -> body.thenReturn(response.status().code()))
                .block(Duration.ofSeconds(3));
            assertEquals(200, status);
            var entry = simulator.journal().entries().getFirst();
            for (String value : List.of(client, credential, unknown, unknownName)) {
                assertFalse(entry.toString().contains(value), "Journal 不得暴露身份或保留 Header 原始数据");
                assertFalse(output.getAll().contains(value), "普通日志不得暴露身份或保留 Header 原始数据");
            }
            assertEquals("[REDACTED]", entry.rawPath());
            assertEquals(Map.of("idempotency-key", List.of("[REDACTED]"), "x-request-id", List.of("[REDACTED]")), entry.headers());
            assertTrue(entry.headerNames().containsAll(List.of("x-gateway-client-id", "x-gateway-credential-id",
                "reserved-gateway-header", "reserved-forwarded-header")));
            simulator.verifyComplete();
        }
    }

    @Test
    void runtimeOwnerAccessorMustNotStartOrReplaceTheSharedServer() throws Exception {
        var calls = new java.util.ArrayList<String>();
        try (var input = com.paytech.gateway.test.GatewayAutoTestRuntime.class
            .getResourceAsStream("GatewayAutoTestRuntime.class")) {
            new org.springframework.asm.ClassReader(input).accept(new org.springframework.asm.ClassVisitor(org.springframework.asm.Opcodes.ASM9) {
                @Override
                public org.springframework.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                    if (!name.equals("clientApiOwnerSimulator")) {
                        return null;
                    }
                    return new org.springframework.asm.MethodVisitor(org.springframework.asm.Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean isInterface) {
                            calls.add(method);
                        }
                    };
                }
            }, 0);
        }
        assertTrue(calls.isEmpty(), "共享 Owner accessor 必须只返回实例，不能隐式启动或替换实例");
        try (var simulator = new ClientApiOwnerSimulator()) {
            assertFalse(simulator.isRunning());
            simulator.reset();
            simulator.verifyComplete();
            assertThrows(IllegalStateException.class, simulator::uri);
            assertFalse(simulator.isRunning());
            simulator.start();
            var uri = simulator.uri();
            assertTrue(simulator.isRunning());
            simulator.reset();
            simulator.verifyComplete();
            assertEquals(uri, simulator.uri());
            simulator.close();
            assertFalse(simulator.isRunning());
            simulator.verifyComplete();
            assertThrows(IllegalStateException.class, simulator::uri);
            assertFalse(simulator.isRunning());
        }
    }

    @Test
    void recordsRawPathAndOnlyApprovedHeadersWithoutCredentialOrBody(CapturedOutput output) {
        String canary = "synthetic-owner-credential-canary";
        try (var simulator = new ClientApiOwnerSimulator()) {
            simulator.start();
            simulator.scenarios().expect(HttpMethod.POST, "/client/order%2D1", Outcome.ACCEPTED);
            String body = "{\"private\":\"" + canary + "\"}";
            int status = HttpClient.create().baseUrl(simulator.uri().toString()).headers(headers -> {
                headers.set("Authorization", "Bearer " + canary);
                headers.set("X-Unapproved", canary);
                headers.set("Cookie", canary);
                headers.set("Idempotency-Key", "command-1");
                headers.set("X-Request-Id", canary);
            }).post().uri("/client/order%2D1?ignored=" + canary).send(reactor.netty.ByteBufFlux.fromString(Mono.just(body)))
                .responseSingle((response, bytes) -> bytes.thenReturn(response.status().code()))
                .block(Duration.ofSeconds(3));
            assertEquals(200, status);
            assertEquals(1, simulator.journal().count());
            var entry = simulator.journal().entries().getFirst();
            assertEquals(HttpMethod.POST, entry.method());
            assertEquals("/client/order%2D1", entry.rawPath());
            assertEquals(Map.of("idempotency-key", List.of("command-1"), "x-request-id", List.of("[REDACTED]")), entry.headers());
            assertEquals(body.length(), entry.bodyLength());
            assertFalse(simulator.journal().entries().toString().contains(canary));
            assertTrue(entry.headerNames().contains("authorization"));
            assertTrue(entry.headerNames().contains("cookie"));
            assertFalse(output.getAll().contains(canary));
            simulator.verifyComplete();
            simulator.reset();
            assertEquals(0, simulator.journal().count());
            simulator.verifyComplete();
        }
    }

    @Test
    void unexpectedAndExtraCallsFailImmediatelyWithoutRecordingSensitivePaths(CapturedOutput output) {
        String canary = "synthetic-unexpected-path-canary";
        try (var simulator = new ClientApiOwnerSimulator()) {
            simulator.start();
            int status = HttpClient.create().baseUrl(simulator.uri().toString()).get().uri("/" + canary)
                .responseSingle((response, bytes) -> bytes.thenReturn(response.status().code())).block(Duration.ofSeconds(3));
            assertEquals(500, status);
            AssertionError failure = assertThrows(AssertionError.class, simulator::verifyComplete);
            assertFalse(failure.toString().contains(canary));
            assertEquals(0, simulator.journal().count());
            assertFalse(output.getAll().contains(canary));
            simulator.reset();
            simulator.scenarios().expect(HttpMethod.GET, "/client/known", Outcome.ACCEPTED);
            assertThrows(AssertionError.class, simulator::verifyComplete);
            simulator.reset();
            simulator.verifyComplete();
        }
    }

    private record RawResponse(int status, List<String> contentTypes, List<String> requestIds, byte[] body) {}

}
