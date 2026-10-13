package com.paytech.risk.auto.ext.http;

import static com.paytech.risk.test.decision.DecisionReleaseTestFixture.ACTOR;
import static com.paytech.risk.test.decision.DecisionReleaseTestFixture.SESSION;
import static com.paytech.risk.test.decision.RiskDecisionHttpClient.assertProblem;
import static org.junit.jupiter.api.Assertions.*;

import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import com.paytech.risk.test.decision.DecisionConfigurationLockProbe;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.OwnedCommandIdentity;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 从公开 HTTP 进入真实事务，分别安排两类字典停用和规则保存的数据库锁顺序。 */
@Import(DecisionDictionaryLockOrderHttpAutoTest.ProbeConfiguration.class)
class DecisionDictionaryLockOrderHttpAutoTest extends RiskServiceAutoTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Autowired private DecisionConfigurationLockProbe probe;

    /** 只注册观察和协调 SQL 的测试插件，应用组件及 SQL 均保持生产实现。 */
    @TestConfiguration
    static class ProbeConfiguration {
        /** 向真实 MyBatis 执行链加入可释放的测试协调器。 */
        @Bean DecisionConfigurationLockProbe dictionaryLockProbe() { return new DecisionConfigurationLockProbe("DecisionDictionaryMapper.find"); }
    }

    /** 保存先持共享锁则可完成；停用先持排他锁则保存必须拒绝且事务完全回滚。 */
    @ParameterizedTest
    @CsvSource({"rejection-reasons,SHARE", "rejection-reasons,WRITE", "subject-tags,SHARE", "subject-tags,WRITE"})
    void test_disableAndSaveRespectActualDatabaseLockOrder(String kind, String firstLock) throws Exception {
        String code = "RCO16_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        String ruleSet = decisionTestMapper.uniqueRuleSetCode();
        String key = decisionTestMapper.uniqueNamespace();
        var identities = List.of(new OwnedCommandIdentity("OPERATIONS_ACCOUNT:" + ACTOR, "CREATE_RULE_SET_VERSION", key));
        String path = "/decision-dictionaries/v1/" + kind;
        try (var http = new RiskDecisionHttpClient(port); var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            ObjectNode create = JSON.createObjectNode().put(kind.equals("rejection-reasons") ? "reasonCode" : "tagCode", code)
                .put("name", "确定锁顺序测试").putNull("description");
            if (kind.equals("rejection-reasons")) create.put("defaultMessage", "内部复核");
            var created = http.dictionary("POST", path, ACTOR, SESSION, create.toString());
            assertEquals(200, created.statusCode(), created.body());
            ObjectNode draft = JSON.valueToTree(DecisionRuleSetRequestBuilder.currentFirstVersion(ruleSet));
            ObjectNode rule = (ObjectNode) draft.at("/rules/0");
            if (kind.equals("rejection-reasons")) {
                ((ObjectNode) rule.get("action")).put("actionCode", "DENY_CHECKOUT");
                rule.put("reasonCode", code).put("reasonMessage", "人工覆盖文案");
                rule.set("additionalActions", JSON.createArrayNode());
            } else {
                rule.set("additionalActions", JSON.readTree("""
                    [{"sequenceNo":1,"actionCode":"ADD_RISK_TAG","actionVersionNo":1,
                      "targetBindingCode":"CURRENT_CLIENT_V1","subjectVariableCode":"INPUT_CLIENT_ID","parameters":[
                      {"parameterCode":"TAG","valueSequence":1,"sourceType":"LITERAL",
                       "fieldCode":null,"valueType":"STRING","literalValue":"%s"}]}]
                    """.formatted(code)));
            }
            Callable<HttpResponse<String>> save = () -> http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION, key, draft);
            Callable<HttpResponse<String>> disable = () -> http.dictionary("POST", path + "/" + code + "/status", ACTOR,
                SESSION, "{\"expectedVersion\":\"1\",\"status\":\"DISABLED\"}");
            boolean saveFirst = firstLock.equals("SHARE");
            try (var gate = probe.arm(code, firstLock)) {
                var first = pool.submit(saveFirst ? save : disable);
                assertTrue(gate.awaitAcquired(), "首个请求必须实际取得数据库锁");
                var second = pool.submit(saveFirst ? disable : save);
                assertTrue(gate.awaitCompetitor(), "竞争请求必须到达相同代码的 SQL");
                assertFalse(first.isDone());
                assertFalse(second.isDone());
                gate.close();
                var firstResult = first.get(5, TimeUnit.SECONDS);
                var secondResult = second.get(5, TimeUnit.SECONDS);
                assertEquals(200, firstResult.statusCode(), firstResult.body());
                if (saveFirst) assertEquals(200, secondResult.statusCode(), secondResult.body());
                else assertProblem(secondResult, 409, "RISK_DICTIONARY_SELECTION_UNAVAILABLE");
                assertEquals(saveFirst ? 1 : 0, decisionTestMapper.ruleSetVersionCount(ruleSet));
                var current = http.json(http.get(path + "/" + code, ACTOR, SESSION));
                assertEquals("DISABLED", current.path("status").asString());
                assertEquals("2", current.path("version").asString());
            }
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleSet), identities);
            dictionaryTestMapper.deleteOwned(code);
        }
    }
}
