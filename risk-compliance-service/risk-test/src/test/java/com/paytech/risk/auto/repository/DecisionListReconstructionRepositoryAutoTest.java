package com.paytech.risk.decision.domainservice.repository.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.DecisionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationStrategy;
import com.paytech.risk.decision.domain.enums.FailureStrategy;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionListReconstructionRepositoryAutoTest {

    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    @Test
    void rule_set_list_validates_an_existing_legacy_companion_point_version() {
        assertEquals(1, ruleSetRepository(List.of(pointVersion(1, DecisionPointVersionStatus.FROZEN)))
            .findPage(ruleSetQuery()).items().size());

        assertThrows(ErrorException.class, () -> ruleSetRepository(List.of()).findPage(ruleSetQuery()));
        assertThrows(ErrorException.class, () -> ruleSetRepository(List.of(
            pointVersion(1, DecisionPointVersionStatus.FROZEN),
            pointVersion(1, DecisionPointVersionStatus.FROZEN))).findPage(ruleSetQuery()));
        assertThrows(ErrorException.class, () -> ruleSetRepository(List.of(
            pointVersion(2, DecisionPointVersionStatus.FROZEN))).findPage(ruleSetQuery()));
        assertThrows(ErrorException.class, () -> ruleSetRepository(List.of(
            pointVersion(1, DecisionPointVersionStatus.DRAFT))).findPage(ruleSetQuery()));
    }

    @Test
    void rule_set_list_without_legacy_binding_does_not_query_point_versions() {
        var repository = new DecisionRuleSetRepositoryImpl(proxy(DecisionRuleSetMapper.class, Map.of(
            "countMalformedRuleSets", 0L,
            "selectRuleSetVersions", List.of(ruleSet(1, RuleSetVersionStatus.FROZEN)),
            "countRuleSetVersions", 1L,
            "selectDecisionPointsByIds", List.of(point()),
            "selectRuleSetsByCurrentVersions", List.of(management()),
            "selectCompanionBindingsByRuleSetVersionIds", List.of()
        )));
        var page = repository.findPage(ruleSetQuery());
        assertEquals(1, page.items().size());
        assertNull(page.items().getFirst().companionBinding());
    }

    private static DecisionRuleSetRepositoryImpl ruleSetRepository(List<DecisionPointVersion> pointVersions) {
        return new DecisionRuleSetRepositoryImpl(proxy(DecisionRuleSetMapper.class, Map.of(
            "countMalformedRuleSets", 0L,
            "selectRuleSetVersions", List.of(ruleSet(1, RuleSetVersionStatus.FROZEN)),
            "countRuleSetVersions", 1L,
            "selectDecisionPointsByIds", List.of(point()),
            "selectRuleSetsByCurrentVersions", List.of(management()),
            "selectCompanionBindingsByRuleSetVersionIds", List.of(binding()),
            "selectPointVersionsByIds", pointVersions
        )));
    }

    private static DecisionRuleSetRepository.RuleSetPageQuery ruleSetQuery() {
        return new DecisionRuleSetRepository.RuleSetPageQuery(null, null, null, 10, 0);
    }

    private static DecisionPoint point() {
        return new DecisionPoint(1, "CHECKOUT_PREPARE", "Checkout prepare", DecisionBusinessDomain.CHECKOUT,
            DecisionPointStatus.ACTIVE, NOW, NOW);
    }

    private static DecisionPointVersion pointVersion(long owner, DecisionPointVersionStatus status) {
        return new DecisionPointVersion(10, owner, 1, "CHECKOUT_PREPARE_INPUT", 1,
            "CHECKOUT_PREPARE_OUTPUT", DecisionValue.DENY, EvaluationStrategy.FIRST_TERMINAL,
            FailureStrategy.FAIL_CLOSED, 500, status, "a".repeat(64), NOW, NOW);
    }

    private static RuleSetVersion ruleSet(long owner, RuleSetVersionStatus status) {
        return new RuleSetVersion(20, owner, "CHECKOUT_DEFAULT", "Checkout default", 1, status,
            "b".repeat(64), 42, "initial", status == RuleSetVersionStatus.FROZEN ? 42L : null,
            status == RuleSetVersionStatus.FROZEN ? NOW : null, NOW, NOW, "LEGACY", "ORDER_PREPARATION", 4);
    }

    private static RuleSet management() {
        return new RuleSet(21, 1, "CHECKOUT_DEFAULT", 20, RuleSetManagementStatus.CONFIGURABLE,
            2, null, null, null, null, NOW, NOW);
    }

    private static DecisionPointRuleSet binding() {
        return new DecisionPointRuleSet(30, 10, 20, 1, NOW);
    }

    private static DecisionRelease release() {
        return new DecisionRelease(40, "1", 1, 10, DecisionEnvironment.TEST, DecisionReleaseStatus.DRAFT,
            null, "c".repeat(64), 42, null, null, 1, NOW, NOW);
    }

    private static ReleaseAuditEvent initialState() {
        return new ReleaseAuditEvent(50, 40, ReleaseAuditEventType.STATE_CHANGE, NOW,
            null, null, null, null, null, null, null, null, null, null,
            null, DecisionReleaseStatus.DRAFT, "CANDIDATE_CREATED", "OPERATIONS_COMMAND", "d".repeat(64),
            42L, 1, NOW);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> results) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (ignored, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(results, args);
            }
            if (!results.containsKey(method.getName())) {
                throw new AssertionError("Unexpected mapper call: " + method.getName());
            }
            return results.get(method.getName());
        });
    }
}
