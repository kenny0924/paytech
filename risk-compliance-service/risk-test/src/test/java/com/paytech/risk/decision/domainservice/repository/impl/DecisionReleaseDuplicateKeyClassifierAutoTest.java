package com.paytech.risk.decision.domainservice.repository.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Release 只把自己拥有的发布编号唯一索引冲突翻译成并发冲突。 */
class DecisionReleaseDuplicateKeyClassifierAutoTest {

    @Test
    void test_onlyExactReleaseNumberIndexIsClassifiedAsBusinessConcurrency() {
        assertTrue(DecisionReleaseRepositoryImpl.isReleaseNumberConflictMessage(
            "Duplicate entry '7' for key 'uk_release_no'"));
        assertTrue(DecisionReleaseRepositoryImpl.isReleaseNumberConflictMessage(
            "Duplicate entry '7' for key `mor_risk`.`uk_release_no`"));

        assertFalse(DecisionReleaseRepositoryImpl.isReleaseNumberConflictMessage(
            "Duplicate entry '7' for key 'PRIMARY'"));
        assertFalse(DecisionReleaseRepositoryImpl.isReleaseNumberConflictMessage(
            "Duplicate entry '7' for key 'uk_release_active'"));
        assertFalse(DecisionReleaseRepositoryImpl.isReleaseNumberConflictMessage(null));
    }
}
