package com.paytech.finance.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.finance.accounting.domain.view.ChannelCostEstimate;
import com.paytech.finance.accounting.domainservice.service.ChannelCostEstimationService;
import com.paytech.finance.api.accounting.request.ChannelCostComponentRequestBody;
import com.paytech.finance.test.builder.ChannelCostEstimateCommandBuilder;
import com.paytech.finance.test.builder.ChannelCostVersionRequestBuilder;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 渠道成本估算入口测试的公共前置与调用
 * <li>每例经真实方案接口创建标准 DRAFT 方案；生效版本由用例经创建、提交、批准链路发布，不直接插入成本规则行。</li>
 * <li>估算为只读领域服务，不需要调用方事务，直接调用。</li>
 */
public abstract class AbstractChannelCostEstimationTest extends AbstractChannelCostApiTest {
    /** 标准方案所属 Payment Channel。 */
    protected static final long CHANNEL = Long.parseLong(CHANNEL_ID);

    @Autowired
    protected ChannelCostEstimationService estimationService;

    /** 两级初始化的类级部分：标准 DRAFT 方案。 */
    @BeforeEach
    void setUpEstimationMocks() {
        createStandardPlan();
    }

    /** 以给定组成在标准范围下发布一个指定生效时间的版本，返回其内容摘要。 */
    protected String publish(Instant effectiveFrom, ChannelCostComponentRequestBody... components) {
        return createApproved(ChannelCostVersionRequestBuilder.template(MAKER_ID, effectiveFrom)
            .components(components)).text("contentHash");
    }

    /** 标准路由元组、当前时间发生的命令模板。 */
    protected static ChannelCostEstimateCommandBuilder command(String basisAmount) {
        return ChannelCostEstimateCommandBuilder.template(CHANNEL, basisAmount);
    }

    /** 调用估算。 */
    protected ChannelCostEstimate estimate(ChannelCostEstimateCommandBuilder builder) {
        return estimationService.estimate(builder.build());
    }

    /** 断言估算被拒，且消息包含给定原因前缀与渠道上下文。 */
    protected void assertRejected(ChannelCostEstimateCommandBuilder builder, String reason) {
        BizCheckFailException e = assertThrows(BizCheckFailException.class, () -> estimate(builder));
        assertAll(() -> assertTrue(e.getMessage().contains(reason), e.getMessage()),
            () -> assertTrue(e.getMessage().contains("渠道 "), e.getMessage()));
    }

    /** 断言命中版本、范围与证据。 */
    protected void assertHit(ChannelCostEstimate estimate, int costVer, String contentHash, String total) {
        assertAll("估算结果",
            () -> assertEquals(costVer, estimate.getCostVer()),
            () -> assertEquals(contentHash, estimate.getContentHash()),
            () -> assertEquals(1, estimate.getScopeNo()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.AGREEMENT_REF, estimate.getChannelAgreementRef()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.ACTION_TYPE, estimate.getActionType()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.CURRENCY, estimate.getCurrency()),
            () -> assertAmount(total, estimate.getTotalPostedAmount()));
    }

    /** 断言一个组成的代码、基数、原始金额与舍入后金额。 */
    protected static void assertComponent(ChannelCostEstimate.Component component, String componentCode,
        String basis, String raw, String posted) {
        assertAll("组成 " + componentCode,
            () -> assertEquals(componentCode, component.getComponentCode()),
            () -> assertAmount(basis, component.getBasisAmount()),
            () -> assertAmount(raw, component.getRawAmount()),
            () -> assertAmount(posted, component.getPostedAmount()),
            () -> assertEquals(new BigDecimal(posted).scale(), component.getPostedAmount().scale(),
                "postedAmount 精度"));
    }

    /** 金额按数值比较。 */
    protected static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "期望 " + expected + "，实际 " + actual);
    }
}
