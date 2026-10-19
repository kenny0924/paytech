package com.paytech.commercial.auto.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutTermVersionMocker;

/**
 * 按商业时点选取适用版本（technique §2.3，区间命中者取 effective_from 最大）：真实 MyBatis 与本地库，夹具方案 910000000000112 的 ACTIVE 版本区间为 [09-01, 10-01)。
 *
 * @see CommercialTermVersionRepository#findEffectiveAt(long, Instant)
 */
class CommercialTermVersionEffectiveAtTest extends CommercialMockTestBase {
    private static final long PLAN_ID = CheckoutTermVersionMocker.TERM_PLAN_ID;
    private static final long ACTIVE_VERSION_ID = CheckoutTermVersionMocker.TERM_VERSION_ID;
    private static final long SUCCESSOR_VERSION_ID = 910000000000351L;
    private static final Instant SWITCH_TIME = Instant.parse("2026-09-10T00:00:00Z");

    @Autowired
    private CommercialTermVersionRepository repository;
    @Autowired
    private CheckoutTermVersionMocker versions;
    @Autowired
    private CommercialTestMapper data;

    /** 每例准备夹具商务事实。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture.install();
    }

    /** 区间内命中原 ACTIVE 版本；起点之前与终点（不含）处没有版本。 */
    @Test
    void test_normalFlow() {
        assertEquals(ACTIVE_VERSION_ID, repository.findEffectiveAt(PLAN_ID, CheckoutFixture.NOW).getTermVersionId());
        assertNull(repository.findEffectiveAt(PLAN_ID, Instant.parse("2026-08-31T23:59:59Z")));
        assertNull(repository.findEffectiveAt(PLAN_ID, Instant.parse("2026-10-01T00:00:00Z")));
    }

    /** 已到起点但尚未切为 ACTIVE 的 SCHEDULED 版本被选中，切换点前仍是原版本。 */
    @Test
    void test_scheduledVersionAlreadyEffective() {
        data.overwriteVersionEffectiveTo(ACTIVE_VERSION_ID, SWITCH_TIME);
        versions.mockSuccessorVersion(SUCCESSOR_VERSION_ID, CommercialTermVersionStatus.SCHEDULED, SWITCH_TIME);

        assertEquals(SUCCESSOR_VERSION_ID, repository.findEffectiveAt(PLAN_ID, SWITCH_TIME).getTermVersionId());
        assertEquals(ACTIVE_VERSION_ID,
            repository.findEffectiveAt(PLAN_ID, SWITCH_TIME.minusNanos(1000)).getTermVersionId());
    }

    /** 区间包含时点的 DRAFT 不参与。 */
    @Test
    void test_draftVersionIgnored() {
        versions.mockSuccessorVersion(SUCCESSOR_VERSION_ID, CommercialTermVersionStatus.DRAFT, SWITCH_TIME);
        assertEquals(ACTIVE_VERSION_ID, repository.findEffectiveAt(PLAN_ID, CheckoutFixture.NOW).getTermVersionId());
    }

    /** 激活作业未运行、原 ACTIVE 终点仍为空时，起点已到的 SCHEDULED 后继以较大 effective_from 胜出；起点之前仍是原版本。 */
    @Test
    void test_lateActivationJobIsHarmless() {
        versions.mockSuccessorVersion(SUCCESSOR_VERSION_ID, CommercialTermVersionStatus.SCHEDULED, SWITCH_TIME);

        assertEquals(SUCCESSOR_VERSION_ID, repository.findEffectiveAt(PLAN_ID, CheckoutFixture.NOW).getTermVersionId());
        assertEquals(ACTIVE_VERSION_ID,
            repository.findEffectiveAt(PLAN_ID, SWITCH_TIME.minusNanos(1000)).getTermVersionId());
    }
}
