package com.paytech.gateway.auto.idempotency;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.gateway.idempotency.IdempotencyKeyPolicy;
import com.paytech.gateway.idempotency.IdempotencyRequestDigest;
import com.paytech.gateway.test.ClientApiGatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore.Outcome;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore.Response;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;
import com.paytech.gateway.web.GatewayProtocol;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实 Gateway、本地 Redis 与 Owner Simulator 上的 Client API 请求幂等：校验、占位、记录、重放与释放。 */
class ClientApiIdempotencyAutoTest extends ClientApiGatewayAutoTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SCOPE = "orders.write";
    private static final String OTHER_CLIENT = "820000002";
    private static final String ROTATED_CREDENTIAL_ID = "830000002";
    private static final String ORDER_BODY = "{\"clientOrderId\":\"order-1\",\"amount\":\"10.00\"}";

    @BeforeEach
    void publishIdempotencySnapshot() {
        GatewayAutoTestRuntime.platformSnapshotSimulator()
            .publish(GatewaySnapshotFixture.clientApiIdempotencySnapshot());
        assertTrue(refreshSnapshot());
    }

    @Test
    void test_invalidParameter_missingDuplicateOrMalformedKeyIsRejectedWithoutRedisOrOwner() {
        credential().expectValid(SCOPE);
        assertProblem(post(GatewaySnapshotFixture.IDEMPOTENT_PATH, null, ORDER_BODY, null).exchange(), 400,
            "IDEMPOTENCY_KEY_REQUIRED");

        credential().expectValid(SCOPE);
        assertProblem(post(GatewaySnapshotFixture.IDEMPOTENT_PATH, null, ORDER_BODY, null)
            .header(IdempotencyKeyPolicy.HEADER_NAME, "key-one", "key-two").exchange(), 400,
            "INVALID_IDEMPOTENCY_KEY");

        for (String invalid : List.of("-leading", "has space", "a,b", "a/b", "k".repeat(256))) {
            credential().expectValid(SCOPE);
            assertProblem(post(GatewaySnapshotFixture.IDEMPOTENT_PATH, invalid, ORDER_BODY, null).exchange(), 400,
                "INVALID_IDEMPOTENCY_KEY");
        }

        assertEquals(0, owner().journal().count());
        assertTrue(redisTestCleaner.idempotencyKeys().isEmpty());
    }

    @Test
    void test_normalFlow_completedResponseIsReplayedWithNewRequestIdAndOwnerCalledOnce() {
        String key = "order-create-001";
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH,
            Response.json(201, "{\"checkoutId\":\"chk_1\",\"url\":\"https://checkout.test/t\"}")
                .withHeader(HttpHeaders.LOCATION, "/api/v1/checkouts/chk_1").withHeader(HttpHeaders.ETAG, "\"v1\""));

        credential().expectValid(SCOPE);
        var first = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, "idem-first-request");
        credential().expectValid(SCOPE);
        var replay = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, "idem-replay-request");

        assertEquals(201, first.getStatus().value());
        assertEquals(201, replay.getStatus().value());
        assertArrayEquals(first.getResponseBody(), replay.getResponseBody());
        for (String name : List.of(HttpHeaders.LOCATION, HttpHeaders.ETAG, HttpHeaders.CONTENT_TYPE)) {
            assertEquals(first.getResponseHeaders().get(name), replay.getResponseHeaders().get(name), name);
        }
        assertEquals("idem-first-request", first.getResponseHeaders().getFirst(GatewayProtocol.REQUEST_ID));
        assertEquals("idem-replay-request", replay.getResponseHeaders().getFirst(GatewayProtocol.REQUEST_ID));
        assertEquals("no-store", replay.getResponseHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals(1, owner().journal().count());
        var entry = owner().journal().entries().getFirst();
        assertFalse(entry.headerNames().contains("idempotency-key"), "REQUIRED 路由不再向 Owner 转发 Key");
        assertEquals(ORDER_BODY.length(), entry.bodyLength());
    }

    @Test
    void test_businessRuleViolation_sameKeyWithDifferentBodyPathOrRouteIsReused() {
        String key = "order-create-002";
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH, Response.json(200, "{\"accepted\":true}"));
        credential().expectValid(SCOPE);
        assertEquals(200, send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null).getStatus().value());

        credential().expectValid(SCOPE);
        assertConflict(send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY.replace("10.00", "10.0"), null),
            "IDEMPOTENCY_KEY_REUSED");
        credential().expectValid(SCOPE);
        assertConflict(send(GatewaySnapshotFixture.IDEMPOTENT_PATH + "?expand=items", key, ORDER_BODY, null),
            "IDEMPOTENCY_KEY_REUSED");
        credential().expectValid(SCOPE);
        assertConflict(send(itemCancelPath("item-1"), key, ORDER_BODY, null), "IDEMPOTENCY_KEY_REUSED");

        String cancelKey = "item-cancel-001";
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_ITEM_DOWNSTREAM_PREFIX + "item-1/cancel",
            Response.json(200, "{\"cancelled\":true}"));
        credential().expectValid(SCOPE);
        assertEquals(200, send(itemCancelPath("item-1"), cancelKey, "{}", null).getStatus().value());
        credential().expectValid(SCOPE);
        assertConflict(send(itemCancelPath("item-2"), cancelKey, "{}", null), "IDEMPOTENCY_KEY_REUSED");

        assertEquals(2, owner().journal().count());
    }

    @Test
    void test_businessRuleViolation_duplicateWhileFirstIsInProgressIsRejectedWithRetryAfter() {
        String key = "order-create-003";
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH,
            Response.json(200, "{\"accepted\":true}").withDelay(Duration.ofMillis(700)));
        credential().expectValid(SCOPE);
        credential().expectValid(SCOPE);
        credential().expectValid(SCOPE);

        CompletableFuture<EntityExchangeResult<byte[]>> first =
            CompletableFuture.supplyAsync(() -> send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null));
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> owner().journal().count() == 1);

        var duplicate = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null);
        assertConflict(duplicate, "IDEMPOTENCY_REQUEST_IN_PROGRESS");
        assertEquals(List.of("3"), duplicate.getResponseHeaders().getOrEmpty(HttpHeaders.RETRY_AFTER));

        assertEquals(200, first.join().getStatus().value());
        assertEquals(200, send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null).getStatus().value());
        assertEquals(1, owner().journal().count());
    }

    @ParameterizedTest
    @ValueSource(ints = {422, 500})
    void test_businessRuleViolation_ownerProblemIsRecordedAndReplayedWithCurrentRequestId(int status) {
        String key = "order-problem-" + status;
        String timestamp = "2026-10-17T01:02:03.123456Z";
        String problem = "{\"type\":\"about:blank\",\"title\":\"" + org.springframework.http.HttpStatus.valueOf(status)
            .getReasonPhrase() + "\",\"detail\":\"Owner rejected\",\"code\":\"OWNER_REJECTED\",\"requestId\":\""
            + ClientApiOwnerScenarioStore.REQUEST_ID_TOKEN + "\",\"timestamp\":\"" + timestamp + "\"}";
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH,
            Response.raw(status, problem.getBytes(StandardCharsets.UTF_8), ApiProblemProfile.CONTENT_TYPE)
                .withHeader(GatewayProtocol.REQUEST_ID, ClientApiOwnerScenarioStore.REQUEST_ID_TOKEN)
                .reflectingRequestId());

        credential().expectValid(SCOPE);
        var first = assertProblem(post(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, "idem-problem-first")
            .exchange(), status, "OWNER_REJECTED");
        credential().expectValid(SCOPE);
        var replay = assertProblem(post(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, "idem-problem-replay")
            .exchange(), status, "OWNER_REJECTED");

        JsonNode firstBody = JSON.readTree(first.getResponseBody());
        JsonNode replayBody = JSON.readTree(replay.getResponseBody());
        assertEquals("idem-problem-first", firstBody.get("requestId").stringValue());
        assertEquals("idem-problem-replay", replayBody.get("requestId").stringValue());
        assertEquals(timestamp, replayBody.get("timestamp").stringValue());
        assertEquals(firstBody.get("detail"), replayBody.get("detail"));
        assertEquals(1, owner().journal().count());
    }

    @ParameterizedTest
    @EnumSource(value = Outcome.class, names = {"TIMEOUT", "DISCONNECT"})
    void test_edgeCase_noOwnerResponseReleasesKeyForTheSameKeyRetry(Outcome failure) {
        String key = "order-release-" + failure.name().toLowerCase();
        owner().scenarios().expect(HttpMethod.POST, GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH, failure);
        credential().expectValid(SCOPE);
        var failed = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null);
        assertTrue(failed.getStatus().is5xxServerError(), failed.getStatus().toString());
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> redisTestCleaner.idempotencyKeys().isEmpty());

        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH, Response.json(200, "{\"accepted\":true}"));
        credential().expectValid(SCOPE);
        assertEquals(200, send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null).getStatus().value());
        credential().expectValid(SCOPE);
        assertEquals(200, send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null).getStatus().value());
        assertEquals(2, owner().journal().count());
    }

    @Test
    void test_edgeCase_responseAboveRecordLimitIsReturnedButNotRecorded() {
        String key = "order-large-001";
        String large = "{\"data\":\"" + "x".repeat(70_000) + "\"}";
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH, Response.json(200, large));
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH, Response.json(200, large));

        credential().expectValid(SCOPE);
        var first = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null);
        assertEquals(200, first.getStatus().value());
        assertEquals(large, new String(first.getResponseBody(), StandardCharsets.UTF_8));
        assertTrue(redisTestCleaner.idempotencyKeys().isEmpty());

        credential().expectValid(SCOPE);
        assertEquals(200, send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null).getStatus().value());
        assertEquals(2, owner().journal().count());
    }

    @Test
    void test_edgeCase_differentClientsWithSameKeyAreIndependent() {
        String key = "shared-client-key";
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH, Response.json(200, "{\"client\":\"a\"}"));
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH, Response.json(200, "{\"client\":\"b\"}"));

        credential().expectValid(SCOPE);
        var clientA = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null);
        credential().expectValidIdentity(OTHER_CLIENT, ROTATED_CREDENTIAL_ID, SCOPE);
        var clientB = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null);
        credential().expectValid(SCOPE);
        var clientAReplay = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null);

        assertEquals("{\"client\":\"a\"}", new String(clientA.getResponseBody(), StandardCharsets.UTF_8));
        assertEquals("{\"client\":\"b\"}", new String(clientB.getResponseBody(), StandardCharsets.UTF_8));
        assertArrayEquals(clientA.getResponseBody(), clientAReplay.getResponseBody());
        assertEquals(2, owner().journal().count());
        assertEquals(2, redisTestCleaner.idempotencyKeys().size());
    }

    @Test
    void test_edgeCase_rotatedCredentialOfSameClientReplays() {
        String key = "order-rotation-001";
        expectOwner(GatewaySnapshotFixture.IDEMPOTENT_DOWNSTREAM_PATH, Response.json(200, "{\"accepted\":true}"));
        credential().expectValid(SCOPE);
        var first = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null);
        credential().expectValidIdentity(PlatformCredentialValidationSimulator.CLIENT, ROTATED_CREDENTIAL_ID, SCOPE);
        var rotated = send(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null);

        assertEquals(200, rotated.getStatus().value());
        assertArrayEquals(first.getResponseBody(), rotated.getResponseBody());
        assertEquals(1, owner().journal().count());
    }

    @Test
    void test_edgeCase_noneRouteForwardsKeyAndNeverRecords() {
        String key = "none-route-key";
        expectOwner(GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH, Response.json(200, "{\"accepted\":true}"));
        expectOwner(GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH, Response.json(200, "{\"accepted\":true}"));
        for (int attempt = 0; attempt < 2; attempt++) {
            credential().expectValid(SCOPE);
            assertEquals(200, send(GatewaySnapshotFixture.CLIENT_API_PATH, key, ORDER_BODY, null).getStatus().value());
        }

        assertEquals(2, owner().journal().count());
        owner().journal().entries()
            .forEach(entry -> assertEquals(List.of(key), entry.headers().get("idempotency-key")));
        assertTrue(redisTestCleaner.idempotencyKeys().isEmpty());
    }

    @Test
    void test_edgeCase_redisCommandFailureFailsClosedWithoutCallingOwner() {
        String key = "order-redis-failure";
        redisTestCleaner.occupyIdempotencyKeyWithString(GatewayAutoTestRuntime.redisNamespace().value() + ":idem:v1:"
            + GatewayRouteChannel.CLIENT_API.getCode() + ":" + PlatformCredentialValidationSimulator.CLIENT + ":"
            + new IdempotencyRequestDigest().keyHash(key));

        credential().expectValid(SCOPE);
        assertProblem(post(GatewaySnapshotFixture.IDEMPOTENT_PATH, key, ORDER_BODY, null).exchange(), 503,
            "DEPENDENCY_UNAVAILABLE");
        assertEquals(0, owner().journal().count());
    }

    @Test
    void test_edgeCase_digestSortsQueryByNameAndKeepsSameNameOrderAndRawEncoding() {
        IdempotencyRequestDigest digest = new IdempotencyRequestDigest();
        byte[] body = ORDER_BODY.getBytes(StandardCharsets.UTF_8);
        String base = digest.digest("POST", "route", "/p", "b=2&a=1&a=0", body);

        assertEquals(base, digest.digest("POST", "route", "/p", "a=1&b=2&a=0", body));
        assertNotEquals(base, digest.digest("POST", "route", "/p", "a=0&a=1&b=2", body));
        assertNotEquals(base, digest.digest("POST", "route", "/p", "b=%32&a=1&a=0", body));
        assertNotEquals(base, digest.digest("POST", "other-route", "/p", "b=2&a=1&a=0", body));
        assertNotEquals(base, digest.digest("POST", "route", "/p", "b=2&a=1&a=0", "{}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(digest.digest("POST", "route", "/p", null, body), digest.digest("POST", "route", "/p", "", body));
    }

    private EntityExchangeResult<byte[]> send(String path, String key, String body, String requestId) {
        return post(path, key, body, requestId).exchange().expectBody().returnResult();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.RequestHeadersSpec<?> post(String path,
        String key, String body, String requestId) {
        var request = client().post().uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + PlatformCredentialValidationSimulator.CREDENTIAL)
            .contentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            request.header(IdempotencyKeyPolicy.HEADER_NAME, key);
        }
        if (requestId != null) {
            request.header(GatewayProtocol.REQUEST_ID, requestId);
        }
        return request.bodyValue(body);
    }

    private void assertConflict(EntityExchangeResult<byte[]> result, String code) {
        assertProblem(result, 409, code);
    }

    private void expectOwner(String downstreamPath, Response response) {
        owner().scenarios().expect(HttpMethod.POST, downstreamPath, response);
    }

    private static String itemCancelPath(String itemId) {
        return GatewaySnapshotFixture.IDEMPOTENT_ITEM_PATH_PREFIX + itemId + "/cancel";
    }

    private static com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerSimulator owner() {
        return GatewayAutoTestRuntime.clientApiOwnerSimulator();
    }

    private static PlatformCredentialValidationSimulator credential() {
        return GatewayAutoTestRuntime.credentialSimulator();
    }
}
