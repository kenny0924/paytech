package com.paytech.commercial.auto.ext.http.agreement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.agreement.request.AgreementFileEntry;
import com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody;
import com.paytech.commercial.api.agreement.request.TerminateAgreementRequestBody;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.event.AgreementStatusChangedPayload;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.service.handler.AgreementStatusChangedHandler;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.checker.write.BusinessChangeChecker;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * 合同激活与终止（technique.md §2.2、§2.8）：状态条件推进、同版本重放、版本冲突、生效区间与同 Owner 区间冲突、保存失败整体回滚，
 * 以及提交后经 compensation_event 发布 AgreementStatusChanged（真实本地 Broker 观察）
 *
 * @see AgreementApi#activate(String, String, AgreementTransitionRequestBody)
 * @see AgreementApi#terminate(String, String, TerminateAgreementRequestBody)
 */
class AgreementLifecycleHttpTest extends AbstractCommercialWriteApiTest {
    private static final long AGREEMENT_ID = AgreementBuilder.AGREEMENT_ID;

    private static final String BASE = "/agreements/v1/" + AGREEMENT_ID;

    private static final Instant SIGNED_TIME = Instant.parse("2026-09-10T00:00:00Z");

    /** SIGNED（版本 4）激活：ACTIVE、激活时刻为当前业务时点、版本 5；提交后发布事件，信封版本与载荷状态一致。 */
    @Test
    void test_activate_normalFlow_eventPublished() {
        agreements.mock(signed(), 4);

        post(BASE + "/activate", transition(4)).assertOk().assertText("status", AgreementStatus.ACTIVE.getCode())
            .assertVersion(5);

        JsonNode envelope = events.awaitEnvelope(AgreementStatusChangedHandler.EVENT_TYPE, eventKey(5));
        JsonNode payload = envelope.path("payload");
        assertAll(() -> assertEquals(AgreementStatusChangedPayload.EVENT_TYPE, envelope.path("eventType").asString()),
            () -> assertEquals(5, envelope.path("aggregateVersion").asLong()),
            () -> assertEquals(AgreementStatus.ACTIVE.getCode(), payload.path("status").asString()),
            () -> assertEquals("5", payload.path("changeVersion").asString()),
            () -> assertEquals(Long.toString(AGREEMENT_ID), payload.path("agreementId").asString()),
            () -> assertEquals(CheckoutFixture.NOW,
                BusinessChangeChecker.instant(testMapper.agreement(AGREEMENT_ID).get("activated_time"))));
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertCurrentVersion(5)
            .assertVersions("5:UPDATE").assertLastReason(BusinessChangeReason.AGREEMENT_ACTIVATED.getCode())
            .assertLastData("status", AgreementStatus.ACTIVE.getCode()).assertIntegrity();
    }

    /** 同版本重放返回已激活结果，不再登记第二个发布任务；过期版本 409。 */
    @Test
    void test_activate_replayAndStaleVersion() {
        agreements.mock(signed(), 4);
        post(BASE + "/activate", transition(4)).assertOk();
        events.awaitEnvelope(AgreementStatusChangedHandler.EVENT_TYPE, eventKey(5));

        post(BASE + "/activate", transition(4)).assertOk().assertVersion(5);
        post(BASE + "/activate", transition(3)).assertConflict();

        assertAll(() -> assertEquals(0, testMapper.countCompensationEvents(AgreementStatusChangedHandler.EVENT_TYPE)),
            () -> events.assertNoEnvelope(eventKey(6)));
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertVersions("5:UPDATE");
    }

    /** 非 SIGNED、生效起点未到、同 Client / OE / 业务模式已有 ACTIVE 合同：拒绝且不发布。 */
    @Test
    void test_activate_businessRuleViolation() {
        long draftId = AGREEMENT_ID + 100;
        agreements.mock(AgreementBuilder.template().agreementId(draftId).agreementNo("B-DRAFT-A"), 1);
        post("/agreements/v1/" + draftId + "/activate", transition(1)).assertBusinessCheckFailed("只有 SIGNED");

        long futureId = AGREEMENT_ID + 200;
        agreements.mock(signed().agreementId(futureId).agreementNo("B-FUTURE-A")
            .effectiveFrom(Instant.parse("2026-10-01T00:00:00Z")), 4);
        post("/agreements/v1/" + futureId + "/activate", transition(4)).assertBusinessCheckFailed("生效起点未到");

        agreements.mock(active().agreementId(AGREEMENT_ID + 300).agreementNo("B-ACTIVE-OTHER"), 5);
        agreements.mock(signed(), 4);
        post(BASE + "/activate", transition(4)).assertBusinessCheckFailed("与已生效合同区间冲突");

        assertAll(() -> assertEquals(AgreementStatus.SIGNED.getCode(), testMapper.agreement(AGREEMENT_ID).get("status")),
            () -> assertEquals(0, testMapper.countCompensationEvents(AgreementStatusChangedHandler.EVENT_TYPE)),
            () -> events.assertNoEnvelope(eventKey(5)));
    }

    /** 同 Client / OE 的 PSP 合同与 MOR 合同可以同时生效（一份合同一个业务模式）。 */
    @Test
    void test_edgeCase_otherBusinessModeNotConflicting() {
        agreements.mock(active().agreementId(AGREEMENT_ID + 300).agreementNo("B-ACTIVE-PSP").businessMode(BusinessMode.PSP), 5);
        agreements.mock(signed(), 4);

        post(BASE + "/activate", transition(4)).assertOk().assertText("status", AgreementStatus.ACTIVE.getCode());

        events.awaitEnvelope(AgreementStatusChangedHandler.EVENT_TYPE, eventKey(5));
    }

    /** 保存阶段在状态更新与 Root CAS 之后失败（快照坐标被占用）：整体回滚，状态、版本不变，不登记也不发布事件。 */
    @Test
    void test_rollback_noPartialWriteNoEvent() {
        agreements.mock(signed(), 4);
        testMapper.occupyBusinessChangeVersion(930000000000777L, AGREEMENT_ID + 7, 5);

        post(BASE + "/activate", transition(4)).assertProblem(500, CommonErrorCode.INTERNAL_ERROR.getCode());

        assertAll(() -> assertEquals(AgreementStatus.SIGNED.getCode(), testMapper.agreement(AGREEMENT_ID).get("status")),
            () -> assertEquals(0, testMapper.countCompensationEvents(AgreementStatusChangedHandler.EVENT_TYPE)),
            () -> events.assertNoEnvelope(eventKey(5)));
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertCurrentVersion(4);
    }

    /** ACTIVE（版本 5）终止：TERMINATED、终点写入、新增终止通知文件、版本 6；提交后发布事件。 */
    @Test
    void test_terminate_normalFlow_eventPublished() {
        agreements.mock(active(), 5);

        post(BASE + "/terminate", terminate(5, "2026-12-31T00:00:00Z")).assertOk()
            .assertText("status", AgreementStatus.TERMINATED.getCode()).assertVersion(6);

        JsonNode payload = events.awaitEnvelope(AgreementStatusChangedHandler.EVENT_TYPE, eventKey(6)).path("payload");
        assertAll(() -> assertEquals(AgreementStatus.TERMINATED.getCode(), payload.path("status").asString()),
            () -> assertEquals("2026-12-31T00:00:00Z", payload.path("effectiveTo").asString()),
            () -> assertEquals(Instant.parse("2026-12-31T00:00:00Z"),
                BusinessChangeChecker.instant(testMapper.agreement(AGREEMENT_ID).get("effective_to"))),
            () -> assertEquals(1, testMapper.agreementDocuments(AGREEMENT_ID).stream()
                .filter(row -> AgreementDocumentRefDocumentType.TERMINATION_NOTICE.getCode().equals(row.get("document_type")))
                .count()));
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertCurrentVersion(6)
            .assertLastReason(BusinessChangeReason.AGREEMENT_TERMINATED.getCode())
            .assertLastData("status", AgreementStatus.TERMINATED.getCode()).assertIntegrity();
    }

    /** 回溯终点、非 ACTIVE 状态与过期版本拒绝；同版本重放返回已终止结果。 */
    @Test
    void test_terminate_businessRuleViolationAndReplay() {
        agreements.mock(active(), 5);
        post(BASE + "/terminate", terminate(5, "2026-09-10T00:00:00Z")).assertBusinessCheckFailed("终止终点必须不早于当前时刻");
        post(BASE + "/terminate", terminate(4, "2026-12-31T00:00:00Z")).assertConflict();
        post(BASE + "/terminate", terminate(5, "2026-12-31T00:00:00Z")).assertOk();
        events.awaitEnvelope(AgreementStatusChangedHandler.EVENT_TYPE, eventKey(6));

        post(BASE + "/terminate", terminate(5, "2026-12-31T00:00:00Z")).assertOk().assertVersion(6);
        post(BASE + "/terminate", terminate(6, "2026-12-30T00:00:00Z")).assertBusinessCheckFailed("只有 ACTIVE");

        assertEquals(1, testMapper.agreementDocuments(AGREEMENT_ID).size());
    }

    /** SIGNED 合同：签署完成于激活前一日。 */
    static AgreementBuilder signed() {
        return AgreementBuilder.template().status(AgreementStatus.SIGNED).signedTime(SIGNED_TIME);
    }

    /** ACTIVE 合同。 */
    static AgreementBuilder active() {
        return signed().status(AgreementStatus.ACTIVE).activatedTime(SIGNED_TIME.plusSeconds(3600));
    }

    /** 本合同某版本的事件键。 */
    static String eventKey(long version) {
        return AgreementStatusChangedHandler.buildEvent(AGREEMENT_ID, version).eventKey();
    }

    /** 状态推进请求体。 */
    static AgreementTransitionRequestBody transition(long expectedVersion) {
        AgreementTransitionRequestBody body = new AgreementTransitionRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        return body;
    }

    /** 终止请求体。 */
    static TerminateAgreementRequestBody terminate(long expectedVersion, String effectiveTo) {
        AgreementFileEntry notice = new AgreementFileEntry();
        notice.setFileId("930000000000941");
        notice.setContentHash("d".repeat(64));
        TerminateAgreementRequestBody body = new TerminateAgreementRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        body.setEffectiveTo(effectiveTo);
        body.setTerminationNotice(notice);
        return body;
    }
}
