package com.paytech.commercial.auto.ext.http.agreement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.service.handler.AgreementStatusChangedHandler;
import com.paytech.commercial.service.handler.CapabilityPrerequisiteCheckHandler;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;
import com.paytech.commercial.test.mocker.local.agreement.CapabilityGateMocker;

import tools.jackson.databind.JsonNode;

/**
 * 合同激活追加登记开通项前置检查（开通项前置门禁 technique.md §11.3）
 * <li>SIGNED → ACTIVE 的同一事务登记前置检查，提交后门禁核对：等前置的 CARD 变为 ENABLED（合同版本 7）。</li>
 * <li>没有等前置 / 前置失效暂停的开通项时不登记检查。</li>
 *
 * @see AgreementApi#activate(String, String, AgreementTransitionRequestBody)
 */
class AgreementActivationPrerequisiteHttpTest extends AbstractCommercialWriteApiTest {
    private static final String ACTIVATE = "/agreements/v1/" + AgreementBuilder.AGREEMENT_ID + "/activate";

    /** CARD 开通项 ID。 */
    private static final long CARD_ID = 930000000000531L;

    @Autowired
    private CapabilityGateMocker gate;

    private AgreementWriteMocker.Mocker agreement;

    /** 每例：目录、画像、SIGNED 合同（Root 版本 5）、ACTIVE 版本与覆盖 CARD/USD 的价格。 */
    @BeforeEach
    void setUpClassMocks() {
        gate.mockCatalogAndProfile();
        agreement = gate.mockAgreement(AgreementBuilder.template().status(AgreementStatus.SIGNED));
        gate.mockCardPrice(gate.mockClientPricingVersion(CommercialTermVersionStatus.ACTIVE), 0);
    }

    /** 激活后前置检查执行：CARD 从等前置变为 ENABLED。 */
    @Test
    void test_activation_checksPendingCapability() {
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        mockAgreementQualified();
        mockChannelIdentityReady("USD");

        post(ACTIVATE, transition(5)).assertOk().assertText("status", AgreementStatus.ACTIVE.getCode()).assertVersion(6);

        JsonNode change = events.awaitEnvelope(AgreementCapabilityStatusChangedHandler.EVENT_TYPE,
            AgreementCapabilityStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID, 7).eventKey())
            .path("payload").path("changes").get(0);
        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), change.path("toExecutionStatus").asString()),
            () -> assertEquals("PREREQUISITE", change.path("trigger").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(),
                testMapper.agreementCapability(CARD_ID).get("execution_status")));
    }

    /** 开通项都已开通：只登记合同状态事件，不登记前置检查、不查资格。 */
    @Test
    void test_activation_noAwaitingCapability() {
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD, AgreementCapabilityExecutionStatus.ENABLED);

        post(ACTIVATE, transition(5)).assertOk().assertVersion(6);

        events.awaitEnvelope(AgreementStatusChangedHandler.EVENT_TYPE,
            AgreementStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID, 6).eventKey());
        assertAll(() -> assertEquals(0, testMapper.countCompensationEvents(CapabilityPrerequisiteCheckHandler.EVENT_TYPE)),
            () -> assertEquals(0, compliance.count()));
    }

    /** 激活请求。 */
    static AgreementTransitionRequestBody transition(long expectedVersion) {
        AgreementTransitionRequestBody body = new AgreementTransitionRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        return body;
    }
}
