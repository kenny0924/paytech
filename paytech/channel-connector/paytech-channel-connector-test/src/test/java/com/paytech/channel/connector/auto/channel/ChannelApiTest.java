package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.channel.ChannelApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelRequestBody;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractChannelConfigApiTest;
import com.paytech.channel.connector.test.builder.ChannelConfigRequestBuilder;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.mocker.ExternalApiMocker;

/**
 * 渠道管理（支付渠道与服务渠道）：创建与类型、编码唯一、列表按类型筛选与计数、名称与说明修改、激活（要求已配置出站地址）、
 * 暂停恢复、关闭前置与 CLOSED 终态、服务渠道拒绝 MID 与成本、变更记录。
 *
 * @see ChannelApi#createChannel(String, CreateChannelRequestBody)
 * @see ChannelApi#listChannels(String, String, String, Integer, Integer)
 * @see ChannelApi#getChannel(String)
 * @see ChannelApi#updateChannel(String, String, UpdateChannelRequestBody)
 * @see ChannelApi#changeChannelStatus(String, String, ChangeChannelStatusRequestBody)
 */
class ChannelApiTest extends AbstractChannelConfigApiTest {

    @Autowired
    private ExternalApiMocker externalApi;

    /** 创建 DRAFT 支付渠道：类型、说明、空出站地址与版本 1，同事务写入 CREATE 变更记录。 */
    @Test
    void test_createChannel() {
        CreateChannelRequestBody body = ChannelConfigRequestBuilder.channel("NI");
        body.setDescription("合成说明");
        ChannelCostResponseChecker created = command(HttpMethod.POST, CHANNEL_PATH, body).assertSuccess(201);

        long channelId = Long.parseLong(created.text("channelId"));
        Map<String, Object> row = testMapper.selectChannel(channelId);
        List<Map<String, Object>> changes = changes(ChangeTargetType.CHANNEL, channelId);
        assertAll(() -> created.assertText("channelCode", "NI").assertText("status", ChannelStatus.DRAFT.getCode())
                .assertText("channelType", ChannelType.PAYMENT.getCode()).assertText("description", "合成说明"),
            () -> assertEquals(0, created.json().path("endpoints").size()),
            () -> assertTrue(created.json().path("endpoints").isArray()),
            () -> assertEquals(1, created.json().path("version").asInt()),
            () -> assertEquals(ChannelStatus.DRAFT.getCode(), row.get("channel_status")),
            () -> assertEquals(ChannelType.PAYMENT.getCode(), row.get("channel_type")),
            () -> assertEquals(1, changes.size()),
            () -> assertChange(changes.getFirst(), ChangeType.CREATE.getCode(), null, null));
        get(CHANNEL_PATH + "/" + channelId).assertSuccess(200).assertText("channelName", "合成渠道 NI")
            .assertText("channelType", ChannelType.PAYMENT.getCode());
    }

    /** 创建服务渠道；类型缺失或非法 422，不落库。 */
    @Test
    void test_createServiceChannelAndTypeRequired() {
        command(HttpMethod.POST, CHANNEL_PATH, ChannelConfigRequestBuilder.channel("GMAP", ChannelType.SERVICE))
            .assertSuccess(201).assertText("channelType", ChannelType.SERVICE.getCode());

        assertValidationError(command(HttpMethod.POST, CHANNEL_PATH, ChannelConfigRequestBuilder.channel("NI", null)));
        CreateChannelRequestBody invalid = ChannelConfigRequestBuilder.channel("NI");
        invalid.setChannelType("PROVIDER");
        assertValidationError(command(HttpMethod.POST, CHANNEL_PATH, invalid));

        assertEquals(1, testMapper.countChannels());
    }

    /** 渠道编码重复由唯一索引拒绝为 409，不写第二行与变更记录。 */
    @Test
    void test_duplicateChannelCode() {
        command(HttpMethod.POST, CHANNEL_PATH, ChannelConfigRequestBuilder.channel("NI")).assertSuccess(201);

        assertConflict(command(HttpMethod.POST, CHANNEL_PATH, ChannelConfigRequestBuilder.channel("NI",
            ChannelType.SERVICE)));

        assertEquals(1, testMapper.countChannels());
        assertEquals(1, testMapper.countConfigChangeRows());
    }

    /** 编码格式错误 422；操作者请求头缺失或非规范十进制 400；不存在的渠道 400。 */
    @Test
    void test_invalidParameter() {
        assertValidationError(command(HttpMethod.POST, CHANNEL_PATH, ChannelConfigRequestBuilder.channel("ni")));
        command(HttpMethod.POST, CHANNEL_PATH, null, ChannelConfigRequestBuilder.channel("NI")).assertStatus(400);
        command(HttpMethod.POST, CHANNEL_PATH, "0123", ChannelConfigRequestBuilder.channel("NI"))
            .assertInvalidRequest();
        get(CHANNEL_PATH + "/1").assertBusinessRejected("渠道不存在");
        get(CHANNEL_PATH + "?channelType=PROVIDER").assertInvalidRequest();
        assertEquals(0, testMapper.countChannels());
    }

    /** 列表按类型、状态与编码 / 名称前缀筛选，回显账号、MID、出站地址数；ACTIVE 授权数只对服务渠道有值。 */
    @Test
    void test_listChannels() {
        Channel active = data.channel("NI", ChannelStatus.ACTIVE);
        data.channel("NIX", ChannelStatus.DRAFT);
        Channel service = data.serviceChannel("GMAP", ChannelStatus.DRAFT);
        externalApi.channel("NI");
        var account = data.account(active.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        data.mid(active.getChannelId(), account.getChannelAccountId(), ENTITY_NO, ChannelStatus.DRAFT);

        get(CHANNEL_PATH).assertPage(1, 10, 3, 3);
        get(CHANNEL_PATH + "?keyword=NI").assertPage(1, 10, 2, 2);
        get(CHANNEL_PATH + "?keyword=N_").assertPage(1, 10, 0, 0);
        get(CHANNEL_PATH + "?channelType=PAYMENT").assertPage(1, 10, 2, 2);
        var serviceOnly = get(CHANNEL_PATH + "?channelType=SERVICE").assertPage(1, 10, 1, 1).item(0);
        var activeOnly = get(CHANNEL_PATH + "?status=ACTIVE").assertPage(1, 10, 1, 1).item(0);
        assertAll(() -> assertEquals(active.getChannelId().toString(), activeOnly.path("channelId").asString()),
            () -> assertEquals(ChannelType.PAYMENT.getCode(), activeOnly.path("channelType").asString()),
            () -> assertEquals(1, activeOnly.path("accountCount").asInt()),
            () -> assertEquals(1, activeOnly.path("midCount").asInt()),
            () -> assertEquals(1, activeOnly.path("endpointCount").asInt()),
            () -> assertTrue(activeOnly.path("activeGrantCount").isNull()),
            () -> assertEquals(service.getChannelId().toString(), serviceOnly.path("channelId").asString()),
            () -> assertEquals(0, serviceOnly.path("endpointCount").asInt()),
            () -> assertEquals(0, serviceOnly.path("activeGrantCount").asInt()));
        get(CHANNEL_PATH + "?page=2&size=10").assertPage(2, 10, 3, 0);
        get(CHANNEL_PATH + "?status=UNKNOWN").assertInvalidRequest();
    }

    /** 名称与说明整体替换并递增版本，写入 UPDATE 变更记录；已关闭渠道拒绝修改且不写记录。 */
    @Test
    void test_updateChannel() {
        Channel channel = data.channel("NI", ChannelStatus.DRAFT);
        UpdateChannelRequestBody body = new UpdateChannelRequestBody();
        body.setChannelName("新名称");
        body.setDescription("新说明");

        command(HttpMethod.PUT, CHANNEL_PATH + "/" + channel.getChannelId(), body).assertSuccess(200)
            .assertText("channelName", "新名称").assertText("description", "新说明")
            .assertText("channelType", ChannelType.PAYMENT.getCode());
        Channel closed = data.channel("GEIDEA", ChannelStatus.CLOSED);
        command(HttpMethod.PUT, CHANNEL_PATH + "/" + closed.getChannelId(), body).assertBusinessRejected("渠道已关闭");

        Map<String, Object> row = testMapper.selectChannel(channel.getChannelId());
        assertAll(() -> assertEquals("新名称", row.get("channel_name")),
            () -> assertEquals("新说明", row.get("description")),
            () -> assertEquals(2, ((Number)row.get("version")).intValue()));
        assertChange(changes(ChangeTargetType.CHANNEL, channel.getChannelId()).getFirst(), ChangeType.UPDATE.getCode(),
            null, null);
        assertEquals(List.of(), changes(ChangeTargetType.CHANNEL, closed.getChannelId()));
    }

    /** 已配置出站地址的渠道可以激活，记录 DRAFT→ACTIVE 与原因；技术账号与标准 API 实现不阻止激活。 */
    @Test
    void test_activateChannel() {
        Channel channel = data.channel("NI", ChannelStatus.DRAFT);
        externalApi.channel("NI");

        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.ACTIVE, "上线")).assertSuccess(200)
            .assertText("status", ChannelStatus.ACTIVE.getCode());

        var change = changes(ChangeTargetType.CHANNEL, channel.getChannelId()).getLast();
        assertAll(() -> assertEquals(ChannelStatus.ACTIVE.getCode(), testMapper.selectChannel(channel.getChannelId())
                .get("channel_status")),
            () -> assertChange(change, ChangeType.STATUS.getCode(), ChannelStatus.DRAFT.getCode(), ChannelStatus.ACTIVE.getCode()),
            () -> assertEquals("上线", change.get("reason")));
        transactions.assertOutsideTransactions();
    }

    /** 两类渠道未配置出站地址时激活被拒绝，渠道保持 DRAFT、不写变更记录。 */
    @Test
    void test_activateRejectedWithoutEndpoints() {
        Channel channel = data.channel("NI", ChannelStatus.DRAFT);
        Channel service = data.serviceChannel("GMAP", ChannelStatus.DRAFT);

        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.ACTIVE))
            .assertBusinessRejected("渠道未配置出站地址: NI");
        command(HttpMethod.POST, statusPath(service), status(ChannelStatus.ACTIVE))
            .assertBusinessRejected("渠道未配置出站地址: GMAP");

        assertUnchanged(channel, ChannelStatus.DRAFT);
        assertUnchanged(service, ChannelStatus.DRAFT);
    }

    /** ACTIVE → SUSPENDED → ACTIVE；恢复同样核对出站地址；两条 STATUS 记录。 */
    @Test
    void test_suspendAndResume() {
        Channel channel = data.channel("NI", ChannelStatus.ACTIVE);
        externalApi.channel("NI");

        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.SUSPENDED)).assertSuccess(200)
            .assertText("status", ChannelStatus.SUSPENDED.getCode());
        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.ACTIVE)).assertSuccess(200)
            .assertText("status", ChannelStatus.ACTIVE.getCode());

        var changes = changes(ChangeTargetType.CHANNEL, channel.getChannelId());
        assertAll(() -> assertEquals(2, changes.size()),
            () -> assertChange(changes.get(0), ChangeType.STATUS.getCode(), ChannelStatus.ACTIVE.getCode(), ChannelStatus.SUSPENDED.getCode()),
            () -> assertChange(changes.get(1), ChangeType.STATUS.getCode(), ChannelStatus.SUSPENDED.getCode(), ChannelStatus.ACTIVE.getCode()));
    }

    /** 非法迁移拒绝并说明当前与目标状态。 */
    @Test
    void test_illegalTransition() {
        Channel channel = data.channel("NI", ChannelStatus.DRAFT);

        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.SUSPENDED))
            .assertBusinessRejected("当前状态 DRAFT, 目标状态 SUSPENDED");

        assertUnchanged(channel, ChannelStatus.DRAFT);
    }

    /** 渠道下有未关闭的账号或 MID 时拒绝关闭；子项全部关闭后可关闭。 */
    @Test
    void test_closeBlockedByChildren() {
        Channel channel = data.channel("NI", ChannelStatus.ACTIVE);
        var account = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);

        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.CLOSED))
            .assertBusinessRejected("未关闭账号 1, 未关闭 MID 0");
        testMapper.accountStatus(ChannelStatus.CLOSED.getCode());
        data.mid(channel.getChannelId(), account.getChannelAccountId(), ENTITY_NO, ChannelStatus.CLOSED);
        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.CLOSED)).assertSuccess(200)
            .assertText("status", ChannelStatus.CLOSED.getCode());

        assertEquals(1, changes(ChangeTargetType.CHANNEL, channel.getChannelId()).size());
    }

    /** CLOSED 渠道拒绝改名、状态变更与账号 / MID 创建。 */
    @Test
    void test_closedChannelRejectsWrites() {
        Channel channel = data.channel("NI", ChannelStatus.CLOSED);

        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.ACTIVE))
            .assertBusinessRejected("当前状态 CLOSED");
        command(HttpMethod.POST, ACCOUNT_PATH, ChannelConfigRequestBuilder.account(channel.getChannelId(), ENTITY_NO))
            .assertBusinessRejected("渠道已关闭，不能创建技术账号");
        command(HttpMethod.POST, MID_PATH, ChannelConfigRequestBuilder.mid(channel.getChannelId(), 1L, "MID-1",
            ENTITY_NO)).assertBusinessRejected("渠道已关闭，不能创建 MID");

        assertUnchanged(channel, ChannelStatus.CLOSED);
        assertEquals(1, testMapper.countRows());
    }

    /** 状态路径。 */
    static String statusPath(Channel channel) {
        return CHANNEL_PATH + "/" + channel.getChannelId() + "/status";
    }

    /** 渠道状态未变且无变更记录。 */
    void assertUnchanged(Channel channel, ChannelStatus expected) {
        assertEquals(expected.getCode(), testMapper.selectChannel(channel.getChannelId()).get("channel_status"));
        assertEquals(0, testMapper.countConfigChangeRows());
    }
}
