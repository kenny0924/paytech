package com.paytech.billing.auto.ext.http.fact;

import static com.paytech.billing.test.builder.BillingFactRequestBuilder.MAKER_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.BillingFactCommandApi;
import com.paytech.billing.api.enums.BillingFactErrorCode;
import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.test.base.AbstractBillingFactApiTest;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;

/**
 * 作废计费事实草稿：真实 HTTP 入口经 Processor、Service、Repository 条件更新 t_billing_fact
 * <li>覆盖 DRAFT → VOIDED 且不登记补偿任务、不发布事件，重复作废返回原事实，已批准 / 已替代事实不能作废，作废后不能批准，不存在的事实与缺少
 * 操作人 Header。</li>
 *
 * @see BillingFactCommandApi#voidBillingFact(String, String, String)
 */
class VoidBillingFactTest extends AbstractBillingFactApiTest {

    /** 作废草稿：200 VOIDED、版本仍为 0、无复核信息；库内状态与更新时间推进，未登记补偿任务、未发布事件；详情一致。 */
    @Test
    void test_normalFlow() {
        long factId = submitDraft(BillingFactRequestBuilder.template());
        Object submittedUpdateTime = factRow(factId).get("update_time");

        voidFact(factId, MAKER_ID).assertSuccess(200).assertText("status", BillingFactStatus.VOIDED.getCode())
            .assertText("factVersion", "0").assertText("checkerId", null).assertText("approvedTime", null);

        Map<String, Object> row = factRow(factId);
        assertAll(() -> assertEquals(BillingFactStatus.VOIDED.getCode(), row.get("status")),
            () -> assertEquals(0L, ((Number)row.get("fact_version")).longValue()), () -> assertNull(row.get("checker_id")),
            () -> assertNull(row.get("published_at")),
            () -> assertNotEquals(submittedUpdateTime, row.get("update_time"), "作废应推进更新时间"));
        events.assertNoEnvelope(factId);
        getFact(factId).assertSuccess(200).assertText("status", BillingFactStatus.VOIDED.getCode());
    }

    /** 已作废再作废（任一操作人）直接返回原事实，不再更新。 */
    @Test
    void test_repeatedVoidReturnsOriginal() {
        long factId = submitDraft(BillingFactRequestBuilder.template());
        voidFact(factId, MAKER_ID).assertSuccess(200);
        Object voidedUpdateTime = factRow(factId).get("update_time");

        voidFact(factId, CHECKER_ID).assertSuccess(200).assertText("status", BillingFactStatus.VOIDED.getCode());

        assertEquals(voidedUpdateTime, factRow(factId).get("update_time"), "重复作废不应再更新");
        events.assertNoEnvelope(factId);
    }

    /** 已批准与已替代事实不能作废，状态不变；不存在的事实业务拒绝。 */
    @Test
    void test_nonDraftRejected() {
        long original = submitApprovedAndPublished(BillingFactRequestBuilder.template());
        long correction = submitDraft(BillingFactRequestBuilder.template().supersedes(original));
        approve(correction, CHECKER_ID).assertSuccess(200);
        events.awaitEnvelope(correction);

        voidFact(correction, MAKER_ID).assertFailCode(BillingFactErrorCode.FACT_NOT_DRAFT);
        voidFact(original, MAKER_ID).assertFailCode(BillingFactErrorCode.FACT_NOT_DRAFT);
        voidFact(9_999_999L, MAKER_ID).assertBusinessRejected("计费事实不存在");

        assertAll(() -> assertEquals(BillingFactStatus.APPROVED.getCode(), factRow(correction).get("status")),
            () -> assertEquals(BillingFactStatus.SUPERSEDED.getCode(), factRow(original).get("status")));
    }

    /** 作废后不能批准：拒绝且不登记发布任务；作废的事实也不能被替代。 */
    @Test
    void test_voidedCannotBeApproved() {
        long factId = submitDraft(BillingFactRequestBuilder.template());
        voidFact(factId, MAKER_ID).assertSuccess(200);

        approve(factId, CHECKER_ID).assertFailCode(BillingFactErrorCode.FACT_NOT_DRAFT);
        submit(BillingFactRequestBuilder.template().supersedes(factId))
            .assertFailCode(BillingFactErrorCode.FACT_SUPERSEDE_INVALID);

        assertAll(() -> assertEquals(BillingFactStatus.VOIDED.getCode(), factRow(factId).get("status")),
            () -> assertNull(factRow(factId).get("checker_id")));
        events.assertNoEnvelope(factId);
    }

    /** 缺少或畸形的操作人 Header 为参数错误，事实保持 DRAFT。 */
    @Test
    void test_invalidParameter() {
        long factId = submitDraft(BillingFactRequestBuilder.template());

        voidFact(factId, null).assertInvalidRequest();
        voidFact(factId, "operator").assertInvalidRequest();

        assertEquals(BillingFactStatus.DRAFT.getCode(), factRow(factId).get("status"));
    }
}
