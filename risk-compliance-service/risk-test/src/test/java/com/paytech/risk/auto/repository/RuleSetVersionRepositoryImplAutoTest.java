package com.paytech.risk.decision.domainservice.repository.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuleSetVersionRepositoryImplAutoTest {

    @Test
    void only_server_allocated_version_business_indexes_are_concurrent_conflicts() {
        assertFalse(RuleSetVersionRepositoryImpl.isRuleSetVersioningConflictMessage(
            "Duplicate entry '1-1' for key 'uk_point_version'"));
        assertTrue(RuleSetVersionRepositoryImpl.isRuleSetVersioningConflictMessage(
            "Duplicate entry '1-1' for key `mor_risk`.`uk_rule_set_ver`"));
        assertTrue(RuleSetVersionRepositoryImpl.isRuleSetVersioningConflictMessage(
            "Duplicate entry '1-1' for key 'mor_risk.uk_rule_set_ver'"));

        assertFalse(RuleSetVersionRepositoryImpl.isRuleSetVersioningConflictMessage(
            "Duplicate entry '1' for key 'PRIMARY'"));
        assertFalse(RuleSetVersionRepositoryImpl.isRuleSetVersioningConflictMessage(
            "Duplicate entry '1-RULE' for key 'uk_risk_rule_code'"));
        assertFalse(RuleSetVersionRepositoryImpl.isRuleSetVersioningConflictMessage(
            "Duplicate entry '1-1' for key 'uk_point_version_shadow'"));
        assertFalse(RuleSetVersionRepositoryImpl.isRuleSetVersioningConflictMessage(
            "Duplicate entry '1-1'"));
        assertFalse(RuleSetVersionRepositoryImpl.isRuleSetVersioningConflictMessage(null));
    }
}
