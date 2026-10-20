package com.paytech.channel.connector.auto.cost;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.api.cost.ChannelCostPlanApi;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.test.base.AbstractChannelCostApiTest;
import com.paytech.channel.connector.test.builder.ChannelCostVersionRequestBuilder;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;

import tools.jackson.databind.JsonNode;

/**
 * 渠道成本方案列表：空库、按 Channel / 状态筛选、数据库分页与 total、更新时间降序排序、待复核版本数与 Channel 编码名称联查、参数校验。
 *
 * @see ChannelCostPlanApi#listChannelCostPlans(String, String, Integer, Integer)
 */
class ChannelCostPlanListTest extends AbstractChannelCostApiTest {

    /** 本类公共配置：标准 Channel / 账号 / MID（方案按用例创建）。 */
    @BeforeEach
    void setUpClassMocks() {
        mockStandardChannel();
    }

    /** 没有方案：空页，保留默认页码与条数。 */
    @Test
    void test_noDataFound() {
        list("").assertPage(1, 10, 0, 0);
    }

    /** 列表项回显方案、Channel 编码与名称、当前版本与生效时间；DRAFT 方案无当前版本。 */
    @Test
    void test_normalFlow() {
        Instant effectiveFrom = minutesAgo(5);
        createStandardPlan();
        createApproved(version(effectiveFrom));

        var page = list("").assertPage(1, 10, 1, 1);

        JsonNode item = page.item(0);
        assertAll(() -> assertTrue(item.path("planId").asString().matches("[1-9][0-9]*"), "planId"),
            () -> assertEquals(PLAN_CODE, item.path("planCode").asString()),
            () -> assertEquals(channelId(), item.path("channelId").asString()),
            () -> assertEquals(CHANNEL_CODE, item.path("channelCode").asString()),
            () -> assertEquals(fixture.getChannel().getChannelName(), item.path("channelName").asString()),
            () -> assertEquals(ChannelCostPlanStatus.ACTIVE.getCode(), item.path("status").asString()),
            () -> assertEquals("1", item.path("currentCostVer").asString()),
            () -> assertEquals(effectiveFrom, Instant.parse(item.path("currentEffectiveFrom").asString())),
            () -> assertEquals(0, item.path("pendingApprovalCount").asInt()),
            () -> assertTrue(item.path("updateTime").isString(), "updateTime"));
    }

    /** 按 Channel 筛选只返回该 Channel 的方案，total 同口径。 */
    @Test
    void test_filterByChannel() {
        createStandardPlan();
        Fixture other = data.cardFixture("GEIDEA", ChannelCostVersionRequestBuilder.ENTITY_NO,
            ChannelCostVersionRequestBuilder.CURRENCY);
        String otherChannelId = other.getChannel().getChannelId().toString();
        createPlan(otherChannelId, "GEIDEA_STANDARD").assertSuccess(201);

        var page = list("?channelId=" + otherChannelId).assertPage(1, 10, 1, 1);

        assertEquals("GEIDEA_STANDARD", page.item(0).path("planCode").asString());
        assertEquals("GEIDEA", page.item(0).path("channelCode").asString());
        list("?channelId=123456789").assertPage(1, 10, 0, 0);
    }

    /** 按状态筛选：ACTIVE 只含已有当前版本的方案，DRAFT 只含未生效方案。 */
    @Test
    void test_filterByStatus() {
        createStandardPlan();
        createApproved(version());
        String draftChannelId = data.cardFixture("GEIDEA", "AE002", "USD").getChannel().getChannelId().toString();
        createPlan(draftChannelId, "GEIDEA_STANDARD").assertSuccess(201);

        var active = list("?status=" + ChannelCostPlanStatus.ACTIVE.getCode()).assertPage(1, 10, 1, 1);
        var draft = list("?status=" + ChannelCostPlanStatus.DRAFT.getCode()).assertPage(1, 10, 1, 1);

        assertEquals(PLAN_CODE, active.item(0).path("planCode").asString());
        assertEquals("GEIDEA_STANDARD", draft.item(0).path("planCode").asString());
        JsonNode current = draft.item(0).path("currentCostVer");
        assertTrue(current.isNull() || current.isMissingNode(), "DRAFT 方案无当前版本");
        list("?status=" + ChannelCostPlanStatus.INACTIVE.getCode()).assertPage(1, 10, 0, 0);
    }

    /** 待复核版本数只统计 PENDING_APPROVAL，DRAFT 与 ACTIVE 不计。 */
    @Test
    void test_pendingApprovalCount() {
        createStandardPlan();
        createApproved(version(minutesAgo(10)));
        createSubmitted(version());

        assertEquals(1, list("").assertPage(1, 10, 1, 1).item(0).path("pendingApprovalCount").asInt());
    }

    /** 11 个方案：按更新时间降序、主键降序稳定排序，第 2 页只剩最旧的一个，total 为 11。 */
    @Test
    void test_pagingAndOrder() {
        Instant base = minutesAgo(60);
        for (int index = 0; index < 11; index++) {
            String code = String.format("CH%02d", index);
            String channelId = data.cardFixture(code, ChannelCostVersionRequestBuilder.ENTITY_NO, "USD").getChannel()
                .getChannelId().toString();
            createPlan(channelId, "PLAN_" + code).assertSuccess(201);
            testMapper.rewritePlanUpdateTime("PLAN_" + code, base.plusSeconds(index));
        }

        ChannelCostResponseChecker first = list("?page=1&size=10").assertPage(1, 10, 11, 10);
        ChannelCostResponseChecker second = list("?page=2&size=10").assertPage(2, 10, 11, 1);

        assertEquals("PLAN_CH10", first.item(0).path("planCode").asString());
        assertEquals("PLAN_CH01", first.item(9).path("planCode").asString());
        assertEquals("PLAN_CH00", second.item(0).path("planCode").asString());
    }

    /** 状态、Channel 与分页参数非法：400 INVALID_REQUEST。 */
    @Test
    void test_invalidParameter() {
        list("?status=UNKNOWN").assertInvalidRequest();
        list("?channelId=0").assertInvalidRequest();
        list("?size=15").assertInvalidRequest();
        list("?page=0").assertInvalidRequest();
    }

    /** 以查询串调用列表接口。 */
    private ChannelCostResponseChecker list(String query) {
        return get(PATH + query);
    }
}
