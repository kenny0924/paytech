package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.channel.connector.api.change.ChannelConfigChangeApi;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelConfigChange;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractChannelCostApiTest;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.foundation.id.IdGenerator;

import tools.jackson.databind.JsonNode;

/**
 * 变更记录查询：按对象类型与主键精确筛选、主键降序、数据库分页与 total、字段回显与参数校验；变更记录由 TestMapper 直接插入。
 *
 * @see ChannelConfigChangeApi#listConfigChanges(String, String, Integer, Integer)
 */
class ChannelConfigChangeQueryTest extends AbstractChannelCostApiTest {
    /** 变更记录查询路径。 */
    private static final String CHANGE_PATH = "/channel-connector/v1/config-changes";

    /** 被查询对象主键。 */
    private static final long TARGET_ID = 70001L;

    @Autowired
    private IdGenerator ids;

    /** 对象没有变更记录：空页。 */
    @Test
    void test_noDataFound() {
        list(ChangeTargetType.CHANNEL, TARGET_ID, "").assertPage(1, 10, 0, 0);
    }

    /** 只返回同类型同主键的记录，按主键降序，字段逐值回显。 */
    @Test
    void test_normalFlow() {
        ChannelConfigChange created = insert(ChangeTargetType.CHANNEL, TARGET_ID, ChangeType.CREATE, null, null);
        ChannelConfigChange status = insert(ChangeTargetType.CHANNEL, TARGET_ID, ChangeType.STATUS,
            ChannelStatus.DRAFT.getCode(), ChannelStatus.ACTIVE.getCode());
        insert(ChangeTargetType.CHANNEL, TARGET_ID + 1, ChangeType.UPDATE, null, null);
        insert(ChangeTargetType.CHANNEL_MID, TARGET_ID, ChangeType.UPDATE, null, null);

        var page = list(ChangeTargetType.CHANNEL, TARGET_ID, "").assertPage(1, 10, 2, 2);

        JsonNode first = page.item(0);
        assertAll(() -> assertEquals(status.getId().toString(), first.path("changeId").asString()),
            () -> assertEquals(ChangeTargetType.CHANNEL.getCode(), first.path("targetType").asString()),
            () -> assertEquals(String.valueOf(TARGET_ID), first.path("targetId").asString()),
            () -> assertEquals(ChangeType.STATUS.getCode(), first.path("changeType").asString()),
            () -> assertEquals(ChannelStatus.DRAFT.getCode(), first.path("fromStatus").asString()),
            () -> assertEquals(ChannelStatus.ACTIVE.getCode(), first.path("toStatus").asString()),
            () -> assertEquals(status.getOperatorId().toString(), first.path("operatorId").asString()),
            () -> assertEquals(status.getReason(), first.path("reason").asString()),
            () -> assertEquals(status.getCreateTime(), Instant.parse(first.path("createTime").asString())));
        assertEquals(created.getId().toString(), page.item(1).path("changeId").asString());
    }

    /** 11 条记录：第 1 页 10 条从最新开始，第 2 页只剩最早一条，total 为 11。 */
    @Test
    void test_paging() {
        List<ChannelConfigChange> changes = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            changes.add(insert(ChangeTargetType.CHANNEL_ACCOUNT, TARGET_ID, ChangeType.UPDATE, null, null));
        }

        var first = list(ChangeTargetType.CHANNEL_ACCOUNT, TARGET_ID, "&page=1&size=10").assertPage(1, 10, 11, 10);
        var second = list(ChangeTargetType.CHANNEL_ACCOUNT, TARGET_ID, "&page=2&size=10").assertPage(2, 10, 11, 1);

        assertEquals(changes.get(10).getId().toString(), first.item(0).path("changeId").asString());
        assertEquals(changes.get(0).getId().toString(), second.item(0).path("changeId").asString());
    }

    /** 对象类型、主键与分页参数非法或缺失：400 INVALID_REQUEST。 */
    @Test
    void test_invalidParameter() {
        get(CHANGE_PATH + "?targetType=UNKNOWN&targetId=1").assertInvalidRequest();
        get(CHANGE_PATH + "?targetType=CHANNEL&targetId=0").assertInvalidRequest();
        get(CHANGE_PATH + "?targetType=CHANNEL").assertInvalidRequest();
        get(CHANGE_PATH + "?targetId=1").assertInvalidRequest();
        list(ChangeTargetType.CHANNEL, TARGET_ID, "&size=15").assertInvalidRequest();
    }

    /** 以对象类型与主键调用查询接口，extra 追加分页参数。 */
    private ChannelCostResponseChecker list(ChangeTargetType targetType, long targetId, String extra) {
        return get(CHANGE_PATH + "?targetType=" + targetType.getCode() + "&targetId=" + targetId + extra);
    }

    /** 插入一条主键递增的变更记录。 */
    private ChannelConfigChange insert(ChangeTargetType targetType, long targetId, ChangeType changeType,
        String fromStatus, String toStatus) {
        ChannelConfigChange change = new ChannelConfigChange();
        change.setId(ids.nextId());
        change.setTargetType(targetType);
        change.setTargetId(targetId);
        change.setChangeType(changeType);
        change.setFromStatus(fromStatus);
        change.setToStatus(toStatus);
        change.setOperatorId(81001L);
        change.setReason("运营调整 " + changeType.getCode());
        change.setCreateTime(Instant.parse("2026-10-20T00:00:00.123456Z"));
        testMapper.insertConfigChange(change);
        return change;
    }
}
