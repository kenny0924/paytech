package com.paytech.finance.auto.ext.http.accounting.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.enums.ChannelCostPlanStatus;
import com.paytech.finance.test.base.AbstractChannelCostApiTest;

/**
 * 渠道成本方案：创建 DRAFT、同渠道或同代码重复被拒、字段格式校验与查询。
 *
 * @see com.paytech.finance.api.accounting.ChannelCostPlanApi#createChannelCostPlan(com.paytech.finance.api.accounting.request.CreateChannelCostPlanRequestBody)
 * @see com.paytech.finance.api.accounting.ChannelCostPlanApi#getChannelCostPlan(String)
 */
class ChannelCostPlanApiTest extends AbstractChannelCostApiTest {

    /** 创建为 DRAFT、无当前版本、无版本摘要；查询回显相同内容。 */
    @Test
    void test_createDraftPlan() {
        var created = createStandardPlan().assertText("planCode", PLAN_CODE).assertText("channelId", CHANNEL_ID)
            .assertText("status", ChannelCostPlanStatus.DRAFT.getCode());
        assertNull(created.text("currentCostVer"));
        assertEquals(0, created.json().path("versions").size());

        var queried = getPlan(PLAN_CODE).assertSuccess(200).assertText("planCode", PLAN_CODE)
            .assertText("channelId", CHANNEL_ID).assertText("status", ChannelCostPlanStatus.DRAFT.getCode());
        assertNull(queried.text("currentCostVer"));
        assertEquals(0, queried.json().path("versions").size());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.DRAFT.getCode(), null, 0L);
    }

    /** 同一渠道已有方案：以其他代码再建被拒，不新增方案。 */
    @Test
    void test_duplicateChannelRejected() {
        createStandardPlan();
        String otherCode = "NI_SECOND";

        createPlan(CHANNEL_ID, otherCode).assertBusinessRejected(CHANNEL_ID);

        getPlan(otherCode).assertBusinessRejected(otherCode);
    }

    /** 方案代码已存在：其他渠道使用同一代码被拒，原方案渠道不变。 */
    @Test
    void test_duplicatePlanCodeRejected() {
        createStandardPlan();

        createPlan("710002", PLAN_CODE).assertBusinessRejected(PLAN_CODE);

        getPlan(PLAN_CODE).assertSuccess(200).assertText("channelId", CHANNEL_ID);
    }

    /** 方案不存在：查询为业务错误。 */
    @Test
    void test_planNotFound() {
        String unknown = "UNKNOWN_PLAN";

        getPlan(unknown).assertBusinessRejected(unknown);
    }

    /** 方案代码与渠道 ID 格式不合法属于字段校验。 */
    @Test
    void test_invalidParameter() {
        createPlan(CHANNEL_ID, "ni_standard").assertProblem(422, "VALIDATION_ERROR").assertFieldError("planCode");
        createPlan("0", PLAN_CODE).assertProblem(422, "VALIDATION_ERROR").assertFieldError("channelId");
        createPlan(null, PLAN_CODE).assertProblem(422, "VALIDATION_ERROR").assertFieldError("channelId");
    }
}
