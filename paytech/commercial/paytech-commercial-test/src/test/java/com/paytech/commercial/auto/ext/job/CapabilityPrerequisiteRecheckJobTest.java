package com.paytech.commercial.auto.ext.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.ext.job.CapabilityPrerequisiteRecheckJobHandler;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.service.processor.response.CapabilityPrerequisiteRecheckResponse;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;
import com.paytech.commercial.test.mocker.local.agreement.CapabilityGateMocker;
import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;

/**
 * 开通项前置定时重查作业入口：显式触发生产 XXL-Job Handler（测试中执行器关闭），经同一门禁核对（开通项前置门禁 technique.md §12、S-06）
 * <li>默认范围只扫等待前置 / 前置失效暂停；scope=ALL 另含已开通；PARTNER 合同的已开通项跳过；按合同 ID 分片互不重叠。</li>
 *
 * @see CapabilityPrerequisiteRecheckJobHandler#runOnce(String, int, int)
 */
class CapabilityPrerequisiteRecheckJobTest extends CommercialMockTestBase {
    /** 第一份合同（已定价）等前置的 CARD。 */
    private static final long PENDING_CARD_ID = 930000000000571L;

    /** 第二份合同（无方案）已开通的 CARD。 */
    private static final long ENABLED_CARD_ID = 930000000000572L;

    @Autowired
    private CapabilityGateMocker gate;

    @Autowired
    private CommercialTestMapper testMapper;

    @Autowired
    private CapabilityPrerequisiteRecheckJobHandler job;

    /** 每例：第一份合同已定价且 CARD 等前置；第二份合同无方案且 CARD 已开通；Client 与两个 OE 资格合格，第一份合同 OE 的 CARD/USD 渠道身份就绪。 */
    @BeforeEach
    void setUpClassMocks() {
        AgreementWriteMocker.Mocker first = gate.mockCoveredAgreement();
        AgreementWriteMocker.Mocker second = gate.mockSecondAgreement();
        gate.mockCapability(first, PENDING_CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        gate.mockCapability(second, ENABLED_CARD_ID, CommercialCodes.Capability.CARD, AgreementCapabilityExecutionStatus.ENABLED);
        compliance.mockStatus(QualificationRoleType.CLIENT, Long.toString(AgreementBuilder.CLIENT_ID),
            QualificationEvaluationStatus.ELIGIBLE);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, AgreementBuilder.ENTITY_NO,
            QualificationEvaluationStatus.ELIGIBLE);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, CapabilityGateMocker.SECOND_ENTITY_NO,
            QualificationEvaluationStatus.ELIGIBLE);
        mockChannelIdentityReady("USD");
    }

    /** 默认范围：只核对第一份合同，CARD 变为 ENABLED；已开通的第二份合同不扫描。 */
    @Test
    void test_pendingScope() {
        CapabilityPrerequisiteRecheckResponse result = job.runOnce("", 0, 1);

        awaitStatusEvents(AgreementBuilder.AGREEMENT_ID);
        assertAll(() -> assertCounts(result, 1, 1, 0),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(PENDING_CARD_ID)),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(ENABLED_CARD_ID)),
            () -> assertEquals(1, compliance.count()));
    }

    /** scope=ALL：两份合同都核对；第二份合同没有生效价格，已开通的 CARD 被暂停；PARTNER 合同的已开通项跳过、不计失败。 */
    @Test
    void test_allScope_includesEnabled() {
        long partnerId = AgreementBuilder.AGREEMENT_ID + 900;
        AgreementWriteMocker.Mocker partner = gate.mockAgreement(AgreementBuilder.template().agreementId(partnerId)
            .agreementNo("GATE-PARTNER-4").agreementType(AgreementAgreementType.PARTNER).businessMode(null).fundMode(null)
            .pricingAuthority(null).clientId(null).entityNo(null).localPartnerId(930000000000985L)
            .status(AgreementStatus.ACTIVE));
        gate.mockCapability(partner, ENABLED_CARD_ID + 1, AgreementBuilder.ENTITY_NO, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.ENABLED);

        CapabilityPrerequisiteRecheckResponse result = job.runOnce("scope=ALL", 0, 1);

        awaitStatusEvents(AgreementBuilder.AGREEMENT_ID, CapabilityGateMocker.SECOND_AGREEMENT_ID);
        assertAll(() -> assertCounts(result, 2, 2, 0),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(PENDING_CARD_ID)),
            () -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE.getCode(),
                executionStatus(ENABLED_CARD_ID)),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(ENABLED_CARD_ID + 1)));
    }

    /** 两个分片合起来恰好覆盖全部合同、互不重叠：合同 ID 同为奇数，分片 0 无合同，分片 1 核对两份。 */
    @Test
    void test_shardsDoNotOverlap() {
        CapabilityPrerequisiteRecheckResponse even = job.runOnce("scope=ALL", 0, 2);
        CapabilityPrerequisiteRecheckResponse odd = job.runOnce("scope=ALL", 1, 2);

        awaitStatusEvents(AgreementBuilder.AGREEMENT_ID, CapabilityGateMocker.SECOND_AGREEMENT_ID);
        assertAll(() -> assertEquals(1, AgreementBuilder.AGREEMENT_ID % 2),
            () -> assertEquals(1, CapabilityGateMocker.SECOND_AGREEMENT_ID % 2),
            () -> assertCounts(even, 0, 0, 0), () -> assertCounts(odd, 2, 2, 0),
            () -> assertEquals(2, compliance.count()));
    }

    /** 断言一轮的核对、迁移与失败数。 */
    static void assertCounts(CapabilityPrerequisiteRecheckResponse result, int checked, int changed, int failed) {
        assertEquals(checked, result.getCheckedCount(), "checked");
        assertEquals(changed, result.getChangedCount(), "changed");
        assertEquals(failed, result.getFailedCount(), "failed");
    }

    /** 一次等待这些合同第 6 版的执行状态变更事件全部发布。 */
    void awaitStatusEvents(long... agreementIds) {
        events.awaitEnvelopes(AgreementCapabilityStatusChangedHandler.EVENT_TYPE, Arrays.stream(agreementIds)
            .mapToObj(id -> AgreementCapabilityStatusChangedHandler.buildEvent(id, CapabilityGateMocker.ROOT_VERSION + 1).eventKey())
            .toArray(String[]::new));
    }

    /** 数据库中的执行状态。 */
    String executionStatus(long agreementCapabilityId) {
        return (String) testMapper.agreementCapability(agreementCapabilityId).get("execution_status");
    }
}
