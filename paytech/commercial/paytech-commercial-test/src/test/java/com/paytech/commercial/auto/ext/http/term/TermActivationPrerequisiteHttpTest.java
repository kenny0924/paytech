package com.paytech.commercial.auto.ext.http.term;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.term.CommercialTermApi;
import com.paytech.commercial.api.term.request.CommercialTermVersionCommandRequestBody;
import com.paytech.commercial.api.term.request.ScheduleCommercialTermVersionRequestBody;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;
import com.paytech.commercial.test.mocker.local.agreement.CapabilityGateMocker;
import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;

import tools.jackson.databind.JsonNode;

/**
 * 价格激活与开通项门禁的循环依赖回归（开通项前置门禁 technique.md §2 F-04、§8、§15.1）
 * <li>等前置的开通项所在合同，其 Client 价格版本可以排期并激活；激活事务追加登记前置检查，提交后门禁核对使开通项 ENABLED。</li>
 * <li>新行为：手动暂停的成员开通项同样要求价格覆盖，缺价格时排期被激活校验 3 拒绝。</li>
 *
 * @see CommercialTermApi#scheduleVersion(String, String, ScheduleCommercialTermVersionRequestBody)
 * @see CommercialTermApi#activateVersion(String, String, CommercialTermVersionCommandRequestBody)
 */
class TermActivationPrerequisiteHttpTest extends AbstractCommercialTermApiTest {
    /** CARD 开通项 ID。 */
    private static final long CARD_ID = 930000000000521L;

    @Autowired
    private CapabilityGateMocker gate;

    private AgreementWriteMocker.Mocker agreement;

    /** 每例：目录、带 Root 与 CLIENT 签署 Party 的 ACTIVE 合同、三个 ACTIVE 卡画像与等前置的 CARD（USD）。 */
    @BeforeEach
    void setUpClassMocks() {
        catalog.mockSeedCatalog();
        agreement = gate.mockActiveAgreement().party(AgreementPartyBuilder.template());
        mockCardProfiles(PricingProfileStatus.ACTIVE);
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
    }

    /** 等前置不阻塞价格激活；激活后前置检查提交执行，CARD 变为 ENABLED（合同版本 6，触发来源 PREREQUISITE）。 */
    @Test
    void test_pendingCapabilityPriceActivates_thenEnabled() {
        compliance.mockStatus(QualificationRoleType.CLIENT, Long.toString(AgreementBuilder.CLIENT_ID),
            QualificationEvaluationStatus.ELIGIBLE);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, ENTITY_NO, QualificationEvaluationStatus.ELIGIBLE);
        mockChannelIdentityReady("USD");

        JsonNode active = activeVersion(price("USD", CARD, 1, percentage("MDR", "0.025")));

        JsonNode change = events.awaitEnvelope(AgreementCapabilityStatusChangedHandler.EVENT_TYPE,
            AgreementCapabilityStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID, CapabilityGateMocker.ROOT_VERSION + 1)
                .eventKey()).path("payload").path("changes").get(0);
        assertAll(() -> assertEquals("ACTIVE", text(active, "status")),
            () -> assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES.getCode(),
                change.path("fromExecutionStatus").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), change.path("toExecutionStatus").asString()),
            () -> assertEquals("PREREQUISITE", change.path("trigger").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(),
                testMapper.agreementCapability(CARD_ID).get("execution_status")),
            () -> assertEquals(1, channelIdentity.count()));
    }

    /** 手动暂停的 APPLE_PAY 仍是合同成员，版本只有 CARD 价格时排期被激活校验 3 拒绝；不触发门禁、不查资格。 */
    @Test
    void test_suspendedCapabilityStillRequiresCoverage() {
        gate.mockCapability(agreement, CARD_ID + 1, CommercialCodes.Capability.APPLE_PAY,
            AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY);
        JsonNode version = replacePrices(createVersion(createPlan()), price("USD", CARD, 1, percentage("MDR", "0.025")));

        assertProblem(scheduleResult(approvedAndSigned(version), NOW), 400, "BUSINESS_CHECK_FAILED",
            "激活校验 3（覆盖完整）：开通项没有命中的价格: " + ENTITY_NO + "/" + CommercialCodes.Capability.APPLE_PAY);

        assertEquals(0, compliance.count() + classification.count() + channelIdentity.count());
    }
}
