package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractChannelCostApiTest;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;

import tools.jackson.databind.JsonNode;

/**
 * MID 列表：空库、Channel 编码与账号名称联查、能力组与启用组计数、按 Channel / 账号 / OE / 状态筛选、更新时间降序排序、
 * 数据库分页与 total、参数校验。
 *
 * @see ChannelMidApi#listChannelMids(String, String, String, String, String, Integer, Integer)
 */
class ChannelMidListTest extends AbstractChannelCostApiTest {
    /** MID 列表路径。 */
    private static final String MID_PATH = "/channel-connector/v1/mids";

    /** 没有 MID：空页。 */
    @Test
    void test_noDataFound() {
        list("").assertPage(1, 10, 0, 0);
    }

    /** 列表项回显 MID、Channel 编码、账号与能力组计数；停用全部能力组后启用计数为 0。 */
    @Test
    void test_normalFlow() {
        Fixture fixture = data.cardFixture("NI", "AE001", "USD");
        ChannelMid mid = fixture.getMid();

        JsonNode item = list("").assertPage(1, 10, 1, 1).item(0);

        assertAll(() -> assertEquals(mid.getMidId().toString(), item.path("midId").asString()),
            () -> assertEquals(mid.getChannelId().toString(), item.path("channelId").asString()),
            () -> assertEquals("NI", item.path("channelCode").asString()),
            () -> assertEquals(mid.getChannelAccountId().toString(), item.path("channelAccountId").asString()),
            () -> assertEquals(fixture.getAccount().getAccountName(), item.path("accountName").asString()),
            () -> assertEquals(mid.getExternalMidCode(), item.path("externalMidCode").asString()),
            () -> assertEquals("AE001", item.path("entityNo").asString()),
            () -> assertEquals(ChannelStatus.ACTIVE.getCode(), item.path("status").asString()),
            () -> assertEquals(1, item.path("capabilityGroupCount").asInt()),
            () -> assertEquals(1, item.path("enabledCapabilityGroupCount").asInt()),
            () -> assertTrue(item.path("activatedAt").isNull() || item.path("activatedAt").isMissingNode()),
            () -> assertEquals(mid.getUpdateTime(), Instant.parse(item.path("updateTime").asString())));
        testMapper.disableCapabilities(mid.getMidId());
        JsonNode disabled = list("").assertPage(1, 10, 1, 1).item(0);
        assertEquals(1, disabled.path("capabilityGroupCount").asInt());
        assertEquals(0, disabled.path("enabledCapabilityGroupCount").asInt());
    }

    /** 按 Channel、当前账号、OE 与状态筛选，total 与筛选同口径。 */
    @Test
    void test_filters() {
        Fixture ni = data.cardFixture("NI", "AE001", "USD");
        ChannelMid second = data.mid(ni.getChannel().getChannelId(), ni.getAccount().getChannelAccountId(), "AE001");
        Fixture geidea = data.cardFixture("GEIDEA", "AE002", "USD");
        testMapper.rewriteMid(second.getMidId(), ChannelStatus.SUSPENDED.getCode(), second.getUpdateTime());

        list("?channelId=" + ni.getChannel().getChannelId()).assertPage(1, 10, 2, 2);
        list("?channelAccountId=" + geidea.getAccount().getChannelAccountId()).assertPage(1, 10, 1, 1);
        var byEntity = list("?entityNo=AE002").assertPage(1, 10, 1, 1);
        var suspended = list("?status=" + ChannelStatus.SUSPENDED.getCode()).assertPage(1, 10, 1, 1);
        list("?channelId=" + ni.getChannel().getChannelId() + "&status=" + ChannelStatus.ACTIVE.getCode())
            .assertPage(1, 10, 1, 1);

        assertEquals(geidea.getMid().getMidId().toString(), byEntity.item(0).path("midId").asString());
        assertEquals(second.getMidId().toString(), suspended.item(0).path("midId").asString());
        list("?entityNo=AE009").assertPage(1, 10, 0, 0);
    }

    /** CC-B 补充：外部 MID 前缀匹配；下划线按文字处理；格式不符 400。 */
    @Test
    void test_externalMidCodePrefix() {
        Fixture fixture = data.cardFixture("NI", "AE001", "USD");
        ChannelMid second = data.mid(fixture.getChannel().getChannelId(), fixture.getAccount().getChannelAccountId(),
            "AE001");

        var byPrefix = list("?externalMidCode=test-mid-" + second.getMidId()).assertPage(1, 10, 1, 1);

        assertEquals(second.getMidId().toString(), byPrefix.item(0).path("midId").asString());
        list("?externalMidCode=test-mid-").assertPage(1, 10, 2, 2);
        list("?externalMidCode=test_mid").assertPage(1, 10, 0, 0);
        list("?externalMidCode=test*").assertInvalidRequest();
    }

    /** 11 个 MID：按更新时间降序、MID 主键降序稳定排序；第 2 页只剩最旧一个，total 为 11。 */
    @Test
    void test_pagingAndOrder() {
        Fixture fixture = data.cardFixture("NI", "AE001", "USD");
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        testMapper.rewriteMid(fixture.getMid().getMidId(), ChannelStatus.ACTIVE.getCode(), base);
        ChannelMid newest = null;
        for (int index = 1; index <= 10; index++) {
            newest = data.mid(fixture.getChannel().getChannelId(), fixture.getAccount().getChannelAccountId(), "AE001");
            testMapper.rewriteMid(newest.getMidId(), ChannelStatus.ACTIVE.getCode(), base.plusSeconds(index));
        }

        var first = list("?page=1&size=10").assertPage(1, 10, 11, 10);
        var second = list("?page=2&size=10").assertPage(2, 10, 11, 1);

        assertEquals(newest.getMidId().toString(), first.item(0).path("midId").asString());
        assertEquals(fixture.getMid().getMidId().toString(), second.item(0).path("midId").asString());
    }

    /** 筛选与分页参数非法：400 INVALID_REQUEST。 */
    @Test
    void test_invalidParameter() {
        list("?entityNo=ae001").assertInvalidRequest();
        list("?status=OPEN").assertInvalidRequest();
        list("?channelId=0").assertInvalidRequest();
        list("?channelAccountId=abc").assertInvalidRequest();
        list("?size=25").assertInvalidRequest();
    }

    /** 以查询串调用 MID 列表接口。 */
    private ChannelCostResponseChecker list(String query) {
        return get(MID_PATH + query);
    }
}
