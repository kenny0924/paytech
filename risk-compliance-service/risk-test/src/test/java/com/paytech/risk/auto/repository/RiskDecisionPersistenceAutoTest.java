package com.paytech.risk.auto.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.risk.api.decision.response.CreateRuleSetVersionResponseBody;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.test.builder.DecisionRuleSetRequestBuilder;
import com.paytech.risk.test.decision.RiskDecisionHttpClient;
import com.paytech.risk.test.decision.RiskDecisionTestMapper.OwnedCommandIdentity;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 使用真实 MySQL 证明 Decision 聚合可由 Owner Repository 无损重建并对损坏 Code 失败关闭。 */
class RiskDecisionPersistenceAutoTest extends RiskServiceAutoTestBase {

    private static final String ACTOR = "910000000000000001";
    private static final String CALLER_SCOPE = "OPERATIONS_ACCOUNT:" + ACTOR;
    private static final String SESSION = "risk-auto-persistence-session";

    @Autowired
    private DecisionRuleSetRepository ruleSetRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void test_ruleSetClosureRoundTripsRegisteredEnumsAndUtcMicros() throws Exception {
        String ruleSetCode = decisionTestMapper.uniqueRuleSetCode();
        String idempotencyKey = decisionTestMapper.uniqueNamespace();
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            HttpResponse<String> response = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                idempotencyKey, DecisionRuleSetRequestBuilder.currentFirstVersion(ruleSetCode));
            assertEquals(200, response.statusCode(), response.body());
            var created = http.body(response, CreateRuleSetVersionResponseBody.class);

            var closure = ruleSetRepository.requireConfiguration(Long.parseLong(created.getRuleSetVersionId()));
            assertEquals(DecisionBusinessDomain.CHECKOUT, closure.decisionPoint().businessDomain());
            assertNotNull(closure.decisionPoint().status());
            org.junit.jupiter.api.Assertions.assertNull(closure.pointVersion());
            assertTrue(closure.inputFields().isEmpty());
            assertEquals(RuleSetVersionStatus.DRAFT, closure.ruleSetVersion().status());
            assertEquals("ORDER_PREPARATION", closure.ruleSetVersion().inputContractCode());
            assertEquals(4, closure.ruleSetVersion().inputContractVersion());
            org.junit.jupiter.api.Assertions.assertNull(closure.operands().getFirst().fieldCode());
            assertEquals("INPUT_COUNTRY_CODE", closure.operands().getFirst().variableCode());
            assertNotNull(closure.nodes().getFirst().nodeType());
            assertNotNull(closure.nodes().getFirst().comparisonOperator());
            assertNotNull(closure.operands().getFirst().operandRole());
            assertNotNull(closure.operands().getFirst().sourceType());
            assertNotNull(closure.actionVersions().getFirst().actionCategory());
            assertNotNull(closure.actionVersions().getFirst().decisionCode());
            assertNotNull(closure.actionVersions().getFirst().mergePolicy());
            assertEquals(0, closure.ruleSetVersion().createTime().getNano() % 1_000,
                "DATETIME(6) 必须以 UTC 微秒精度往返");
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleSetCode),
                List.of(command(idempotencyKey)));
        }
    }

    @Test
    void test_unknownPersistedEnumCodeFailsClosedInsteadOfGuessing() throws Exception {
        String ruleSetCode = decisionTestMapper.uniqueRuleSetCode();
        String idempotencyKey = decisionTestMapper.uniqueNamespace();
        try (RiskDecisionHttpClient http = new RiskDecisionHttpClient(port)) {
            HttpResponse<String> response = http.post("/decision-rule-sets/v1/rule-sets", ACTOR, SESSION,
                idempotencyKey, DecisionRuleSetRequestBuilder.currentFirstVersion(ruleSetCode));
            assertEquals(200, response.statusCode(), response.body());
            var created = http.body(response, CreateRuleSetVersionResponseBody.class);
            jdbc.update("UPDATE t_rule_set_version SET status='UNKNOWN_AUTO' WHERE rule_set_version_id=?",
                Long.parseLong(created.getRuleSetVersionId()));

            RuntimeException failure = assertThrows(RuntimeException.class,
                () -> ruleSetRepository.requireConfiguration(Long.parseLong(created.getRuleSetVersionId())));
            assertTrue(rootCause(failure).getMessage().contains("未知枚举 Code"));
            assertTrue(rootCause(failure).getMessage().contains("UNKNOWN_AUTO"));
        } finally {
            decisionTestMapper.deleteOwnedDecisionConfigurations(List.of(ruleSetCode),
                List.of(command(idempotencyKey)));
        }
    }

    private static OwnedCommandIdentity command(String idempotencyKey) {
        return new OwnedCommandIdentity(CALLER_SCOPE, "CREATE_RULE_SET_VERSION", idempotencyKey);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
