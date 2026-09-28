package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.api.decision.DecisionReleaseManagementModels.CreateTestReleaseRequestBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.CreateTestReleaseResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.CreateRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.CreateRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.OwnedCommandIdentity;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.test.decision.LegacyRuleSetTestMapper;

/** 以真实 HTTP、Processor、事务、MyBatis 和本地 MySQL 验证配置命令的并发边界。 */
class DecisionConfigurationConcurrencyAutoTest extends RiskServiceAutoTestBase {

    private static final String ACTOR = "910000000000000001";
    private static final String CALLER_SCOPE = "OPERATIONS_ACCOUNT:" + ACTOR;
    private static final String SESSION = "risk-auto-concurrency-session";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DecisionIdentifierGenerator identifiers;

    @Test
    void test_differentRuleSetCodesAllocateDistinctRuleVersionsWithoutLegacyComposition() throws Exception {
        String firstCode = decisionTestMapper.uniqueRuleSetCode();
        String secondCode = decisionTestMapper.uniqueRuleSetCode();
        String firstKey = decisionTestMapper.uniqueNamespace();
        String secondKey = decisionTestMapper.uniqueNamespace();
        List<OwnedCommandIdentity> commands = List.of(command("CREATE_RULE_SET_VERSION", firstKey),
            command("CREATE_RULE_SET_VERSION", secondKey));
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            List<HttpResponse<String>> responses = whilePointLocked(() -> List.of(
                () -> http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, firstKey,
                    DecisionRuleSetRequestBuilder.currentFirstVersion(firstCode)),
                () -> http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, secondKey,
                    DecisionRuleSetRequestBuilder.currentFirstVersion(secondCode))));

            assertEquals(List.of(200, 200), statuses(responses), bodies(responses));
            CreateRuleSetVersionResponseBody first = http.body(responses.get(0),
                CreateRuleSetVersionResponseBody.class);
            CreateRuleSetVersionResponseBody second = http.body(responses.get(1),
                CreateRuleSetVersionResponseBody.class);
            assertNotEquals(first.ruleSetVersionId(), second.ruleSetVersionId());
            assertNull(first.pointVersionId());
            assertNull(second.pointVersionId());
            assertEquals(1, decisionTestMapper.snapshotCounts(firstCode).ruleSetVersions());
            assertEquals(1, decisionTestMapper.snapshotCounts(secondCode).ruleSetVersions());
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(firstCode, secondCode), commands);
        }
    }

    @Test
    void test_sameRuleSetCodeAllowsOnlyOneFirstVersion() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        String firstKey = decisionTestMapper.uniqueNamespace();
        String secondKey = decisionTestMapper.uniqueNamespace();
        List<OwnedCommandIdentity> commands = List.of(command("CREATE_RULE_SET_VERSION", firstKey),
            command("CREATE_RULE_SET_VERSION", secondKey));
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            CreateRuleSetVersionRequestBody body = DecisionRuleSetRequestBuilder.currentFirstVersion(code);
            List<HttpResponse<String>> responses = whilePointLocked(() -> List.of(
                () -> http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, firstKey, body),
                () -> http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, secondKey, body)));

            assertEquals(List.of(200, 409), sortedStatuses(responses), bodies(responses));
            responses.stream().filter(response -> response.statusCode() == 409)
                .forEach(response -> RiskDecisionHttpClient.assertProblem(response, 409, "CONCURRENT_REQUEST"));
            assertEquals(1, decisionTestMapper.snapshotCounts(code).ruleSetVersions());
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), commands);
        }
    }

    @Test
    void test_sameFrozenRuleSetAllowsTwoCandidatesWithDistinctReleaseNumbers() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        String createKey = decisionTestMapper.uniqueNamespace();
        String freezeKey = decisionTestMapper.uniqueNamespace();
        String firstCandidateKey = decisionTestMapper.uniqueNamespace();
        String secondCandidateKey = decisionTestMapper.uniqueNamespace();
        List<OwnedCommandIdentity> commands = List.of(command("CREATE_RULE_SET_VERSION", createKey),
            command("FREEZE_RULE_SET_VERSION", freezeKey),
            command("CREATE_TEST_DECISION_RELEASE", firstCandidateKey),
            command("CREATE_TEST_DECISION_RELEASE", secondCandidateKey));
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            new LegacyRuleSetTestMapper(jdbc, new TransactionTemplate(transactionManager), identifiers)
                .insertFirstVersion(code, Long.parseLong(ACTOR), createKey);
            CreateRuleSetVersionResponseBody created = create(http, code, createKey);
            freeze(http, created, freezeKey);
            var candidateBody = new CreateTestReleaseRequestBody(
                created.ruleSetVersionId(), created.contentHash());
            List<HttpResponse<String>> responses = whilePointLocked(() -> List.of(
                () -> http.post("/decision-releases/v1/candidates", ACTOR, SESSION, firstCandidateKey,
                    candidateBody),
                () -> http.post("/decision-releases/v1/candidates", ACTOR, SESSION, secondCandidateKey,
                    candidateBody)));

            assertEquals(List.of(200, 200), statuses(responses), bodies(responses));
            CreateTestReleaseResponseBody first = http.body(responses.get(0),
                CreateTestReleaseResponseBody.class);
            CreateTestReleaseResponseBody second = http.body(responses.get(1),
                CreateTestReleaseResponseBody.class);
            assertNotEquals(first.releaseId(), second.releaseId());
            assertNotEquals(first.releaseNo(), second.releaseNo());
            assertEquals(2, decisionTestMapper.releaseCount(code));
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), commands);
        }
    }

    @Test
    void test_sameCommandReplaysButDifferentHashConflictsAndCaseVariantIsDistinct() throws Exception {
        String firstCode = decisionTestMapper.uniqueRuleSetCode();
        String changedCode = decisionTestMapper.uniqueRuleSetCode();
        String generatedKey = decisionTestMapper.uniqueNamespace();
        String suffix = generatedKey.substring("risk-auto-".length() + 1);
        String baseKey = "risk-auto-a" + suffix;
        String caseVariantKey = "risk-auto-A" + suffix;
        List<OwnedCommandIdentity> commands = List.of(command("CREATE_RULE_SET_VERSION", baseKey),
            command("CREATE_RULE_SET_VERSION", caseVariantKey));
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            CreateRuleSetVersionRequestBody body = DecisionRuleSetRequestBuilder.currentFirstVersion(firstCode);
            List<HttpResponse<String>> sameKey = concurrently(() -> List.of(
                () -> http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, baseKey, body),
                () -> http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, baseKey, body)));
            assertEquals(List.of(200, 200), statuses(sameKey), bodies(sameKey));
            assertEquals(sameKey.get(0).body(), sameKey.get(1).body());

            HttpResponse<String> conflict = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                baseKey, DecisionRuleSetRequestBuilder.currentFirstVersion(changedCode));
            RiskDecisionHttpClient.assertProblem(conflict, 409, "CONCURRENT_REQUEST");

            HttpResponse<String> caseVariant = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                caseVariantKey, DecisionRuleSetRequestBuilder.currentFirstVersion(changedCode));
            assertEquals(200, caseVariant.statusCode(), caseVariant.body());
            assertEquals(1, decisionTestMapper.ownedCommandCount(
                CALLER_SCOPE, "CREATE_RULE_SET_VERSION", baseKey));
            assertEquals(1, decisionTestMapper.ownedCommandCount(
                CALLER_SCOPE, "CREATE_RULE_SET_VERSION", caseVariantKey));
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(firstCode, changedCode), commands);
        }
    }

    @Test
    void test_sameDraftFreezeAllowsOneTransitionAndWinnerReplay() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        String createKey = decisionTestMapper.uniqueNamespace();
        String firstFreezeKey = decisionTestMapper.uniqueNamespace();
        String secondFreezeKey = decisionTestMapper.uniqueNamespace();
        List<OwnedCommandIdentity> commands = List.of(command("CREATE_RULE_SET_VERSION", createKey),
            command("FREEZE_RULE_SET_VERSION", firstFreezeKey),
            command("FREEZE_RULE_SET_VERSION", secondFreezeKey));
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            new LegacyRuleSetTestMapper(jdbc, new TransactionTemplate(transactionManager), identifiers)
                .insertFirstVersion(code, Long.parseLong(ACTOR), createKey);
            CreateRuleSetVersionResponseBody created = create(http, code, createKey);
            FreezeRuleSetVersionRequestBody body = new FreezeRuleSetVersionRequestBody(created.contentHash());
            String path = "/decision-rule-sets/v1/rule-sets/" + created.ruleSetVersionId() + "/freeze";
            List<HttpResponse<String>> responses = whilePointLocked(() -> List.of(
                () -> http.post(path, ACTOR, SESSION, firstFreezeKey, body),
                () -> http.post(path, ACTOR, SESSION, secondFreezeKey, body)));

            assertEquals(List.of(200, 400), sortedStatuses(responses), bodies(responses));
            responses.stream().filter(response -> response.statusCode() == 400)
                .forEach(response -> RiskDecisionHttpClient.assertProblem(response, 400, "BUSINESS_CHECK_FAILED"));
            HttpResponse<String> winner = responses.stream().filter(response -> response.statusCode() == 200)
                .findFirst().orElseThrow();
            String winnerKey = responses.get(0).statusCode() == 200 ? firstFreezeKey : secondFreezeKey;
            HttpResponse<String> replay = http.post(path, ACTOR, SESSION, winnerKey, body);
            assertEquals(200, replay.statusCode(), replay.body());
            assertEquals(winner.body(), replay.body());
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), commands);
        }
    }

    private CreateRuleSetVersionResponseBody create(
        RiskDecisionHttpClient http,
        String code,
        String key
    ) throws Exception {
        HttpResponse<String> response = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key,
            DecisionRuleSetRequestBuilder.firstVersion(code));
        assertEquals(200, response.statusCode(), response.body());
        return http.body(response, CreateRuleSetVersionResponseBody.class);
    }

    private void freeze(RiskDecisionHttpClient http, CreateRuleSetVersionResponseBody created, String key)
        throws Exception {
        HttpResponse<String> response = http.post(
            "/decision-rule-sets/v1/rule-sets/" + created.ruleSetVersionId() + "/freeze",
            ACTOR, SESSION, key, new FreezeRuleSetVersionRequestBody(created.contentHash()));
        assertEquals(200, response.statusCode(), response.body());
    }

    private List<HttpResponse<String>> whilePointLocked(ConcurrentCalls calls) throws Exception {
        var pointLock = decisionTestMapper.lockDecisionPoint("CHECKOUT_PREPARE");
        RunningCalls running = start(calls.get());
        try {
            decisionTestMapper.awaitActiveConnectionsAtLeast(2, Duration.ofSeconds(2));
            // 同一规则集的第二个请求在 Redis 等待，不能要求它先打开数据库事务。
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (Exception failure) {
            running.cancel();
            throw failure;
        } finally {
            pointLock.close();
        }
        return running.responses();
    }

    private List<HttpResponse<String>> concurrently(ConcurrentCalls calls) throws Exception {
        RunningCalls running = start(calls.get());
        return running.responses();
    }

    private RunningCalls start(List<HttpCall> calls) {
        var executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (HttpCall call : calls) {
            futures.add(executor.submit(() -> {
                start.await();
                return call.execute();
            }));
        }
        start.countDown();
        return new RunningCalls(executor, futures);
    }

    private static List<Integer> statuses(List<HttpResponse<String>> responses) {
        return responses.stream().map(HttpResponse::statusCode).toList();
    }

    private static List<Integer> sortedStatuses(List<HttpResponse<String>> responses) {
        return responses.stream().map(HttpResponse::statusCode).sorted().toList();
    }

    private static String bodies(List<HttpResponse<String>> responses) {
        return responses.stream().map(HttpResponse::body).toList().toString();
    }

    private static OwnedCommandIdentity command(String operationType, String key) {
        return new OwnedCommandIdentity(CALLER_SCOPE, operationType, key);
    }

    @FunctionalInterface
    private interface HttpCall {
        HttpResponse<String> execute() throws Exception;
    }

    @FunctionalInterface
    private interface ConcurrentCalls {
        List<HttpCall> get();
    }

    private static final class RunningCalls {

        private final java.util.concurrent.ExecutorService executor;
        private final List<Future<HttpResponse<String>>> futures;

        private RunningCalls(
            java.util.concurrent.ExecutorService executor,
            List<Future<HttpResponse<String>>> futures
        ) {
            this.executor = executor;
            this.futures = futures;
        }

        private List<HttpResponse<String>> responses() throws Exception {
            try {
                List<HttpResponse<String>> responses = new ArrayList<>();
                for (Future<HttpResponse<String>> future : futures) {
                    responses.add(future.get(15, TimeUnit.SECONDS));
                }
                return responses;
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "并发 HTTP 线程未终止");
            }
        }

        private void cancel() {
            futures.forEach(future -> future.cancel(true));
            executor.shutdownNow();
        }
    }
}
