package com.paytech.channel.connector.auto.cost;

import static com.paytech.channel.connector.test.builder.ChannelCostVersionRequestBuilder.component;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.api.cost.ChannelCostResolutionApi;
import com.paytech.channel.connector.api.cost.request.ResolveChannelCostRequestBody;
import com.paytech.channel.connector.api.enums.ChannelCostErrorCode;
import com.paytech.channel.connector.core.enums.ChannelCostComponentType;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.channel.connector.test.base.AbstractChannelCostApiTest;
import com.paytech.channel.connector.test.builder.ChannelCostVersionRequestBuilder;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 渠道成本命中：唯一命中返回版本、范围与按 componentCode 升序的组成；无 ACTIVE 方案、无覆盖版本、无命中范围为
 * CHANNEL_COST_NOT_RESOLVED，覆盖版本多于一个为 CHANNEL_COST_AMBIGUOUS；effective_to 为开区间边界；命中只读。
 *
 * @see ChannelCostResolutionApi#resolveChannelCost(ResolveChannelCostRequestBody)
 */
class ChannelCostResolutionTest extends AbstractChannelCostApiTest {

    /** 本类公共配置：标准 Channel / MID 与 DRAFT 方案。 */
    @BeforeEach
    void setUpClassMocks() {
        mockStandardChannel();
        createStandardPlan();
    }

    /** 唯一命中：返回方案、版本主键与摘要、范围与组成主键，组成按代码升序，金额与费率数值不变，且不改变任何数据。 */
    @Test
    void test_uniqueHit() {
        Instant from = minutesAgo(10);
        var approved = createApproved(version(from).components(
            component("B_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.029", "0.30"),
            component("A_FEE", ChannelCostComponentType.CROSS_BORDER_FEE, "0.01", null, "0.50", "20.00")));
        int rowsBefore = testMapper.countChannelCostRows();

        var hit = resolve(body(minutesAgo(1))).assertSuccess(200).assertText("planCode", PLAN_CODE)
            .assertText("costVer", "1").assertText("contentHash", approved.text("contentHash"))
            .assertText("channelAgreementRef", ChannelCostVersionRequestBuilder.AGREEMENT_REF)
            .assertText("currency", ChannelCostVersionRequestBuilder.CURRENCY);

        JsonNode components = hit.json().path("components");
        assertAll(() -> assertEquals(from, Instant.parse(hit.text("effectiveFrom"))),
            () -> assertNull(hit.text("effectiveTo")),
            () -> assertEquals(1, hit.json().path("scopeNo").asInt()),
            () -> assertTrue(hit.text("costVerId").matches("[1-9][0-9]*"), "costVerId"),
            () -> assertTrue(hit.text("scopeId").matches("[1-9][0-9]*"), "scopeId"),
            () -> assertEquals(2, components.size()),
            () -> assertEquals("A_FEE", components.get(0).path("componentCode").asString()),
            () -> assertEquals("B_FEE", components.get(1).path("componentCode").asString()),
            () -> assertTrue(components.get(0).path("componentId").asString().matches("[1-9][0-9]*"), "componentId"),
            () -> assertDecimal("0.01", components.get(0).path("rateValue")),
            () -> assertDecimal("0.50", components.get(0).path("minAmount")),
            () -> assertDecimal("20.00", components.get(0).path("maxAmount")),
            () -> assertTrue(components.get(0).path("fixedAmount").isNull()
                || components.get(0).path("fixedAmount").isMissingNode(), "fixedAmount 应为空"),
            () -> assertDecimal("0.30", components.get(1).path("fixedAmount")),
            () -> assertEquals(ChannelCostVersionRequestBuilder.CURRENCY, components.get(1).path("currency").asString()));
        assertEquals(rowsBefore, testMapper.countChannelCostRows(), "命中不应写入数据");
    }

    /** Channel 没有方案、或方案尚无当前版本（DRAFT）：未命中。 */
    @Test
    void test_noActivePlan() {
        resolve(body(minutesAgo(1))).assertProblem(400, ChannelCostErrorCode.CHANNEL_COST_NOT_RESOLVED.getCode())
            .assertDetailContains("无 ACTIVE 渠道成本方案").assertDetailContains(channelId())
            .assertDetailContains(String.valueOf(midId()));

        ResolveChannelCostRequestBody otherChannel = body(minutesAgo(1));
        otherChannel.setChannelId(data.cardFixture("GEIDEA", ChannelCostVersionRequestBuilder.ENTITY_NO,
            ChannelCostVersionRequestBuilder.CURRENCY).getChannel().getChannelId().toString());
        resolve(otherChannel).assertProblem(400, ChannelCostErrorCode.CHANNEL_COST_NOT_RESOLVED.getCode())
            .assertDetailContains(otherChannel.getChannelId());
    }

    /** 命中时间早于唯一版本的生效时间：没有覆盖版本，未命中。 */
    @Test
    void test_noVersionCoversTime() {
        createApproved(version(minutesAgo(10)));

        resolve(body(minutesAgo(20))).assertProblem(400, ChannelCostErrorCode.CHANNEL_COST_NOT_RESOLVED.getCode())
            .assertDetailContains("无覆盖命中时间的渠道成本版本").assertDetailContains(PLAN_CODE);
    }

    /** 版本内没有六维精确命中的范围（动作类别不同）：未命中。 */
    @Test
    void test_noScopeMatched() {
        createApproved(version(minutesAgo(10)));
        ResolveChannelCostRequestBody refund = body(minutesAgo(1));
        refund.setActionType("REFUND");

        resolve(refund).assertProblem(400, ChannelCostErrorCode.CHANNEL_COST_NOT_RESOLVED.getCode())
            .assertDetailContains("无精确命中的渠道成本范围").assertDetailContains("REFUND");
    }

    /** 两个 ACTIVE / INACTIVE 版本的区间都覆盖命中时间（配置损坏）：多重命中。 */
    @Test
    void test_ambiguousVersions() {
        createApproved(version(minutesAgo(10)));
        createApproved(version(tomorrow())).assertText("status", ChannelCostVersionStatus.APPROVED.getCode());
        testMapper.rewriteVersionWindow(PLAN_CODE, 2, ChannelCostVersionStatus.INACTIVE.getCode(), minutesAgo(5),
            null);

        resolve(body(minutesAgo(1))).assertProblem(400, ChannelCostErrorCode.CHANNEL_COST_AMBIGUOUS.getCode())
            .assertDetailContains(PLAN_CODE).assertDetailContains(String.valueOf(midId()));
    }

    /** 区间为 [effective_from, effective_to)：恰在新版本生效时刻命中新版本，前一微秒命中被替代的旧版本。 */
    @Test
    void test_effectiveToBoundary() {
        createApproved(version(minutesAgo(10)));
        Instant secondFrom = minutesAgo(5);
        createApproved(version(secondFrom));

        resolve(body(secondFrom)).assertSuccess(200).assertText("costVer", "2").assertText("effectiveTo", null);
        resolve(body(secondFrom.minus(1, ChronoUnit.MICROS))).assertSuccess(200).assertText("costVer", "1")
            .assertInstant("effectiveTo", secondFrom);
    }

    /** 命中时间缺失、维度格式不合法属于字段校验。 */
    @Test
    void test_invalidParameter() {
        ResolveChannelCostRequestBody noTime = body(null);
        ResolveChannelCostRequestBody badEntity = body(minutesAgo(1));
        badEntity.setEntityNo("00001");

        resolve(noTime).assertProblem(422, "VALIDATION_ERROR").assertFieldError("asOfTime");
        resolve(badEntity).assertProblem(422, "VALIDATION_ERROR").assertFieldError("entityNo");
    }

    /** 标准六维命中请求。 */
    ResolveChannelCostRequestBody body(Instant asOfTime) {
        ResolveChannelCostRequestBody body = new ResolveChannelCostRequestBody();
        body.setChannelId(channelId());
        body.setMidId(String.valueOf(midId()));
        body.setEntityNo(ChannelCostVersionRequestBuilder.ENTITY_NO);
        body.setCountryCode(ChannelCostVersionRequestBuilder.COUNTRY_CODE);
        body.setCurrency(ChannelCostVersionRequestBuilder.CURRENCY);
        body.setPaymentMethodCode(ChannelCostVersionRequestBuilder.PAYMENT_METHOD);
        body.setActionType(ChannelCostVersionRequestBuilder.ACTION_TYPE);
        body.setAsOfTime(asOfTime);
        return body;
    }

    /** 经真实 HTTP 入口命中。 */
    ChannelCostResponseChecker resolve(ResolveChannelCostRequestBody body) {
        return post(RESOLUTION_PATH, body);
    }

    /** 十进制字符串按数值比较。 */
    static void assertDecimal(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.asString())),
            "期望 " + expected + "，实际 " + actual);
    }
}
