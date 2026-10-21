package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.channel.PaymentChannelApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelRequestBody;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractChannelConfigApiTest;
import com.paytech.channel.connector.test.builder.ChannelConfigRequestBuilder;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.mocker.ExternalApiMocker;

/**
 * 支付渠道管理：创建与编码唯一、列表筛选分页、改名、激活（核对本库同编码接入方 t_integration）、暂停恢复、关闭前置与 CLOSED 终态、变更记录。
 *
 * @see PaymentChannelApi#createChannel(String, CreateChannelRequestBody)
 * @see PaymentChannelApi#listChannels(String, String, Integer, Integer)
 * @see PaymentChannelApi#getChannel(String)
 * @see PaymentChannelApi#updateChannel(String, String, UpdateChannelRequestBody)
 * @see PaymentChannelApi#changeChannelStatus(String, String, ChangeChannelStatusRequestBody)
 */
class PaymentChannelApiTest extends AbstractChannelConfigApiTest {

    @Autowired
    private ExternalApiMocker externalApi;

    /** 创建 DRAFT 渠道，同事务写入 CREATE 变更记录；创建不核对接入方。 */
    @Test
    void test_createChannel() {
        ChannelCostResponseChecker created =
            command(HttpMethod.POST, CHANNEL_PATH, ChannelConfigRequestBuilder.channel("NI")).assertSuccess(201);

        long channelId = Long.parseLong(created.text("channelId"));
        Map<String, Object> row = testMapper.selectChannel(channelId);
        List<Map<String, Object>> changes = changes(ChangeTargetType.CHANNEL, channelId);
        assertAll(() -> created.assertText("channelCode", "NI").assertText("status", ChannelStatus.DRAFT.getCode()),
            () -> assertEquals(ChannelStatus.DRAFT.getCode(), row.get("channel_status")),
            () -> assertEquals(1, changes.size()),
            () -> assertChange(changes.getFirst(), ChangeType.CREATE.getCode(), null, null));
        get(CHANNEL_PATH + "/" + channelId).assertSuccess(200).assertText("channelName", "合成渠道 NI");
    }

    /** 渠道编码重复由唯一索引拒绝为 409，不写第二行与变更记录。 */
    @Test
    void test_duplicateChannelCode() {
        command(HttpMethod.POST, CHANNEL_PATH, ChannelConfigRequestBuilder.channel("NI")).assertSuccess(201);

        assertConflict(command(HttpMethod.POST, CHANNEL_PATH, ChannelConfigRequestBuilder.channel("NI")));

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
        assertEquals(0, testMapper.countChannels());
    }

    /** 列表按状态与编码 / 名称前缀筛选，回显账号与 MID 计数，分页 total 正确。 */
    @Test
    void test_listChannels() {
        PaymentChannel active = data.channel("NI", ChannelStatus.ACTIVE);
        data.channel("NIX", ChannelStatus.DRAFT);
        data.channel("GEIDEA", ChannelStatus.DRAFT);
        var account = data.account(active.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        data.mid(active.getChannelId(), account.getChannelAccountId(), ENTITY_NO, ChannelStatus.DRAFT);

        get(CHANNEL_PATH).assertPage(1, 10, 3, 3);
        get(CHANNEL_PATH + "?keyword=NI").assertPage(1, 10, 2, 2);
        get(CHANNEL_PATH + "?keyword=N_").assertPage(1, 10, 0, 0);
        var activeOnly = get(CHANNEL_PATH + "?status=ACTIVE").assertPage(1, 10, 1, 1).item(0);
        assertAll(() -> assertEquals(active.getChannelId().toString(), activeOnly.path("channelId").asString()),
            () -> assertEquals(1, activeOnly.path("accountCount").asInt()),
            () -> assertEquals(1, activeOnly.path("midCount").asInt()));
        get(CHANNEL_PATH + "?page=2&size=10").assertPage(2, 10, 3, 0);
        get(CHANNEL_PATH + "?status=UNKNOWN").assertInvalidRequest();
    }

    /** 改名写入 UPDATE 变更记录；已关闭渠道拒绝改名且不写记录。 */
    @Test
    void test_updateChannel() {
        PaymentChannel channel = data.channel("NI", ChannelStatus.DRAFT);
        UpdateChannelRequestBody body = new UpdateChannelRequestBody();
        body.setChannelName("新名称");

        command(HttpMethod.PUT, CHANNEL_PATH + "/" + channel.getChannelId(), body).assertSuccess(200)
            .assertText("channelName", "新名称");
        PaymentChannel closed = data.channel("GEIDEA", ChannelStatus.CLOSED);
        command(HttpMethod.PUT, CHANNEL_PATH + "/" + closed.getChannelId(), body).assertBusinessRejected("渠道已关闭");

        assertEquals("新名称", testMapper.selectChannel(channel.getChannelId()).get("channel_name"));
        assertChange(changes(ChangeTargetType.CHANNEL, channel.getChannelId()).getFirst(), ChangeType.UPDATE.getCode(),
            null, null);
        assertEquals(List.of(), changes(ChangeTargetType.CHANNEL, closed.getChannelId()));
    }

    /** 激活前经 protocol 公开查询核对本库同编码接入方；存在则 ACTIVE 并记录 DRAFT→ACTIVE 与原因。 */
    @Test
    void test_activateChannel() {
        PaymentChannel channel = data.channel("NI", ChannelStatus.DRAFT);
        externalApi.integration("NI");

        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.ACTIVE, "上线")).assertSuccess(200)
            .assertText("status", ChannelStatus.ACTIVE.getCode());

        var change = changes(ChangeTargetType.CHANNEL, channel.getChannelId()).getFirst();
        assertAll(() -> assertEquals(ChannelStatus.ACTIVE.getCode(), testMapper.selectChannel(channel.getChannelId())
                .get("channel_status")),
            () -> assertChange(change, ChangeType.STATUS.getCode(), ChannelStatus.DRAFT.getCode(), ChannelStatus.ACTIVE.getCode()),
            () -> assertEquals("上线", change.get("reason")));
        transactions.assertOutsideTransactions();
    }

    /** 本库不存在同编码接入方（只有其他编码）时业务拒绝，渠道保持 DRAFT、不写变更记录。 */
    @Test
    void test_activateRejectedWhenIntegrationMissing() {
        PaymentChannel channel = data.channel("NI", ChannelStatus.DRAFT);
        externalApi.integration("GEIDEA");

        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.ACTIVE))
            .assertBusinessRejected("不存在同编码接入方: NI");

        assertUnchanged(channel, ChannelStatus.DRAFT);
    }

    /** ACTIVE → SUSPENDED → ACTIVE；恢复同样核对本库同编码接入方；两条 STATUS 记录。 */
    @Test
    void test_suspendAndResume() {
        PaymentChannel channel = data.channel("NI", ChannelStatus.ACTIVE);
        externalApi.integration("NI");

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
        PaymentChannel channel = data.channel("NI", ChannelStatus.DRAFT);

        command(HttpMethod.POST, statusPath(channel), status(ChannelStatus.SUSPENDED))
            .assertBusinessRejected("当前状态 DRAFT, 目标状态 SUSPENDED");

        assertUnchanged(channel, ChannelStatus.DRAFT);
    }

    /** 渠道下有未关闭的账号或 MID 时拒绝关闭；子项全部关闭后可关闭。 */
    @Test
    void test_closeBlockedByChildren() {
        PaymentChannel channel = data.channel("NI", ChannelStatus.ACTIVE);
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
        PaymentChannel channel = data.channel("NI", ChannelStatus.CLOSED);

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
    static String statusPath(PaymentChannel channel) {
        return CHANNEL_PATH + "/" + channel.getChannelId() + "/status";
    }

    /** 渠道状态未变且无变更记录。 */
    void assertUnchanged(PaymentChannel channel, ChannelStatus expected) {
        assertEquals(expected.getCode(), testMapper.selectChannel(channel.getChannelId()).get("channel_status"));
        assertEquals(0, testMapper.countConfigChangeRows());
    }
}
