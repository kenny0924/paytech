package com.paytech.commercial.auto.ext.eventhandler;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.api.event.AgreementStatusChangedPayload;
import com.paytech.commercial.api.event.CommercialTermVersionActivatedPayload;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.service.handler.AgreementStatusChangedHandler;
import com.paytech.commercial.service.handler.CommercialTermVersionActivatedHandler;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.local.agreement.CommercialGraphMocker;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

import tools.jackson.databind.JsonNode;

/**
 * 两个发布型补偿 Handler 入口（technique.md §2.8）
 * <li>正常流程：以调用方事务经真实 CompensationEventRegistrar 登记任务，提交后由 Foundation 唤醒 Handler，回读本库并发布到真实本地
 * Broker 的 commercial.events；断言信封身份与 Payload 字段。</li>
 * <li>根修订已前进、事件键非法：直接以补偿任务输入调用 Handler，终态失败且不发布。</li>
 *
 * @see AgreementStatusChangedHandler#handle(CompensationEvent)
 * @see CommercialTermVersionActivatedHandler#handle(CompensationEvent)
 */
class CommercialEventPublishHandlerTest extends CommercialMockTestBase {
    /** 合同根修订版本。 */
    private static final long AGREEMENT_VERSION = 3;

    /** 方案根修订版本。 */
    private static final long PLAN_VERSION = 5;

    /** 第 2 版生效、第 1 版被替代的切换时点。 */
    private static final Instant SWITCH_TIME = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private CommercialGraphMocker graph;

    @Autowired
    private CommercialTestMapper testMapper;

    @Autowired
    private CompensationEventRegistrar registrar;

    @Autowired
    private DataSourceTransactionManager transactionManager;

    @Autowired
    private AgreementStatusChangedHandler agreementHandler;

    @Autowired
    private CommercialTermVersionActivatedHandler termHandler;

    /** ACTIVE 合同：提交后发布 AgreementStatusChanged，信封版本为 AGREEMENT 根修订，Payload 标量均为字符串。 */
    @Test
    void test_normalFlow_agreementStatusChanged() {
        mockActiveAgreement(AGREEMENT_VERSION);
        CompensationEventRequest request = AgreementStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID,
            AGREEMENT_VERSION);

        registerInTransaction(request);

        JsonNode envelope = events.awaitEnvelope(AgreementStatusChangedHandler.EVENT_TYPE, request.eventKey());
        JsonNode payload = envelope.path("payload");
        assertAll(() -> assertEnvelope(envelope, AgreementStatusChangedPayload.EVENT_TYPE,
                AgreementStatusChangedPayload.AGGREGATE_TYPE, AgreementBuilder.AGREEMENT_ID, AGREEMENT_VERSION),
            () -> assertEquals(Long.toString(AgreementBuilder.AGREEMENT_ID), payload.path("agreementId").asString()),
            () -> assertEquals(AgreementBuilder.AGREEMENT_NO, payload.path("agreementNo").asString()),
            () -> assertEquals(Long.toString(AgreementBuilder.CLIENT_ID), payload.path("clientId").asString()),
            () -> assertTrue(payload.path("localPartnerId").isNull()),
            () -> assertEquals(AgreementBuilder.ENTITY_NO, payload.path("entityNo").asString()),
            () -> assertEquals(AgreementStatus.ACTIVE.getCode(), payload.path("status").asString()),
            () -> assertTrue(payload.path("effectiveTo").isNull()),
            () -> assertEquals(Long.toString(AGREEMENT_VERSION), payload.path("changeVersion").asString()));
    }

    /** 第 2 版生效：提交后发布 CommercialTermVersionActivated，被替代版本为生效终点等于新起点的第 1 版。 */
    @Test
    void test_normalFlow_termVersionActivated() {
        mockActivatedPlan();
        CompensationEventRequest request = CommercialTermVersionActivatedHandler.buildEvent(
            CommercialTermPlanBuilder.TERM_PLAN_ID, PLAN_VERSION);

        registerInTransaction(request);

        JsonNode envelope = events.awaitEnvelope(CommercialTermVersionActivatedHandler.EVENT_TYPE, request.eventKey());
        JsonNode payload = envelope.path("payload");
        assertAll(() -> assertEnvelope(envelope, CommercialTermVersionActivatedPayload.EVENT_TYPE,
                CommercialTermVersionActivatedPayload.AGGREGATE_TYPE, CommercialTermPlanBuilder.TERM_PLAN_ID, PLAN_VERSION),
            () -> assertEquals(Long.toString(CommercialTermPlanBuilder.TERM_PLAN_ID), payload.path("planId").asString()),
            () -> assertEquals(Long.toString(AgreementBuilder.AGREEMENT_ID), payload.path("agreementId").asString()),
            () -> assertEquals(Long.toString(CommercialTermVersionBuilder.TERM_VERSION_ID + 1),
                payload.path("versionId").asString()),
            () -> assertEquals("2", payload.path("versionNo").asString()),
            () -> assertEquals(SWITCH_TIME.toString(), payload.path("effectiveFrom").asString()),
            () -> assertEquals(Long.toString(CommercialTermVersionBuilder.TERM_VERSION_ID),
                payload.path("supersededVersionId").asString()),
            () -> assertEquals(Long.toString(PLAN_VERSION), payload.path("changeVersion").asString()));
    }

    /** 发布前聚合又被修改（根修订已前进）或聚合不存在：终态失败，不发布。 */
    @Test
    void test_businessRuleViolation_versionDrift() {
        mockActiveAgreement(AGREEMENT_VERSION + 1);
        String agreementKey = AgreementStatusChangedHandler.buildEvent(AgreementBuilder.AGREEMENT_ID, AGREEMENT_VERSION)
            .eventKey();
        String missingPlanKey = CommercialTermVersionActivatedHandler.buildEvent(CommercialTermPlanBuilder.TERM_PLAN_ID,
            PLAN_VERSION).eventKey();

        CompensationHandlerResult drift = agreementHandler.handle(event(agreementKey, AgreementStatusChangedHandler.EVENT_TYPE));
        CompensationHandlerResult missing = termHandler.handle(
            event(missingPlanKey, CommercialTermVersionActivatedHandler.EVENT_TYPE));

        assertAll(() -> assertEquals(CompensationHandlerResult.terminalFailure("AGREEMENT_STATUS_NOT_PUBLISHABLE",
                "Agreement is not at the event change version"), drift),
            () -> assertEquals(CompensationHandlerResult.terminalFailure("TERM_VERSION_NOT_PUBLISHABLE",
                "Term plan is not at the event change version"), missing));
        events.assertNoEnvelope(agreementKey);
    }

    /** 事件键聚合类型、事件类型、形状或子类型不符，以及带 payloadJson：终态失败。 */
    @Test
    void test_invalidParameter_malformedEventKey() {
        String type = AgreementStatusChangedHandler.EVENT_TYPE;
        CompensationHandlerResult wrongAggregate = agreementHandler.handle(event("COMMERCIAL_TERM_PLAN:1:AgreementStatusChanged:1", type));
        CompensationHandlerResult wrongEvent = agreementHandler.handle(event("AGREEMENT:1:CommercialTermVersionActivated:1", type));
        CompensationHandlerResult wrongShape = agreementHandler.handle(event("AGREEMENT:1", type));
        CompensationHandlerResult wrongSubType = agreementHandler.handle(
            new CompensationEvent(1L, "AGREEMENT:1:AgreementStatusChanged:1", type, "OTHER", null, 1));
        CompensationHandlerResult withPayload = agreementHandler.handle(
            new CompensationEvent(1L, "AGREEMENT:1:AgreementStatusChanged:1", type, "-", "{}", 1));
        CompensationHandlerResult termWrongAggregate = termHandler.handle(
            event("AGREEMENT:1:CommercialTermVersionActivated:1", CommercialTermVersionActivatedHandler.EVENT_TYPE));

        CompensationHandlerResult invalid = CompensationHandlerResult.terminalFailure("AGREEMENT_STATUS_EVENT_INVALID",
            "Agreement status event is malformed");
        assertAll(() -> assertEquals(invalid, wrongAggregate), () -> assertEquals(invalid, wrongEvent),
            () -> assertEquals(invalid, wrongShape), () -> assertEquals(invalid, wrongSubType),
            () -> assertEquals(invalid, withPayload),
            () -> assertEquals(CompensationHandlerResult.terminalFailure("TERM_VERSION_EVENT_INVALID",
                "Term version event is malformed"), termWrongAggregate));
    }

    /** ACTIVE 合同与指定版本的 AGREEMENT 根。 */
    private void mockActiveAgreement(long rootVersion) {
        graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE)
            .activatedTime(Instant.parse("2026-09-01T00:00:00Z")));
        testMapper.insertBusinessChangeRoot(940000000000001L, BusinessChangeRecordAggregateRootType.AGREEMENT.getCode(),
            AgreementBuilder.AGREEMENT_ID, rootVersion);
    }

    /** ACTIVE 方案：第 1 版 SUPERSEDED 于切换时点，第 2 版自切换时点 ACTIVE；方案根修订为 PLAN_VERSION。 */
    private void mockActivatedPlan() {
        graph.mockAgreement(AgreementBuilder.template().status(AgreementStatus.ACTIVE))
            .plan(CommercialTermPlanBuilder.template().status(CommercialTermPlanStatus.ACTIVE))
            .version(CommercialTermVersionBuilder.template().status(CommercialTermVersionStatus.SUPERSEDED)
                .effectiveTo(SWITCH_TIME))
            .version(CommercialTermVersionBuilder.template().termVersionId(CommercialTermVersionBuilder.TERM_VERSION_ID + 1)
                .versionNo(2L).status(CommercialTermVersionStatus.ACTIVE).activeSlot(1).effectiveFrom(SWITCH_TIME));
        testMapper.insertBusinessChangeRoot(940000000000002L,
            BusinessChangeRecordAggregateRootType.COMMERCIAL_TERM_PLAN.getCode(), CommercialTermPlanBuilder.TERM_PLAN_ID,
            PLAN_VERSION);
    }

    /** 模拟写入方：在本地事务内登记任务，提交后由 Foundation 唤醒 Handler。 */
    private void registerInTransaction(CompensationEventRequest request) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> registrar.register(request));
    }

    /** 信封身份：eventId 即事件键，聚合版本为根修订，发布方为 commercial。 */
    private static void assertEnvelope(JsonNode envelope, String eventType, String aggregateType, long aggregateId,
        long aggregateVersion) {
        assertAll(() -> assertEquals(eventType, envelope.path("eventType").asString()),
            () -> assertEquals(1, envelope.path("schemaVersion").asInt()),
            () -> assertEquals(aggregateType, envelope.path("aggregateType").asString()),
            () -> assertEquals(Long.toString(aggregateId), envelope.path("aggregateId").asString()),
            () -> assertEquals(aggregateVersion, envelope.path("aggregateVersion").asLong()),
            () -> assertEquals("commercial", envelope.path("sourceService").asString()),
            () -> assertEquals("main", envelope.path("instanceId").asString()));
    }

    /** 补偿任务输入；记录 ID 与执行次数不影响 Handler。 */
    private static CompensationEvent event(String eventKey, String eventType) {
        return new CompensationEvent(1L, eventKey, eventType, "-", null, 1);
    }
}
