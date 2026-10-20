package com.paytech.channel.connector.auto.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.api.cost.ChannelCostPlanApi;
import com.paytech.channel.connector.api.cost.request.CreateChannelCostPlanRequestBody;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.test.base.AbstractChannelCostApiTest;

/**
 * 渠道成本方案：创建 DRAFT、渠道不存在被拒、同渠道或同代码重复被拒、字段格式校验与查询（自 billing 搬入）。
 *
 * @see ChannelCostPlanApi#createChannelCostPlan(CreateChannelCostPlanRequestBody)
 * @see ChannelCostPlanApi#getChannelCostPlan(String)
 */
class ChannelCostPlanApiTest extends AbstractChannelCostApiTest {

    /** 本类公共配置：标准 Channel / 账号 / MID。 */
    @BeforeEach
    void setUpClassMocks() {
        mockStandardChannel();
    }

    /** 创建为 DRAFT、无当前版本、无版本摘要；查询回显相同内容。 */
    @Test
    void test_createDraftPlan() {
        var created = createStandardPlan().assertText("planCode", PLAN_CODE).assertText("channelId", channelId())
            .assertText("status", ChannelCostPlanStatus.DRAFT.getCode());
        assertNull(created.text("currentCostVer"));
        assertEquals(0, created.json().path("versions").size());

        var queried = getPlan(PLAN_CODE).assertSuccess(200).assertText("planCode", PLAN_CODE)
            .assertText("channelId", channelId()).assertText("status", ChannelCostPlanStatus.DRAFT.getCode());
        assertNull(queried.text("currentCostVer"));
        assertEquals(0, queried.json().path("versions").size());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.DRAFT.getCode(), null, 0L);
    }

    /** 同一渠道已有方案：以其他代码再建被拒，不新增方案。 */
    @Test
    void test_duplicateChannelRejected() {
        createStandardPlan();
        String otherCode = "NI_SECOND";

        createPlan(channelId(), otherCode).assertBusinessRejected(channelId());

        getPlan(otherCode).assertBusinessRejected(otherCode);
    }

    /** 方案代码已存在：其他渠道使用同一代码被拒，原方案渠道不变。 */
    @Test
    void test_duplicatePlanCodeRejected() {
        createStandardPlan();
        String otherChannelId = data.cardFixture("GEIDEA", "AE001", "USD").getChannel().getChannelId().toString();

        createPlan(otherChannelId, PLAN_CODE).assertBusinessRejected(PLAN_CODE);

        getPlan(PLAN_CODE).assertSuccess(200).assertText("channelId", channelId());
    }

    /** Channel 不存在于本库：创建被拒，不写入方案。 */
    @Test
    void test_channelNotFound() {
        String unknownChannelId = "123456789";

        createPlan(unknownChannelId, PLAN_CODE).assertBusinessRejected("渠道不存在: " + unknownChannelId);

        assertEquals(0, testMapper.countChannelCostRows());
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
        createPlan(channelId(), "ni_standard").assertProblem(422, "VALIDATION_ERROR").assertFieldError("planCode");
        createPlan("0", PLAN_CODE).assertProblem(422, "VALIDATION_ERROR").assertFieldError("channelId");
        createPlan(null, PLAN_CODE).assertProblem(422, "VALIDATION_ERROR").assertFieldError("channelId");
    }
}
