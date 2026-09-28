package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.api.decision.DecisionReleaseManagementModels.TestReleaseDetailResponseBody;
import com.paytech.risk.api.decision.DecisionReleaseManagementModels.ValidateTestReleaseResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.CreateRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleSetDetailResponseBody;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import com.paytech.risk.test.decision.DecisionReleaseTestFixture;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.OwnedCommandIdentity;
import com.paytech.risk.test.infrastructure.RiskSqlBarrier;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 以真实 HTTP/MySQL 查询屏障验证多 SQL 管理查询只观察一个提交快照。 */
@Import(DecisionConfigurationReadSnapshotAutoTest.TestSupport.class)
class DecisionConfigurationReadSnapshotAutoTest extends RiskServiceAutoTestBase {

    private static final String ACTOR = "910000000000000001";
    private static final String CALLER_SCOPE = "OPERATIONS_ACCOUNT:" + ACTOR;
    private static final String SESSION = "risk-auto-read-snapshot-session";
    private static final Duration BARRIER_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private RiskSqlBarrier sqlBarrier;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DecisionIdentifierGenerator identifiers;

    @Autowired
    private ValidationExecutionBarrier validationBarrier;

    @Test
    void test_freezeCommitBetweenRuleSetDetailQueriesReturnsOneOldSnapshot() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        String createKey = decisionTestMapper.uniqueNamespace();
        String freezeKey = decisionTestMapper.uniqueNamespace();
        List<OwnedCommandIdentity> commands = List.of(
            command("CREATE_RULE_SET_VERSION", createKey),
            command("FREEZE_RULE_SET_VERSION", freezeKey));
        try (RiskDecisionHttpClient reader = new RiskDecisionHttpClient(port);
             RiskDecisionHttpClient writer = new RiskDecisionHttpClient(port);
             var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            CreateRuleSetVersionResponseBody created = create(writer, code, createKey);
            String detailPath = "/decision-rule-sets/v1/rule-sets/" + created.ruleSetVersionId();
            try (var gate = sqlBarrier.pauseAfter(".selectDecisionPointById", 1)) {
                Future<HttpResponse<String>> detail = executor.submit(() -> reader.get(detailPath, ACTOR, SESSION));
                assertTrue(gate.awaitReached(BARRIER_TIMEOUT), "Rule Set 详情未到达 Root/Companion 读取窗口");

                HttpResponse<String> frozen = writer.post(detailPath + "/freeze", ACTOR, SESSION, freezeKey,
                    new FreezeRuleSetVersionRequestBody(created.contentHash()));
                assertEquals(200, frozen.statusCode(), frozen.body());
                gate.release();

                HttpResponse<String> response = detail.get(5, TimeUnit.SECONDS);
                assertEquals(200, response.statusCode(), response.body());
                assertEquals("DRAFT", reader.body(response, RuleSetDetailResponseBody.class).status());
                assertReadSnapshot(gate);
            }

            HttpResponse<String> current = reader.get(detailPath, ACTOR, SESSION);
            assertEquals("FROZEN", reader.body(current, RuleSetDetailResponseBody.class).status());
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), commands);
        }
    }

    @Test
    void test_freezeCommitBetweenRuleSetListQueriesReturnsOneOldSnapshot() throws Exception {
        String code = decisionTestMapper.uniqueRuleSetCode();
        String createKey = decisionTestMapper.uniqueNamespace();
        String freezeKey = decisionTestMapper.uniqueNamespace();
        List<OwnedCommandIdentity> commands = List.of(
            command("CREATE_RULE_SET_VERSION", createKey),
            command("FREEZE_RULE_SET_VERSION", freezeKey));
        try (RiskDecisionHttpClient reader = new RiskDecisionHttpClient(port);
             RiskDecisionHttpClient writer = new RiskDecisionHttpClient(port);
             var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            CreateRuleSetVersionResponseBody created = create(writer, code, createKey);
            String listPath = "/decision-rule-sets/v1/rule-sets?page=1&size=10"
                + "&pointCode=CHECKOUT_PREPARE&ruleSetCode=" + code + "&status=DRAFT";
            String detailPath = "/decision-rule-sets/v1/rule-sets/" + created.ruleSetVersionId();
            try (var gate = sqlBarrier.pauseAfter(".selectRuleSetVersions", 1)) {
                Future<HttpResponse<String>> page = executor.submit(() -> reader.get(listPath, ACTOR, SESSION));
                assertTrue(gate.awaitReached(BARRIER_TIMEOUT), "Rule Set 列表未到达 Rows/Count 读取窗口");

                HttpResponse<String> frozen = writer.post(detailPath + "/freeze", ACTOR, SESSION, freezeKey,
                    new FreezeRuleSetVersionRequestBody(created.contentHash()));
                assertEquals(200, frozen.statusCode(), frozen.body());
                gate.release();

                HttpResponse<String> response = page.get(5, TimeUnit.SECONDS);
                assertEquals(200, response.statusCode(), response.body());
                var json = reader.json(response);
                assertEquals(1, json.get("total").longValue());
                assertEquals(created.ruleSetVersionId(),
                    json.get("items").get(0).get("ruleSetVersionId").textValue());
                assertEquals("DRAFT", json.get("items").get(0).get("status").textValue());
                assertReadSnapshot(gate);
            }

            HttpResponse<String> current = reader.get(listPath, ACTOR, SESSION);
            assertEquals(0, reader.json(current).get("total").longValue());
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(code), commands);
        }
    }

    @Test
    void test_validateCommitBetweenReleaseDetailQueriesReturnsOneOldSnapshot() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture();
             RiskDecisionHttpClient reader = new RiskDecisionHttpClient(port);
             var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String validateKey = fixture.nextKey();
            String detailPath = "/decision-releases/v1/candidates/" + candidate.created().releaseId();
            try (var gate = sqlBarrier.pauseBefore(".selectReleaseAuditEvents", 1)) {
                Future<HttpResponse<String>> detail = executor.submit(() -> reader.get(detailPath, ACTOR, SESSION));
                assertTrue(gate.awaitReached(BARRIER_TIMEOUT), "Candidate 详情未到达 Root/Audit 读取窗口");

                HttpResponse<String> validated = fixture.postValidate(candidate, ACTOR, validateKey, 1);
                assertEquals(200, validated.statusCode(), validated.body());
                gate.release();

                HttpResponse<String> response = detail.get(5, TimeUnit.SECONDS);
                assertEquals(200, response.statusCode(), response.body());
                TestReleaseDetailResponseBody old = fixture.body(response, TestReleaseDetailResponseBody.class);
                assertEquals("DRAFT", old.status());
                assertEquals(1, old.stateChanges().size());
                assertTrue(old.staticChecks().isEmpty());
                assertReadSnapshot(gate);
            }

            TestReleaseDetailResponseBody current = fixture.body(fixture.getCandidate(candidate),
                TestReleaseDetailResponseBody.class);
            assertEquals("VALIDATING", current.status());
            assertEquals(8, current.staticChecks().size());
        }
    }

    @Test
    void test_validateCommitBetweenReleaseListQueriesReturnsOneOldSnapshot() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture();
             RiskDecisionHttpClient reader = new RiskDecisionHttpClient(port);
             var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String validateKey = fixture.nextKey();
            String listPath = "/decision-releases/v1/candidates?page=1&size=10"
                + "&pointCode=CHECKOUT_PREPARE&status=DRAFT";
            try (var gate = sqlBarrier.pauseAfter(".selectReleases", 1)) {
                Future<HttpResponse<String>> page = executor.submit(() -> reader.get(listPath, ACTOR, SESSION));
                assertTrue(gate.awaitReached(BARRIER_TIMEOUT), "Candidate 列表未到达 Rows/Count 读取窗口");

                HttpResponse<String> validated = fixture.postValidate(candidate, ACTOR, validateKey, 1);
                assertEquals(200, validated.statusCode(), validated.body());
                gate.release();

                HttpResponse<String> response = page.get(5, TimeUnit.SECONDS);
                assertEquals(200, response.statusCode(), response.body());
                var json = fixture.json(response);
                assertEquals(1, json.get("total").longValue());
                assertEquals(candidate.created().releaseId(), json.get("items").get(0).get("releaseId").textValue());
                assertEquals("DRAFT", json.get("items").get(0).get("status").textValue());
                assertReadSnapshot(gate);
            }
        }
    }

    @Test
    void test_authoringContextReturnsPointAndCatalogFromOneDatabaseSnapshot() throws Exception {
        long actionVersionId = 240001L;
        String originalStatus = actionStatus(actionVersionId);
        assertEquals("ACTIVE", originalStatus, "测试目录必须从 ACTIVE 状态开始");
        boolean catalogChanged = false;
        try {
            try (RiskDecisionHttpClient reader = new RiskDecisionHttpClient(port);
                 var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
                 var gate = sqlBarrier.pauseAfter(".selectDecisionPointByCode", 1)) {
                Future<HttpResponse<String>> response = executor.submit(() -> reader.get(
                    "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE", ACTOR, SESSION));
                assertTrue(gate.awaitReached(BARRIER_TIMEOUT), "Authoring Context 未读取 Decision Point");

                int updated = jdbc.update("UPDATE t_action_version SET status='RETIRED' "
                    + "WHERE action_version_id=? AND status='ACTIVE'", actionVersionId);
                catalogChanged = updated == 1;
                assertEquals(1, updated, "测试必须真实提交 Action Catalog 状态变化");
                assertEquals("RETIRED", actionStatus(actionVersionId));
                gate.release();

                HttpResponse<String> observed = response.get(5, TimeUnit.SECONDS);
                assertEquals(200, observed.statusCode(), observed.body());
                assertReadSnapshot(gate);
                var context = reader.json(observed);
                assertEquals("CHECKOUT_PREPARE", context.get("point").get("pointCode").textValue());
                assertEquals(5, context.get("actions").size());
                assertEquals("ADD_RISK_TAG", context.get("actions").get(0).get("actionCode").textValue());
            }
        } finally {
            if (catalogChanged) {
                assertEquals(1, jdbc.update("UPDATE t_action_version SET status=? "
                    + "WHERE action_version_id=? AND status='RETIRED'", originalStatus, actionVersionId),
                    "测试必须精确恢复 Action Catalog 状态");
            }
            assertEquals(originalStatus, actionStatus(actionVersionId), "Action Catalog 状态恢复失败");
        }
    }

    @Test
    void test_validateLoadsFrozenClosureInShortReadOnlyRepeatableReadSnapshot() throws Exception {
        try (DecisionReleaseTestFixture fixture = fixture();
             var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var candidate = fixture.arrangeFrozenCandidate();
            String validateKey = fixture.nextKey();
            try (var validationGate = validationBarrier.pauseNext()) {
                Future<HttpResponse<String>> response;
                RiskSqlBarrier.TransactionObservation closureTransaction;
                try (var closureGate = sqlBarrier.pauseAfter(".selectRelease", 3)) {
                    response = executor.submit(() -> fixture.postValidate(candidate, ACTOR, validateKey, 1));
                    assertTrue(closureGate.awaitReached(BARRIER_TIMEOUT),
                        "Validate 未进入事务 A 后的冻结闭包装载");
                    assertReadSnapshot(closureGate);
                    closureTransaction = closureGate.observation();
                    assertTrue(closureTransaction.transactionId() != null, "闭包读事务必须具有可区分标识");
                    closureGate.release();
                    assertTrue(validationGate.awaitReached(BARRIER_TIMEOUT),
                        "Validate 未到达纯 staticValidator.validate 执行点");
                }

                RiskSqlBarrier.TransactionObservation validation = validationGate.observation();
                assertFalse(validation.active(), "staticValidator.validate 必须在事务外执行: " + validation);
                assertNull(validation.transactionId(), "事务外纯校验不得继承闭包读事务标识");

                try (var completionGate = sqlBarrier.pauseBefore(".upsertClaimCandidate", 1)) {
                    validationGate.release();
                    assertTrue(completionGate.awaitReached(BARRIER_TIMEOUT),
                        "Validate 未进入事务 B 的首个幂等 SQL");
                    RiskSqlBarrier.TransactionObservation completion = completionGate.observation();
                    assertTrue(completion.active(), "事务 B 必须是实际写事务: " + completion);
                    assertFalse(completion.readOnly(), "事务 B 不得继承 read-only 属性: " + completion);
                    assertNotEquals(closureTransaction.transactionId(), completion.transactionId(),
                        "事务 B 必须与冻结闭包读事务明确分离");
                    completionGate.release();

                    HttpResponse<String> validated = response.get(5, TimeUnit.SECONDS);
                    assertEquals(200, validated.statusCode(), validated.body());
                    assertEquals("VALIDATING", fixture.body(validated,
                        ValidateTestReleaseResponseBody.class).status());
                }
            }
        }
    }

    private CreateRuleSetVersionResponseBody create(
        RiskDecisionHttpClient http,
        String code,
        String key
    ) throws Exception {
        new com.paytech.risk.test.decision.LegacyRuleSetTestMapper(jdbc,
            new org.springframework.transaction.support.TransactionTemplate(transactionManager), identifiers)
            .insertFirstVersion(code, Long.parseLong(ACTOR), key);
        HttpResponse<String> response = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key,
            DecisionRuleSetRequestBuilder.firstVersion(code));
        assertEquals(200, response.statusCode(), response.body());
        return http.body(response, CreateRuleSetVersionResponseBody.class);
    }

    private DecisionReleaseTestFixture fixture() {
        return new DecisionReleaseTestFixture(port, decisionTestMapper, jdbc, transactionManager, identifiers);
    }

    private String actionStatus(long actionVersionId) {
        return jdbc.queryForObject("SELECT status FROM t_action_version WHERE action_version_id=?",
            String.class, actionVersionId);
    }

    private static OwnedCommandIdentity command(String operationType, String idempotencyKey) {
        return new OwnedCommandIdentity(CALLER_SCOPE, operationType, idempotencyKey);
    }

    private static void assertReadSnapshot(RiskSqlBarrier.Gate gate) {
        assertTrue(gate.observation().isReadOnlyRepeatableRead(),
            "查询必须位于显式 read-only REPEATABLE_READ 快照，实际=" + gate.observation());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupport {

        @Bean
        RiskSqlBarrier riskSqlBarrier() {
            return new RiskSqlBarrier();
        }

        @Bean
        ValidationExecutionBarrier validationExecutionBarrier() {
            return new ValidationExecutionBarrier();
        }

        @Bean
        @Primary
        DecisionReleaseStaticValidator observingDecisionReleaseStaticValidator(
            @Qualifier("decisionReleaseStaticValidator") DecisionReleaseStaticValidator delegate,
            ValidationExecutionBarrier barrier
        ) {
            return closure -> barrier.observe(() -> delegate.validate(closure));
        }
    }

    static final class ValidationExecutionBarrier {

        private static final Duration SAFETY_TIMEOUT = Duration.ofSeconds(15);

        private final AtomicReference<Gate> active = new AtomicReference<>();

        Gate pauseNext() {
            Gate gate = new Gate(active);
            if (!active.compareAndSet(null, gate)) {
                throw new IllegalStateException("已有 staticValidator 测试屏障尚未释放");
            }
            return gate;
        }

        <T> T observe(Supplier<T> operation) {
            Gate gate = active.get();
            if (gate == null || !gate.claim()) {
                return operation.get();
            }
            return gate.observe(operation);
        }

        static final class Gate implements AutoCloseable {

            private final AtomicReference<Gate> owner;
            private final AtomicBoolean claimed = new AtomicBoolean();
            private final CountDownLatch reached = new CountDownLatch(1);
            private final CountDownLatch release = new CountDownLatch(1);
            private final AtomicReference<RiskSqlBarrier.TransactionObservation> observation =
                new AtomicReference<>();

            private Gate(AtomicReference<Gate> owner) {
                this.owner = owner;
            }

            boolean awaitReached(Duration timeout) throws InterruptedException {
                return reached.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
            }

            RiskSqlBarrier.TransactionObservation observation() {
                RiskSqlBarrier.TransactionObservation value = observation.get();
                if (value == null) {
                    throw new IllegalStateException("staticValidator 测试屏障尚未到达");
                }
                return value;
            }

            void release() {
                release.countDown();
            }

            private boolean claim() {
                return claimed.compareAndSet(false, true);
            }

            private <T> T observe(Supplier<T> operation) {
                observation.set(RiskSqlBarrier.observeCurrentTransaction());
                reached.countDown();
                try {
                    if (!release.await(SAFETY_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)) {
                        throw new IllegalStateException("staticValidator 测试屏障等待释放超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("staticValidator 测试屏障被中断", exception);
                }
                return operation.get();
            }

            @Override
            public void close() {
                release();
                owner.compareAndSet(this, null);
            }
        }
    }
}
