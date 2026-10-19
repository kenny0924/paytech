package com.paytech.commercial.auto.ext.http.agreement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.agreement.AgreementApi;
import com.paytech.commercial.api.agreement.request.AgreementTransitionRequestBody;
import com.paytech.commercial.api.agreement.request.RecordAgreementAcceptanceRequestBody;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.service.handler.AgreementStatusChangedHandler;
import com.paytech.commercial.test.base.write.AbstractCommercialWriteApiTest;
import com.paytech.commercial.test.builder.AgreementAcceptanceBodyBuilder;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.AgreementDocumentRefBuilder;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.commercial.test.builder.CreateAgreementBodyBuilder;
import com.paytech.commercial.test.checker.write.BusinessChangeChecker;
import com.paytech.commercial.test.mocker.local.agreement.AgreementWriteMocker;

/**
 * 提交签署与签署登记（technique.md §2.2）：状态条件推进、同版本重放、版本冲突、签署文件 / 覆盖 OE 前置条件、签署方式矩阵、
 * acceptanceReference 唯一与重放、同 Party 多角色原子登记、全部签完转 SIGNED
 *
 * @see AgreementApi#submitForSignature(String, String, AgreementTransitionRequestBody)
 * @see AgreementApi#recordAcceptance(String, String, String, RecordAgreementAcceptanceRequestBody)
 */
class AgreementSigningHttpTest extends AbstractCommercialWriteApiTest {
    private static final long AGREEMENT_ID = AgreementBuilder.AGREEMENT_ID;

    private static final String BASE = "/agreements/v1/" + AGREEMENT_ID;

    private static final long CLIENT_PARTY = AgreementPartyBuilder.PARTY_ID;

    private static final long PLATFORM_PARTY = Long.parseLong(CreateAgreementBodyBuilder.PLATFORM_PARTY_ID);

    /** DRAFT 合同（版本 1）提交签署：PENDING_SIGNATURE、版本 2、Root UPDATE 快照，不登记发布任务。 */
    @Test
    void test_submit_normalFlow() {
        mockAgreement(AgreementStatus.DRAFT, 1).document(AgreementDocumentRefBuilder.template());

        post(BASE + "/submit-for-signature", transition(1)).assertOk()
            .assertText("status", AgreementStatus.PENDING_SIGNATURE.getCode()).assertVersion(2);

        assertAll(() -> assertEquals(AgreementStatus.PENDING_SIGNATURE.getCode(), testMapper.agreement(AGREEMENT_ID).get("status")),
            () -> assertEquals(0, testMapper.countCompensationEvents(AgreementStatusChangedHandler.EVENT_TYPE)));
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertCurrentVersion(2)
            .assertVersions("2:UPDATE").assertLastReason(BusinessChangeReason.AGREEMENT_SUBMITTED_FOR_SIGNATURE.getCode())
            .assertLastData("status", AgreementStatus.PENDING_SIGNATURE.getCode()).assertIntegrity();
    }

    /** 同版本重放返回已提交结果，不再推进；过期版本 409。 */
    @Test
    void test_submit_replayAndStaleVersion() {
        mockAgreement(AgreementStatus.DRAFT, 1).document(AgreementDocumentRefBuilder.template());
        post(BASE + "/submit-for-signature", transition(1)).assertOk();

        post(BASE + "/submit-for-signature", transition(1)).assertOk().assertVersion(2);
        post(BASE + "/submit-for-signature", transition(3)).assertConflict();

        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertVersions("2:UPDATE");
    }

    /** 缺签署文件、PARTNER 无覆盖 OE、非 DRAFT 状态：业务检查失败且状态不变。 */
    @Test
    void test_submit_businessRuleViolation() {
        mockAgreement(AgreementStatus.DRAFT, 1);
        post(BASE + "/submit-for-signature", transition(1)).assertBusinessCheckFailed("恰好一份 SIGNING_DOCUMENT");

        long partnerId = AGREEMENT_ID + 100;
        agreements.mock(AgreementBuilder.template().agreementId(partnerId).agreementNo("B-P-1")
            .agreementType(AgreementAgreementType.PARTNER).businessMode(null).fundMode(null).pricingAuthority(null)
            .clientId(null).entityNo(null).localPartnerId(Long.parseLong(CreateAgreementBodyBuilder.LOCAL_PARTNER_ID)), 1)
            .document(AgreementDocumentRefBuilder.template().agreementDocumentId(930000000000121L));
        post("/agreements/v1/" + partnerId + "/submit-for-signature", transition(1))
            .assertBusinessCheckFailed("至少覆盖一个 OE");

        long pendingId = AGREEMENT_ID + 200;
        agreements.mock(AgreementBuilder.template().agreementId(pendingId).agreementNo("B-PENDING-1")
            .status(AgreementStatus.PENDING_SIGNATURE), 1);
        post("/agreements/v1/" + pendingId + "/submit-for-signature", transition(1)).assertBusinessCheckFailed("只有 DRAFT");

        assertEquals(AgreementStatus.DRAFT.getCode(), testMapper.agreement(AGREEMENT_ID).get("status"));
    }

    /**
     * Client 点击接受后仍待签；平台电子签补齐后合同 SIGNED，签署时刻取两次接受时刻的最大值，执行文件新增为唯一 EXECUTED_DOCUMENT
     */
    @Test
    void test_acceptance_normalFlow_signedWhenAllPartiesAccepted() {
        mockPending(2);

        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.click(2, "ACC-CLIENT-1").build())
            .assertOk().assertText("status", AgreementStatus.PENDING_SIGNATURE.getCode()).assertVersion(3);
        post(BASE + "/parties/" + PLATFORM_PARTY + "/acceptance",
            AgreementAcceptanceBodyBuilder.eSignature(3, "ACC-PLATFORM-1").build())
            .assertOk().assertText("status", AgreementStatus.SIGNED.getCode()).assertVersion(4);

        List<Map<String, Object>> parties = testMapper.agreementParties(AGREEMENT_ID);
        List<Map<String, Object>> documents = testMapper.agreementDocuments(AGREEMENT_ID);
        Map<String, Object> executed = documents.stream()
            .filter(row -> AgreementDocumentRefDocumentType.EXECUTED_DOCUMENT.getCode().equals(row.get("document_type")))
            .findFirst().orElseThrow();
        assertAll(() -> assertEquals(AgreementPartyAcceptanceMethod.CLICK_ACCEPTANCE.getCode(),
                parties.get(0).get("acceptance_method")),
            () -> assertEquals(AgreementDocumentRefBuilder.AGREEMENT_DOCUMENT_ID,
                ((Number) parties.get(0).get("evidence_document_id")).longValue()),
            () -> assertEquals(ACTOR_ID, ((Number) parties.get(0).get("acceptance_recorded_by")).longValue()),
            // TINYINT(1) 经 JDBC 读回为 Boolean，true 即 slot=1。
            () -> assertEquals(Boolean.TRUE, parties.get(0).get("acceptance_ref_slot")),
            () -> assertEquals(AgreementPartyAcceptanceMethod.E_SIGNATURE.getCode(), parties.get(1).get("acceptance_method")),
            () -> assertEquals(((Number) executed.get("agreement_document_id")).longValue(),
                ((Number) parties.get(1).get("evidence_document_id")).longValue()),
            () -> assertEquals(Long.parseLong(AgreementAcceptanceBodyBuilder.EXECUTED_FILE_ID),
                ((Number) executed.get("file_id")).longValue()),
            () -> assertEquals(AgreementStatus.SIGNED.getCode(), testMapper.agreement(AGREEMENT_ID).get("status")),
            () -> assertEquals(Instant.parse("2026-09-11T11:30:00Z"),
                BusinessChangeChecker.instant(testMapper.agreement(AGREEMENT_ID).get("signed_time"))));
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertCurrentVersion(4)
            .assertVersions("3:UPDATE", "4:UPDATE").assertLastReason(BusinessChangeReason.AGREEMENT_PARTY_ACCEPTED.getCode())
            .assertLastData("status", AgreementStatus.SIGNED.getCode()).assertIntegrity();
    }

    /** 同 Party 的两个角色一次原子登记，只有最小主键行占 slot=1；合同仍有其他 Party 待签。 */
    @Test
    void test_acceptance_multiRolePartyAtomic() {
        mockPending(2).party(AgreementPartyBuilder.template().agreementPartyId(930000000000013L)
            .partyRole(AgreementPartyPartyRole.OPERATING_ENTITY));

        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.click(2, "ACC-MULTI-1").build())
            .assertOk().assertVersion(3);

        List<Map<String, Object>> roles = testMapper.agreementParties(AGREEMENT_ID).stream()
            .filter(row -> ((Number) row.get("party_id")).longValue() == CLIENT_PARTY).toList();
        assertAll(() -> assertEquals(2, roles.size()),
            () -> assertEquals("ACC-MULTI-1", roles.get(0).get("acceptance_reference")),
            () -> assertEquals("ACC-MULTI-1", roles.get(1).get("acceptance_reference")),
            () -> assertEquals(Boolean.TRUE, roles.get(0).get("acceptance_ref_slot")),
            () -> assertNull(roles.get(1).get("acceptance_ref_slot")),
            () -> assertEquals(AgreementStatus.PENDING_SIGNATURE.getCode(), testMapper.agreement(AGREEMENT_ID).get("status")));
    }

    /** 同引用同内容重放返回当前结果；同引用改 Party 或改接受时刻为 409。 */
    @Test
    void test_acceptance_replay() {
        mockPending(2);
        RecordAgreementAcceptanceRequestBody first = AgreementAcceptanceBodyBuilder.click(2, "ACC-REPLAY-1").build();
        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", first).assertOk().assertVersion(3);

        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", first).assertOk().assertVersion(3);
        post(BASE + "/parties/" + PLATFORM_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.click(3, "ACC-REPLAY-1")
            .build()).assertConflict();
        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.click(2, "ACC-REPLAY-1")
            .acceptedAt("2026-09-11T10:00:00Z").build()).assertConflict();

        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertVersions("3:UPDATE");
        assertNull(testMapper.agreementParties(AGREEMENT_ID).get(1).get("accepted_time"));
    }

    /** 非参与方、已签 Party、未来接受时刻、非待签状态与过期版本均拒绝且不写入。 */
    @Test
    void test_acceptance_businessRuleViolation() {
        mockPending(2);
        post(BASE + "/parties/930000000000999/acceptance", AgreementAcceptanceBodyBuilder.click(2, "ACC-X-1").build())
            .assertBusinessCheckFailed("不是该合同参与方");
        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.click(2, "ACC-X-2")
            .acceptedAt("2026-09-11T13:00:00Z").build()).assertBusinessCheckFailed("接受时刻不能晚于登记时刻");
        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.click(1, "ACC-X-3").build())
            .assertConflict();
        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.click(2, "ACC-X-4").build())
            .assertOk();
        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.click(3, "ACC-X-5").build())
            .assertBusinessCheckFailed("已登记签署结果");

        long draftId = AGREEMENT_ID + 300;
        agreements.mock(AgreementBuilder.template().agreementId(draftId).agreementNo("B-DRAFT-2"), 1)
            .party(AgreementPartyBuilder.template().agreementPartyId(930000000000301L));
        post("/agreements/v1/" + draftId + "/parties/" + CLIENT_PARTY + "/acceptance",
            AgreementAcceptanceBodyBuilder.click(1, "ACC-X-6").build()).assertBusinessCheckFailed("只有 PENDING_SIGNATURE");

        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertVersions("3:UPDATE");
    }

    /** 签署方式矩阵：点击接受须有接受 Actor 且无执行文件，电子签须有执行文件，已有执行文件时不得换文件。 */
    @Test
    void test_acceptance_methodMatrix() {
        mockPending(2);
        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.click(2, "ACC-M-1")
            .acceptedByActorId(null).build()).assertBusinessCheckFailed("CLICK_ACCEPTANCE 必须提供 acceptedByActorId");
        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.eSignature(2, "ACC-M-2")
            .executedDocument(null, null).build()).assertBusinessCheckFailed("必须提供 executedDocument");
        post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.eSignature(2, "ACC-M-3")
            .build()).assertOk();

        post(BASE + "/parties/" + PLATFORM_PARTY + "/acceptance", AgreementAcceptanceBodyBuilder.eSignature(3, "ACC-M-4")
            .executedDocument("930000000000936", "c".repeat(64)).build()).assertBusinessCheckFailed("已有不同的 EXECUTED_DOCUMENT");

        assertEquals(1, testMapper.agreementDocuments(AGREEMENT_ID).stream()
            .filter(row -> AgreementDocumentRefDocumentType.EXECUTED_DOCUMENT.getCode().equals(row.get("document_type")))
            .count());
    }

    /** 同一合同、同一期望版本的两个签署并发提交：资源锁串行化后恰好一个成功、另一个版本冲突，只写一次快照。 */
    @Test
    void test_concurrentRequests_serializedByLockAndVersion() throws Exception {
        mockPending(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> client = pool.submit(() -> post(BASE + "/parties/" + CLIENT_PARTY + "/acceptance",
                AgreementAcceptanceBodyBuilder.click(2, "ACC-RACE-1").build()).status());
            Future<Integer> platform = pool.submit(() -> post(BASE + "/parties/" + PLATFORM_PARTY + "/acceptance",
                AgreementAcceptanceBodyBuilder.click(2, "ACC-RACE-2").build()).status());

            assertEquals(List.of(200, 409), Stream.of(client.get(30, TimeUnit.SECONDS), platform.get(30, TimeUnit.SECONDS))
                .sorted().toList());
        } finally {
            pool.shutdownNow();
        }
        history.record(BusinessChangeRecordRecordType.AGREEMENT.getCode(), AGREEMENT_ID).assertCurrentVersion(3)
            .assertVersions("3:UPDATE");
    }

    /** 两个签约方（Client 与平台）的合同，状态与 Root 版本由用例指定。 */
    AgreementWriteMocker.Mocker mockAgreement(AgreementStatus status, long version) {
        return agreements.mock(AgreementBuilder.template().status(status), version)
            .party(AgreementPartyBuilder.template())
            .party(AgreementPartyBuilder.template().agreementPartyId(930000000000012L).partyId(PLATFORM_PARTY)
                .partyProfileId(Long.parseLong(CreateAgreementBodyBuilder.PLATFORM_PROFILE_ID))
                .partyRole(AgreementPartyPartyRole.PLATFORM_SIGNATORY));
    }

    /** 待签合同，含唯一签署文件。 */
    AgreementWriteMocker.Mocker mockPending(long version) {
        return mockAgreement(AgreementStatus.PENDING_SIGNATURE, version).document(AgreementDocumentRefBuilder.template());
    }

    /** 状态推进请求体。 */
    static AgreementTransitionRequestBody transition(long expectedVersion) {
        AgreementTransitionRequestBody body = new AgreementTransitionRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        return body;
    }
}
