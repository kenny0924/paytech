package com.paytech.commercial.auto.ext.eventhandler;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.event.AgreementCapabilityStatusChangedPayload;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.service.handler.CapabilityPrerequisiteCheckHandler;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;
import com.paytech.commercial.test.mocker.local.agreement.CapabilityGateMocker;
import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

import tools.jackson.databind.JsonNode;

/**
 * 开通项前置门禁的两个补偿 Handler 入口（开通项前置门禁 technique.md §11.1、§11.3）
 * <li>前置检查：经真实 Registrar 登记、提交后由 Foundation 唤醒，门禁迁移并发布；合同不存在 / PARTNER 合同终态；缺少 BCR Root（系统错误）
 * 与资格不可用（UNKNOWN）可重试且状态不变；事件键非法终态。</li>
 * <li>执行状态发布：按本版本与上一版本快照重建 changes[]（含旧格式快照的 status 推导），提交后发布；该版本无变化或事件键非法终态。</li>
 *
 * @see CapabilityPrerequisiteCheckHandler#handle(CompensationEvent)
 * @see AgreementCapabilityStatusChangedHandler#handle(CompensationEvent)
 */
class CapabilityPrerequisiteHandlerTest extends CommercialMockTestBase {
    /** CARD 开通项 ID。 */
    private static final long CARD_ID = 930000000000541L;

    @Autowired
    private CapabilityGateMocker gate;

    @Autowired
    private CommercialTestMapper testMapper;

    @Autowired
    private CompensationEventRegistrar registrar;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    @Autowired
    private CapabilityPrerequisiteCheckHandler checkHandler;

    @Autowired
    private AgreementCapabilityStatusChangedHandler statusHandler;

    /** 每例：目录与 ACTIVE 通用卡画像。 */
    @BeforeEach
    void setUpClassMocks() {
        gate.mockCatalogAndProfile();
    }

    /** 登记前置检查并提交：Handler 调用门禁，CARD 从等前置变为 ENABLED 并发布执行状态事件（版本 6）。 */
    @Test
    void test_check_normalFlow() {
        AgreementWriteMocker.Mocker agreement = gate.mockActiveAgreement();
        gate.mockCardPrice(gate.mockClientPricingVersion(CommercialTermVersionStatus.ACTIVE), 0);
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        mockQualified();

        registerInTransaction(CapabilityPrerequisiteCheckHandler.buildEvent(AgreementBuilder.AGREEMENT_ID,
            CapabilityGateMocker.ROOT_VERSION));

        events.awaitEnvelope(AgreementCapabilityStatusChangedHandler.EVENT_TYPE,
            AgreementCapabilityStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID, CapabilityGateMocker.ROOT_VERSION + 1)
                .eventKey());
        assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(CARD_ID));
    }

    /** 合同不存在与 PARTNER 合同为终态；事件键非法为终态。 */
    @Test
    void test_check_terminalFailures() {
        long partnerId = AgreementBuilder.AGREEMENT_ID + 800;
        gate.mockAgreement(AgreementBuilder.template().agreementId(partnerId).agreementNo("GATE-PARTNER-3")
            .agreementType(AgreementAgreementType.PARTNER).businessMode(null).fundMode(null).pricingAuthority(null)
            .clientId(null).entityNo(null).localPartnerId(930000000000985L).status(AgreementStatus.ACTIVE));

        CompensationHandlerResult missing = checkHandler.handle(checkEvent(partnerId + 1));
        CompensationHandlerResult partner = checkHandler.handle(checkEvent(partnerId));
        CompensationHandlerResult malformed = checkHandler.handle(new CompensationEvent(1L,
            "AGREEMENT:1:AgreementCapabilityStatusChanged:1", CapabilityPrerequisiteCheckHandler.EVENT_TYPE, "-", null, 1));

        CompensationHandlerResult rejected = CompensationHandlerResult.terminalFailure("PREREQUISITE_CHECK_REJECTED",
            "Agreement is not eligible for capability prerequisite check");
        assertAll(() -> assertEquals(rejected, missing), () -> assertEquals(rejected, partner),
            () -> assertEquals(CompensationHandlerResult.terminalFailure("PREREQUISITE_CHECK_EVENT_INVALID",
                "Capability prerequisite check event is malformed"), malformed),
            () -> assertEquals(0, compliance.count()));
    }

    /** 缺少 BCR Root（系统错误）与资格服务 503（结论 UNKNOWN）：可重试，执行状态不变。 */
    @Test
    void test_check_retryableFailures() {
        AgreementWriteMocker.Mocker agreement = gate.mockActiveAgreement();
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD, AgreementCapabilityExecutionStatus.ENABLED);
        compliance.mockProblem(503, "SERVICE_UNAVAILABLE");

        CompensationHandlerResult unavailable = checkHandler.handle(checkEvent(AgreementBuilder.AGREEMENT_ID));
        testMapper.deleteBusinessChangeRecord(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AgreementBuilder.AGREEMENT_ID);
        CompensationHandlerResult rootMissing = checkHandler.handle(checkEvent(AgreementBuilder.AGREEMENT_ID));

        assertAll(() -> assertEquals(CompensationHandlerResult.retryableFailure("PREREQUISITE_CHECK_UNAVAILABLE",
                "Qualification check unavailable, prerequisite check requires retry"), unavailable),
            () -> assertEquals(CompensationHandlerResult.retryableFailure("PREREQUISITE_CHECK_RETRY",
                "Capability prerequisite check requires retry"), rootMissing),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), executionStatus(CARD_ID)));
    }

    /**
     * 执行状态发布：第 6 版快照 ENABLED → SUSPENDED_BY_PREREQUISITE（门禁），上一版为旧格式快照（只有 status=ENABLED）时 from 按存量映射推出；
     * 提交后发布，信封版本为 6、changes 一条、触发来源 PREREQUISITE。
     */
    @Test
    void test_status_normalFlow_legacyPreviousSnapshot() {
        AgreementWriteMocker.Mocker agreement = gate.mockActiveAgreement();
        AgreementCapability capability = gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD,
            AgreementCapabilityExecutionStatus.ENABLED);
        testMapper.legacyCapabilitySnapshot(CARD_ID, CapabilityGateMocker.ROOT_VERSION);
        long version = gate.mockExecutionSnapshot(agreement, capability,
            AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE, BusinessChangeReason.AGREEMENT_CAPABILITY_EXECUTION_CHANGED);
        CompensationEventRequest request = AgreementCapabilityStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID,
            version);

        registerInTransaction(request);

        JsonNode envelope = events.awaitEnvelope(AgreementCapabilityStatusChangedHandler.EVENT_TYPE, request.eventKey());
        JsonNode payload = envelope.path("payload");
        JsonNode change = payload.path("changes").get(0);
        assertAll(() -> assertEquals(AgreementCapabilityStatusChangedPayload.EVENT_TYPE, envelope.path("eventType").asString()),
            () -> assertEquals("AGREEMENT", envelope.path("aggregateType").asString()),
            () -> assertEquals(version, envelope.path("aggregateVersion").asLong()),
            () -> assertEquals(Long.toString(version), payload.path("changeVersion").asString()),
            () -> assertEquals(Long.toString(AgreementBuilder.AGREEMENT_ID), payload.path("agreementId").asString()),
            () -> assertEquals(1, payload.path("changes").size()),
            () -> assertEquals(Long.toString(CARD_ID), change.path("agreementCapabilityId").asString()),
            () -> assertEquals(AgreementBuilder.ENTITY_NO, change.path("entityNo").asString()),
            () -> assertEquals(CommercialCodes.Capability.CARD, change.path("capabilityCode").asString()),
            () -> assertEquals("PAYMENT_METHOD", change.path("capabilityKind").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED.getCode(), change.path("fromExecutionStatus").asString()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE.getCode(),
                change.path("toExecutionStatus").asString()),
            () -> assertEquals("PREREQUISITE", change.path("trigger").asString()),
            () -> assertTrue(payload.path("occurredAt").asString().endsWith("Z")));
    }

    /** 该版本没有执行状态变化（只有 CREATE 快照的版本之外）、合同不存在、事件键非法：终态失败，不发布。 */
    @Test
    void test_status_notPublishable() {
        AgreementWriteMocker.Mocker agreement = gate.mockActiveAgreement();
        gate.mockCapability(agreement, CARD_ID, CommercialCodes.Capability.CARD, AgreementCapabilityExecutionStatus.ENABLED);
        String noChangeKey = AgreementCapabilityStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID,
            CapabilityGateMocker.ROOT_VERSION + 3).eventKey();
        String missingKey = AgreementCapabilityStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID + 1, 1).eventKey();

        CompensationHandlerResult noChange = statusHandler.handle(statusEvent(noChangeKey));
        CompensationHandlerResult missing = statusHandler.handle(statusEvent(missingKey));
        CompensationHandlerResult malformed = statusHandler.handle(statusEvent("AGREEMENT:1:AgreementStatusChanged:1"));

        CompensationHandlerResult notPublishable = CompensationHandlerResult.terminalFailure("CAPABILITY_STATUS_NOT_PUBLISHABLE",
            "Agreement has no capability execution change at the event version");
        assertAll(() -> assertEquals(notPublishable, noChange), () -> assertEquals(notPublishable, missing),
            () -> assertEquals(CompensationHandlerResult.terminalFailure("CAPABILITY_STATUS_EVENT_INVALID",
                "Capability status event is malformed"), malformed));
        events.assertNoEnvelope(noChangeKey);
    }

    /** 默认合同 Client 与 OE 资格合格。 */
    void mockQualified() {
        compliance.mockStatus(QualificationRoleType.CLIENT, Long.toString(AgreementBuilder.CLIENT_ID),
            QualificationEvaluationStatus.ELIGIBLE);
        compliance.mockStatus(QualificationRoleType.OPERATING_ENTITY, AgreementBuilder.ENTITY_NO,
            QualificationEvaluationStatus.ELIGIBLE);
    }

    /** 调用方事务内登记。 */
    void registerInTransaction(CompensationEventRequest request) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> registrar.register(request));
    }

    /** 前置检查任务输入。 */
    static CompensationEvent checkEvent(long agreementId) {
        return new CompensationEvent(1L, CapabilityPrerequisiteCheckHandler.buildEvent(agreementId, 1).eventKey(),
            CapabilityPrerequisiteCheckHandler.EVENT_TYPE, "-", null, 1);
    }

    /** 执行状态发布任务输入。 */
    static CompensationEvent statusEvent(String eventKey) {
        return new CompensationEvent(1L, eventKey, AgreementCapabilityStatusChangedHandler.EVENT_TYPE, "-", null, 1);
    }

    /** 数据库中的执行状态。 */
    String executionStatus(long agreementCapabilityId) {
        return (String) testMapper.agreementCapability(agreementCapabilityId).get("execution_status");
    }
}
