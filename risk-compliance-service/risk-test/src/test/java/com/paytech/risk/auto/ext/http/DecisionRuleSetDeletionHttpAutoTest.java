package com.paytech.risk.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.paytech.risk.test.decision.DecisionReleaseTestFixture.ACTOR;
import static com.paytech.risk.test.decision.DecisionReleaseTestFixture.SESSION;
import static com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem;

import com.paytech.risk.api.decision.request.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import com.paytech.risk.test.decision.DecisionReleaseTestFixture;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;

/** 整体删除通过真实 HTTP、Redis、事务和 MySQL，覆盖旧版使用与不可变历史。 */
class DecisionRuleSetDeletionHttpAutoTest extends RiskServiceAutoTestBase {
    private static final String RULES = "/decision-rule-sets/v1/rule-sets";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private org.redisson.api.RedissonClient redis;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DecisionIdentifierGenerator identifiers;

    /** 多版本合为一行，删除只改变管理根并保留冻结历史；已有 Candidate 仍可读取和完成校验。 */
    @Test
    void test_deleteWholeSetKeepsFrozenHistoryAndRejectsRepeatedDelete() throws Exception {
        try (var f = fixture(); var http = new RiskDecisionHttpClient(port)) {
            var candidate = f.arrangeFrozenCandidate();
            var initial = DecisionRuleSetRequestBuilder.firstVersion(f.ruleSetCode());
            var secondRequest = DecisionRuleSetRequestBuilder.currentNextVersion(f.ruleSetCode(),
                candidate.created().getRuleSetVersionId(), candidate.request().getExpectedRuleSetContentHash());
            String saveKey = f.nextKey();
            var second = http.post(RULES, ACTOR, SESSION, saveKey, secondRequest);
            assertEquals(200, second.statusCode(), second.body());
            var page = page(http, f.ruleSetCode(), "");
            assertEquals(1, page.get("total").asLong());
            JsonNode root = page.get("items").get(0);
            assertEquals(http.json(second).get("ruleSetVersionId"), root.get("ruleSetVersionId"));
            assertEquals("3", root.get("managementVersion").asText());
            assertFalse(root.get("inUse").asBoolean());
            assertEquals(0, page(http, f.ruleSetCode(), "&status=FROZEN").get("total").asLong());
            var history = history(f.ruleSetCode());
            var counts = decisionTestMapper.snapshotCounts(f.ruleSetCode());
            String key = f.nextKey();
            var deleted = delete(http, root, key);
            assertEquals(200, deleted.statusCode(), deleted.body());
            assertEquals("DELETED", http.json(deleted).get("status").asText());
            assertEquals("4", http.json(deleted).get("version").asText());
            assertProblem(delete(http, root, key), 409, "RISK_RULE_SET_DELETED");
            assertEquals(history, history(f.ruleSetCode()));
            assertEquals(counts, decisionTestMapper.snapshotCounts(f.ruleSetCode()));
            assertEquals(0, page(http, f.ruleSetCode(), "").get("total").asLong());
            for (String id : List.of(candidate.created().getRuleSetVersionId(), root.get("ruleSetVersionId").asText()))
                assertProblem(http.get(RULES + "/" + id, ACTOR, SESSION), 404, "RISK_DECISION_CONFIGURATION_NOT_FOUND");
            assertProblem(delete(http, root, f.nextKey()), 409, "RISK_RULE_SET_DELETED");
            assertProblem(http.post(RULES, ACTOR, SESSION, f.nextKey(), DecisionRuleSetRequestBuilder.current(initial)), 409, "RISK_RULE_SET_DELETED");
            assertProblem(http.post(RULES + "/" + root.get("ruleSetVersionId").asText() + "/freeze", ACTOR,
                SESSION, f.nextKey(), new FreezeRuleSetVersionRequestBody(root.get("contentHash").asText())), 409, "RISK_RULE_SET_DELETED");
            assertProblem(f.postCandidate(f.nextKey(), candidate.request()), 409, "RISK_RULE_SET_DELETED");
            assertEquals(200, f.getCandidate(candidate).statusCode());
            String validateKey = f.nextKey();
            var validated = f.postValidate(candidate, ACTOR, validateKey, 1);
            assertEquals(200, validated.statusCode(), validated.body());
            assertEquals(validated.body(), f.postValidate(candidate, ACTOR, validateKey, 1).body());
            assertEquals(8, f.checkCodes(candidate).size());
        }
    }

    /** 任一环境旧版本仍被 ACTIVE Release 引用时，最新草稿也不可删除。 */
    @ParameterizedTest
    @ValueSource(strings = {"TEST", "PRODUCTION"})
    void test_activeOldVersionBlocksDelete(String environment) throws Exception {
        try (var f = fixture(); var http = new RiskDecisionHttpClient(port)) {
            var candidate = f.arrangeFrozenCandidate();
            var second = http.post(RULES, ACTOR, SESSION, f.nextKey(), DecisionRuleSetRequestBuilder.currentNextVersion(
                f.ruleSetCode(), candidate.created().getRuleSetVersionId(), candidate.request().getExpectedRuleSetContentHash()));
            assertEquals(200, second.statusCode(), second.body());
            jdbc.update("UPDATE t_decision_release SET environment_code=?,status='ACTIVE',active_slot=1 WHERE release_id=?",
                environment, candidate.created().getReleaseId());
            var root = page(http, f.ruleSetCode(), "").get("items").get(0);
            assertEquals("DRAFT", root.get("status").asText());
            assertTrue(root.get("inUse").asBoolean());
            String key = f.nextKey();
            assertProblem(delete(http, root, key), 409, "RISK_RULE_SET_IN_USE");
            jdbc.update("UPDATE t_decision_release SET status='SUPERSEDED',active_slot=NULL WHERE release_id=?",
                candidate.created().getReleaseId());
            assertEquals(200, delete(http, root, key).statusCode());
        }
    }

    /** 陈旧管理版本返回可刷新冲突，不产生删除与幂等残留。 */
    @Test
    void test_changedVersionAndCorruptActiveSlotRollBack() throws Exception {
        try (var f = fixture(); var http = new RiskDecisionHttpClient(port)) {
            var candidate = f.arrangeFrozenCandidate();
            var root = page(http, f.ruleSetCode(), "").get("items").get(0);
            String path = RULES + "/" + root.get("ruleSetId").asText();
            String key = f.nextKey();
            assertProblem(http.delete(path, ACTOR, SESSION, key, Map.of("expectedVersion", "1")), 409, "RISK_RULE_SET_CHANGED");
            jdbc.update("UPDATE t_decision_release SET active_slot=1 WHERE release_id=?", candidate.created().getReleaseId());
            try {
                assertEquals(500, delete(http, root, key).statusCode());
                assertEquals(500, http.get(RULES + "?page=1&size=10&ruleSetCode=" + f.ruleSetCode(), ACTOR, SESSION).statusCode());
            } finally { jdbc.update("UPDATE t_decision_release SET active_slot=NULL WHERE release_id=?", candidate.created().getReleaseId()); }
            assertEquals("CONFIGURABLE", jdbc.queryForObject("SELECT status FROM t_rule_set WHERE rule_set_id=?", String.class,
                root.get("ruleSetId").asText()));
        }
    }

    /** 两个相同幂等删除并发只形成一次终态，响应完全一致。 */
    @Test
    void test_parallelSameDeleteHasOneWinner() throws Exception {
        try (var f = fixture(); var http = new RiskDecisionHttpClient(port); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            f.arrangeFrozenCandidate();
            var root = page(http, f.ruleSetCode(), "").get("items").get(0);
            String key = f.nextKey();
            var first = executor.submit(() -> delete(http, root, key));
            var second = executor.submit(() -> delete(http, root, key));
            var a = first.get(); var b = second.get();
            // 同一资源并发删除只有一个成功；另一个在锁内看到已删除或在锁等待超时后返回并发冲突。
            var winner = a.statusCode() == 200 ? a : b;
            var loser = winner == a ? b : a;
            assertEquals(200, winner.statusCode(), winner.body());
            assertEquals(409, loser.statusCode(), loser.body());
            assertTrue(Set.of("RISK_RULE_SET_DELETED", "CONCURRENT_REQUEST").contains(http.json(loser).path("code").asText()),
                loser.body());
        }
    }

    /** 删除入口拒绝非规范字符串长整数及多余字段。 */
    @Test
    void test_deleteRequestShapeAndSignedLongBounds() throws Exception {
        try (var f = fixture(); var http = new RiskDecisionHttpClient(port)) {
            f.arrangeFrozenCandidate();
            var root = page(http, f.ruleSetCode(), "").get("items").get(0);
            for (Object invalid : List.of(1, "0", "01", "-1", "9223372036854775808", " 2")) {
                String key = f.nextKey();
                var response = http.delete(RULES + "/" + root.get("ruleSetId").asText(), ACTOR, SESSION, key,
                    Map.of("expectedVersion", invalid));
                assertTrue(response.statusCode() == 400 || response.statusCode() == 422, response.body());
            }
        }
    }

    /** 损坏根指针不得在分页中静默消失或显示旧版。 */
    @Test
    void test_listFailsClosedForDanglingOrStaleHead() throws Exception {
        try (var f = fixture(); var http = new RiskDecisionHttpClient(port)) {
            var first = f.arrangeFrozenCandidate();
            var second = http.post(RULES, ACTOR, SESSION, f.nextKey(), DecisionRuleSetRequestBuilder.currentNextVersion(
                f.ruleSetCode(), first.created().getRuleSetVersionId(), first.request().getExpectedRuleSetContentHash()));
            assertEquals(200, second.statusCode(), second.body());
            String head = http.json(second).get("ruleSetVersionId").asText();
            for (String invalid : List.of(Long.toString(Long.MAX_VALUE), first.created().getRuleSetVersionId())) {
                jdbc.update("UPDATE t_rule_set SET current_rule_set_version_id=? WHERE rule_set_code=?", invalid, f.ruleSetCode());
                try {
                    var response = http.get(RULES + "?page=1&size=10&ruleSetCode=" + f.ruleSetCode(), ACTOR, SESSION);
                    assertEquals(500, response.statusCode(), response.body());
                } finally { jdbc.update("UPDATE t_rule_set SET current_rule_set_version_id=? WHERE rule_set_code=?", head, f.ruleSetCode()); }
            }
        }
    }

    /** 所有管理写命令竞争同一实际规则集；竞争失败没有事务或幂等残留。 */
    @Test
    void test_realRedisContentionProtectsEveryManagementCommandAndReleasesAfterFailure() throws Exception {
        try (var f = fixture(); var http = new RiskDecisionHttpClient(port)) {
            var candidate = f.arrangeFrozenCandidate();
            var second = http.post(RULES, ACTOR, SESSION, f.nextKey(), DecisionRuleSetRequestBuilder.currentNextVersion(
                f.ruleSetCode(), candidate.created().getRuleSetVersionId(), candidate.request().getExpectedRuleSetContentHash()));
            assertEquals(200, second.statusCode(), second.body());
            var root = page(http, f.ruleSetCode(), "").get("items").get(0);
            var lock = redis.getLock("decision:rule-set:CHECKOUT_PREPARE:" + f.ruleSetCode());
            lock.lock(10, java.util.concurrent.TimeUnit.SECONDS);
            String deleteKey = f.nextKey();
            try {
                assertProblem(delete(http, root, deleteKey), 409, "CONCURRENT_REQUEST");
                assertProblem(http.post(RULES, ACTOR, SESSION, f.nextKey(), DecisionRuleSetRequestBuilder.currentNextVersion(
                    f.ruleSetCode(), root.get("ruleSetVersionId").asText(), root.get("contentHash").asText())), 409, "CONCURRENT_REQUEST");
                assertProblem(http.post(RULES + "/" + root.get("ruleSetVersionId").asText() + "/freeze", ACTOR,
                    SESSION, f.nextKey(), new FreezeRuleSetVersionRequestBody(root.get("contentHash").asText())), 409, "CONCURRENT_REQUEST");
                assertProblem(f.postCandidate(f.nextKey(), candidate.request()), 409, "CONCURRENT_REQUEST");
            } finally { lock.unlock(); }
            assertEquals(200, delete(http, root, deleteKey).statusCode());
            assertFalse(lock.isLocked(), "成功提交之后必须释放 Redis 锁");
            assertProblem(delete(http, root, f.nextKey()), 409, "RISK_RULE_SET_DELETED");
            assertFalse(lock.isLocked(), "业务冲突之后也必须释放 Redis 锁");
            var metrics = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://127.0.0.1:" + port + "/actuator/prometheus")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, metrics.statusCode(), metrics.body());
            assertTrue(metrics.body().contains("jvm_memory_used_bytes"));
        }
    }

    /** 删除与保存、冻结、Candidate 创建并发时，根 CAS 和共同资源锁维持唯一合法结果。 */
    @ParameterizedTest
    @ValueSource(strings = {"save", "freeze", "candidate"})
    void test_deleteRacesWithConfigurationCommands(String action) throws Exception {
        try (var f = fixture(); var http = new RiskDecisionHttpClient(port); var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var candidate = action.equals("freeze") ? null : f.arrangeFrozenCandidate();
            if (action.equals("freeze")) f.arrangeLegacyDraft();
            var root = page(http, f.ruleSetCode(), "").get("items").get(0);
            var start = new java.util.concurrent.CountDownLatch(1);
            String deleteKey = f.nextKey(); String writeKey = f.nextKey();
            var deleting = pool.submit(() -> { start.await(); return delete(http, root, deleteKey); });
            var writing = pool.submit(() -> {
                start.await();
                if (action.equals("candidate")) return f.postCandidate(writeKey, candidate.request());
                if (action.equals("freeze")) return http.post(RULES + "/" + root.get("ruleSetVersionId").asText() + "/freeze",
                    ACTOR, SESSION, writeKey, new FreezeRuleSetVersionRequestBody(root.get("contentHash").asText()));
                return http.post(RULES, ACTOR, SESSION, writeKey, DecisionRuleSetRequestBuilder.currentNextVersion(
                    f.ruleSetCode(), root.get("ruleSetVersionId").asText(), root.get("contentHash").asText()));
            });
            start.countDown();
            var deleted = deleting.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var written = writing.get(10, java.util.concurrent.TimeUnit.SECONDS);
            if (action.equals("candidate")) {
                assertEquals(200, deleted.statusCode(), deleted.body());
                assertTrue(written.statusCode() == 200 || written.statusCode() == 409, written.body());
                if (written.statusCode() == 200) {
                    assertEquals(200, http.get("/decision-releases/v1/candidates/" + http.json(written).get("releaseId").asText(), ACTOR, SESSION).statusCode());
                } else assertProblem(written, 409, "RISK_RULE_SET_DELETED");
            } else if (deleted.statusCode() == 200) {
                assertProblem(written, 409, "RISK_RULE_SET_DELETED");
            } else {
                assertProblem(deleted, 409, "RISK_RULE_SET_CHANGED");
                assertEquals(200, written.statusCode(), written.body());
            }
        }
    }

    /** 已删除命令的回执不依赖之后发生变化的动作目录。 */
    @Test
    void test_repeatedDeleteIsRejectedWithoutReloadingActionCatalog() throws Exception {
        try (var f = fixture(); var http = new RiskDecisionHttpClient(port)) {
            f.arrangeFrozenCandidate();
            var root = page(http, f.ruleSetCode(), "").get("items").get(0);
            String key = f.nextKey();
            var result = delete(http, root, key);
            assertEquals(200, result.statusCode(), result.body());
            String original = jdbc.queryForObject("SELECT implementation_hash FROM t_action_version WHERE action_code='ALLOW_CHECKOUT'", String.class);
            jdbc.update("UPDATE t_action_version SET implementation_hash=? WHERE action_code='ALLOW_CHECKOUT'", "b".repeat(64));
            try {
                assertProblem(delete(http, root, key), 409, "RISK_RULE_SET_DELETED");
                assertProblem(delete(http, root, f.nextKey()), 409, "RISK_RULE_SET_DELETED");
            } finally { jdbc.update("UPDATE t_action_version SET implementation_hash=? WHERE action_code='ALLOW_CHECKOUT'", original); }
        }
    }

    /** 当前测试拥有的完整版本行快照，整体删除前后应逐项相等。 */
    private List<Map<String, Object>> history(String code) {
        return jdbc.queryForList("SELECT * FROM t_rule_set_version WHERE rule_set_code=? ORDER BY version_no", code);
    }

    /** 使用规则集粒度查询并校验成功响应。 */
    private JsonNode page(RiskDecisionHttpClient http, String code, String extra) throws Exception {
        var response = http.get(RULES + "?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode=" + code + extra, ACTOR, SESSION);
        assertEquals(200, response.statusCode(), response.body());
        return http.json(response);
    }

    /** 提交页面观察到的准确整体身份及 CAS 版本。 */
    private HttpResponse<String> delete(RiskDecisionHttpClient http, JsonNode root, String key) throws Exception {
        return http.delete(RULES + "/" + root.get("ruleSetId").asText(), ACTOR, SESSION, key,
            Map.of("expectedVersion", root.get("managementVersion").asText()));
    }

    /** 为每个用例构造独占且可清理的业务数据。 */
    private DecisionReleaseTestFixture fixture() {
        return new DecisionReleaseTestFixture(port, decisionTestMapper, jdbc, transactionManager, identifiers);
    }
}
