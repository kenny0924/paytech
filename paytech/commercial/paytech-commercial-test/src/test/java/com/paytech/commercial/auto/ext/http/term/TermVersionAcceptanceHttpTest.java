package com.paytech.commercial.auto.ext.http.term;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.term.CommercialTermApi;
import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.AgreementPartyBuilder;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * 价格协议客户签署（开通与定价模型调整 technique.md §4.6，AC-08）：APPROVED 未签署不能排期；登记签署（附件文件 ID + SHA-256 + 方式 + 时刻 +
 * 编号）后为 CLIENT_SIGNED 并可排期；同编号同内容重放、不同内容 409；签署方须为合同 CLIENT 角色；非 APPROVED 拒绝；存量免签版本 APPROVED 可直接排期。
 * Partner 方案的签署见 CommercialPriceResolutionHttpTest 的 Partner 条款用例。
 *
 * @see CommercialTermApi#recordAcceptance(String, String, com.paytech.commercial.api.term.request.RecordTermVersionAcceptanceRequestBody)
 */
class TermVersionAcceptanceHttpTest extends AbstractCommercialTermApiTest {
    private static final String BIZ = CommonErrorCode.BUSINESS_CHECK_FAILED.getCode();

    /** 每例：标准前置数据。 */
    @BeforeEach
    void setUpClassMocks() {
        mockBaseline();
    }

    /** 未签署排期被拒；登记签署后 CLIENT_SIGNED、签署记录随版本返回、快照记签署事实，然后可排期与激活。 */
    @Test
    void test_normalFlow() {
        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()),
            price("USD", CARD, 1, percentage("MDR", "0.025"))));
        assertProblem(scheduleResult(approvedVersion, NOW), 400, BIZ, "价格协议尚未签署");

        JsonNode signedVersion = signed(approvedVersion);

        long versionId = Long.parseLong(text(signedVersion, "versionId"));
        JsonNode acceptance = signedVersion.path("acceptance");
        assertAll(() -> assertEquals(CommercialTermVersionStatus.CLIENT_SIGNED.getCode(), text(signedVersion, "status")),
            () -> assertEquals(Long.toString(AgreementPartyBuilder.PARTY_ID), text(acceptance, "partyId")),
            () -> assertEquals(AgreementPartyAcceptanceMethod.E_SIGNATURE.getCode(), text(acceptance, "acceptanceMethod")),
            () -> assertEquals("930000000000961", text(acceptance, "fileId")),
            () -> assertEquals("a".repeat(64), text(acceptance, "contentHash")),
            () -> assertEquals(Long.toString(MAKER), text(acceptance, "recordedBy")),
            () -> assertEquals(NOW.minusSeconds(60).toString(), text(acceptance, "acceptedAt").replace(".000000Z", "Z")),
            () -> assertEquals(BusinessChangeReason.TERM_VERSION_ACCEPTED.getCode(),
                json.readTree((String) testMapper.businessChangeSnapshots("COMMERCIAL_TERM_VERSION", versionId).getLast()
                    .get("snapshot_json")).path("_meta").path("reasonCode").asString()),
            () -> assertEquals(true, ok(200, http.get(TERMS + "/versions?planId=" + text(signedVersion, "planId"), headers()))
                .path("items").get(0).path("hasAcceptance").asBoolean()));
        JsonNode active = command(ok(200, scheduleResult(signedVersion, NOW)), "activate", CHECKER);
        assertEquals(CommercialTermVersionStatus.ACTIVE.getCode(), text(active, "status"));
    }

    /** 同编号同内容重放返回当前结果；同编号不同附件 409；新编号重复签署同一版本因已不是 APPROVED 被拒。 */
    @Test
    void test_replayAndConflict() {
        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()),
            price("USD", CARD, 1, percentage("MDR", "0.025"))));
        JsonNode signedVersion = signed(approvedVersion);

        JsonNode replay = ok(200, acceptanceResult(approvedVersion, AgreementPartyBuilder.PARTY_ID,
            "SIGN-" + text(approvedVersion, "versionId")));

        assertAll(() -> assertEquals(text(signedVersion, "changeVersion"), text(replay, "changeVersion")),
            () -> assertEquals(CommercialTermVersionStatus.CLIENT_SIGNED.getCode(), text(replay, "status")),
            () -> assertProblem(acceptanceResult(signedVersion, AgreementPartyBuilder.PARTY_ID + 1,
                "SIGN-" + text(approvedVersion, "versionId")), 409, CommonErrorCode.CONCURRENT_REQUEST.getCode(),
                "签署编号已被不同内容使用"),
            () -> assertProblem(acceptanceResult(signedVersion, AgreementPartyBuilder.PARTY_ID, "SIGN-OTHER"), 400, BIZ,
                "只有 APPROVED 版本可以登记客户签署"));
    }

    /** 签署方不是合同 CLIENT 角色、版本仍是 DRAFT、签署时刻晚于登记时刻、过期根版本 409。 */
    @Test
    void test_businessRuleViolation() {
        JsonNode draft = replacePrices(createVersion(createPlan()), price("USD", CARD, 1, percentage("MDR", "0.025")));
        assertProblem(acceptanceResult(draft, AgreementPartyBuilder.PARTY_ID, "SIGN-DRAFT"), 400, BIZ,
            "只有 APPROVED 版本可以登记客户签署");
        JsonNode approvedVersion = approved(draft);

        assertAll(() -> assertProblem(acceptanceResult(approvedVersion, 930000000000999L, "SIGN-PARTY"), 400, BIZ,
                "签署方不是合同 CLIENT 角色 Party"),
            () -> assertProblem(acceptanceResult(draft, AgreementPartyBuilder.PARTY_ID, "SIGN-STALE"), 409,
                CommonErrorCode.CONCURRENT_REQUEST.getCode(), "业务版本已变化"));
        assertEquals(CommercialTermVersionStatus.APPROVED.getCode(),
            testMapper.termVersion(Long.parseLong(text(approvedVersion, "versionId"))).get("status"));
    }

    /** 存量免签版本（迁移标记）APPROVED 即可排期。 */
    @Test
    void test_signatureExemptSchedulesWithoutAcceptance() {
        JsonNode approvedVersion = approved(replacePrices(createVersion(createPlan()),
            price("USD", CARD, 1, percentage("MDR", "0.025"))));
        testMapper.overwriteVersionSignatureExempt(Long.parseLong(text(approvedVersion, "versionId")), true);

        JsonNode scheduled = ok(200, scheduleResult(approvedVersion, NOW));

        assertAll(() -> assertEquals(CommercialTermVersionStatus.SCHEDULED.getCode(), text(scheduled, "status")),
            () -> assertEquals(true, scheduled.path("signatureExempt").asBoolean()));
    }
}
