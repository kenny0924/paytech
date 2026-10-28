package com.paytech.commercial.auto.ext.mq;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.ext.mq.QualificationChangedListener;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.compliance.ComplianceEventMocker;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;
import com.paytech.commercial.test.mocker.local.agreement.CapabilityGateMocker;
import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.foundation.messaging.api.DomainEvent;

/**
 * compliance QualificationChanged 消费入口（开通项前置门禁 technique.md §11.2、§15.1）：真实本地 Broker 投递到 commercial.compliance 分区队列
 * <li>CLIENT 撤销：该 Client 两份 CLIENT 合同的已开通 CARD 都被门禁暂停；OE 维度只影响该 OE 上的合同；LOCAL_PARTNER 确认后忽略、不查资格；
 * 信封不合法进入死信。</li>
 *
 * @see QualificationChangedListener#consume(DomainEvent)
 */
class QualificationChangedListenerTest extends CommercialMockTestBase {
    /** 第一份合同 CARD 开通项 ID。 */
    private static final long FIRST_CARD_ID = 930000000000551L;

    /** 第二份合同 CARD 开通项 ID。 */
    private static final long SECOND_CARD_ID = 930000000000552L;

    @Autowired
    private CapabilityGateMocker gate;

    @Autowired
    private CommercialTestMapper testMapper;

    @Autowired
    private ComplianceEventMocker complianceEvents;

    /** 每例：目录与画像、同一 Client 的两份 ACTIVE CLIENT 合同（AE993、AE994），各有一个已开通的 CARD。 */
    @BeforeEach
    void setUpClassMocks() {
        gate.mockCatalogAndProfile();
        AgreementWriteMocker.Mocker first = gate.mockActiveAgreement();
        AgreementWriteMocker.Mocker second = gate.mockSecondAgreement();
        gate.mockCapability(first, FIRST_CARD_ID, CommercialCodes.Capability.CARD, AgreementCapabilityExecutionStatus.ENABLED);
        gate.mockCapability(second, SECOND_CARD_ID, CommercialCodes.Capability.CARD, AgreementCapabilityExecutionStatus.ENABLED);
    }

    /** CLIENT REVOKED：两份合同都核对，Client 不合格使两个 CARD 都变为 SUSPENDED_BY_PREREQUISITE 并各发布一条事件。 */
    @Test
    void test_clientRevoked_suspendsAllAgreements() {
        compliance.mockStatus(QualificationRoleType.CLIENT, Long.toString(AgreementBuilder.CLIENT_ID),
            QualificationEvaluationStatus.REVOKED);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, AgreementBuilder.ENTITY_NO,
            QualificationEvaluationStatus.ELIGIBLE);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, CapabilityGateMocker.SECOND_ENTITY_NO,
            QualificationEvaluationStatus.ELIGIBLE);
        double handled = complianceEvents.outcome("handled");

        complianceEvents.publish(ComplianceEventMocker.qualificationChanged(930000000000561L,
            QualificationRoleType.CLIENT.getCode(), Long.toString(AgreementBuilder.CLIENT_ID), "REVOKED"));

        complianceEvents.awaitOutcome("handled", handled, 1);
        awaitStatusEvents(AgreementBuilder.AGREEMENT_ID, CapabilityGateMocker.SECOND_AGREEMENT_ID);
        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE.getCode(),
                executionStatus(FIRST_CARD_ID)),
            () -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE.getCode(),
                executionStatus(SECOND_CARD_ID)),
            () -> assertEquals(2, compliance.count()));
    }

    /** OPERATING_ENTITY 维度：只核对有开通项在 AE994 上的第二份合同，它被暂停；第一份合同不变。 */
    @Test
    void test_operatingEntityRejected_onlyThatEntity() {
        compliance.mockStatus(QualificationRoleType.CLIENT, Long.toString(AgreementBuilder.CLIENT_ID),
            QualificationEvaluationStatus.ELIGIBLE);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, CapabilityGateMocker.SECOND_ENTITY_NO,
            QualificationEvaluationStatus.REJECTED);
        double handled = complianceEvents.outcome("handled");

        complianceEvents.publish(ComplianceEventMocker.qualificationChanged(930000000000562L,
            QualificationRoleType.OPERATING_ENTITY.getCode(), CapabilityGateMocker.SECOND_ENTITY_NO, "REJECTED"));

        complianceEvents.awaitOutcome("handled", handled, 1);
        awaitStatusEvents(CapabilityGateMocker.SECOND_AGREEMENT_ID);
        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(FIRST_CARD_ID)),
            () -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE.getCode(),
                executionStatus(SECOND_CARD_ID)),
            () -> assertEquals(1, compliance.count()));
    }

    /** LOCAL_PARTNER：确认后忽略，不查资格、不改状态。 */
    @Test
    void test_localPartnerIgnored() {
        double handled = complianceEvents.outcome("handled");

        complianceEvents.publish(ComplianceEventMocker.qualificationChanged(930000000000563L,
            QualificationRoleType.LOCAL_PARTNER.getCode(), "930000000000985", "REVOKED"));

        complianceEvents.awaitOutcome("handled", handled, 1);
        assertAll(() -> assertEquals(0, compliance.count()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(FIRST_CARD_ID)));
    }

    /** 信封 Payload 缺少 roleId：进入死信，不查资格。 */
    @Test
    void test_invalidEnvelope_deadLetter() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roleType", QualificationRoleType.CLIENT.getCode());
        payload.put("status", "REVOKED");
        double dead = complianceEvents.outcome("dead");

        complianceEvents.publish(ComplianceEventMocker.event(930000000000564L, payload));

        complianceEvents.awaitOutcome("dead", dead, 1);
        assertAll(() -> assertNotNull(complianceEvents.receiveDeadLetter(5000)), () -> assertEquals(0, compliance.count()));
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
