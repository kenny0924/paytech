package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelMidRequestBody;
import com.paytech.channel.connector.api.channel.request.RebindChannelMidAccountRequestBody;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChangeType;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.base.AbstractChannelConfigApiTest;
import com.paytech.channel.connector.test.builder.ChannelConfigRequestBuilder;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;

/**
 * MID 管理：创建与同渠道外部 MID 唯一、账号归属与 OE 一致、改绑（跨渠道 / 跨 OE 拒绝）、激活前置、激活与关闭时间、详情含能力组。
 *
 * @see ChannelMidApi#createChannelMid(String, CreateChannelMidRequestBody)
 * @see ChannelMidApi#getChannelMid(String)
 * @see ChannelMidApi#rebindChannelMidAccount(String, String, RebindChannelMidAccountRequestBody)
 * @see ChannelMidApi#changeChannelMidStatus(String, String, ChangeChannelStatusRequestBody)
 */
class ChannelMidApiTest extends AbstractChannelConfigApiTest {
    /** 本类公共 ACTIVE 渠道。 */
    private PaymentChannel channel;

    /** 本类公共 ACTIVE 且已配置的账号。 */
    private ChannelAccount account;

    /** 本类公共：ACTIVE 的 NI 渠道与 AE001 账号。 */
    @BeforeEach
    void setUpClassMocks() {
        channel = data.channel("NI", ChannelStatus.ACTIVE);
        account = data.account(channel.getChannelId(), ENTITY_NO, TOKEN_A, ChannelStatus.ACTIVE);
    }

    /** 创建 DRAFT MID 并写入 CREATE 记录；详情无能力组。 */
    @Test
    void test_createMid() {
        ChannelCostResponseChecker created = createMid(account, "MID-001", ENTITY_NO).assertSuccess(201)
            .assertText("status", ChannelStatus.DRAFT.getCode()).assertText("externalMidCode", "MID-001");

        long midId = Long.parseLong(created.text("midId"));
        assertAll(() -> assertEquals(0, created.json().path("capabilities").size()),
            () -> assertEquals(account.getChannelAccountId(),
                ((Number)testMapper.selectMid(midId).get("channel_account_id")).longValue()),
            () -> assertChange(changes(ChangeTargetType.CHANNEL_MID, midId).getFirst(), ChangeType.CREATE.getCode(),
                null, null));
        get(MID_PATH + "/" + midId).assertSuccess(200).assertText("entityNo", ENTITY_NO);
    }

    /** 同渠道外部 MID 重复 409；不同渠道可重复。 */
    @Test
    void test_duplicateExternalMid() {
        createMid(account, "MID-001", ENTITY_NO).assertSuccess(201);

        assertConflict(createMid(account, "MID-001", ENTITY_NO));

        PaymentChannel other = data.channel("GEIDEA", ChannelStatus.ACTIVE);
        ChannelAccount otherAccount = data.account(other.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        command(HttpMethod.POST, MID_PATH, ChannelConfigRequestBuilder.mid(other.getChannelId(),
            otherAccount.getChannelAccountId(), "MID-001", ENTITY_NO)).assertSuccess(201);
        assertEquals(2, testMapper.countConfigChangeRows());
    }

    /** 账号 OE 不一致、账号属于其他渠道或已关闭时拒绝创建。 */
    @Test
    void test_createRejectsMismatchedAccount() {
        createMid(account, "MID-001", "SA001").assertBusinessRejected("OE 不一致");
        PaymentChannel other = data.channel("GEIDEA", ChannelStatus.ACTIVE);
        command(HttpMethod.POST, MID_PATH, ChannelConfigRequestBuilder.mid(other.getChannelId(),
            account.getChannelAccountId(), "MID-002", ENTITY_NO)).assertBusinessRejected("不属于该渠道");
        ChannelAccount closed = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.CLOSED);
        createMid(closed, "MID-003", ENTITY_NO).assertBusinessRejected("已关闭");

        assertEquals(0, testMapper.countConfigChangeRows());
    }

    /** 改绑到同渠道同 OE 账号成功并记 REBIND；跨 OE、跨渠道、同账号与已关闭 MID 拒绝。 */
    @Test
    void test_rebindAccount() {
        ChannelMid mid = data.mid(channel.getChannelId(), account.getChannelAccountId(), ENTITY_NO, ChannelStatus.ACTIVE);
        ChannelAccount sameOe = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        ChannelAccount otherOe = data.account(channel.getChannelId(), "SA001", null, ChannelStatus.DRAFT);
        PaymentChannel other = data.channel("GEIDEA", ChannelStatus.ACTIVE);
        ChannelAccount otherChannel = data.account(other.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);

        rebind(mid, otherOe).assertBusinessRejected("OE 不一致");
        rebind(mid, otherChannel).assertBusinessRejected("不属于 MID 所在渠道");
        rebind(mid, account).assertBusinessRejected("已绑定该技术账号");
        rebind(mid, sameOe).assertSuccess(200).assertText("channelAccountId", sameOe.getChannelAccountId().toString());

        assertEquals(sameOe.getChannelAccountId(),
            ((Number)testMapper.selectMid(mid.getMidId()).get("channel_account_id")).longValue());
        assertChange(changes(ChangeTargetType.CHANNEL_MID, mid.getMidId()).getFirst(), ChangeType.REBIND.getCode(),
            null, null);
        ChannelMid closed = data.mid(channel.getChannelId(), account.getChannelAccountId(), ENTITY_NO,
            ChannelStatus.CLOSED);
        rebind(closed, sameOe).assertBusinessRejected("MID 已关闭");
    }

    /** 激活要求渠道与当前账号均 ACTIVE；首次激活写激活时间，再次激活保持原值；关闭写关闭时间。 */
    @Test
    void test_statusLifecycle() {
        ChannelAccount draftAccount = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        ChannelMid blocked = data.mid(channel.getChannelId(), draftAccount.getChannelAccountId(), ENTITY_NO,
            ChannelStatus.DRAFT);
        command(HttpMethod.POST, statusPath(blocked), status(ChannelStatus.ACTIVE)).assertBusinessRejected("账号状态 DRAFT");
        ChannelMid mid = data.mid(channel.getChannelId(), account.getChannelAccountId(), ENTITY_NO, ChannelStatus.DRAFT);

        command(HttpMethod.POST, statusPath(mid), status(ChannelStatus.ACTIVE)).assertSuccess(200);
        Object activatedAt = testMapper.selectMid(mid.getMidId()).get("activated_at");
        command(HttpMethod.POST, statusPath(mid), status(ChannelStatus.SUSPENDED)).assertSuccess(200);
        command(HttpMethod.POST, statusPath(mid), status(ChannelStatus.ACTIVE)).assertSuccess(200);
        Map<String, Object> reactivated = testMapper.selectMid(mid.getMidId());
        assertAll(() -> assertNotNull(activatedAt), () -> assertEquals(activatedAt, reactivated.get("activated_at")),
            () -> assertNull(reactivated.get("closed_at")));
        command(HttpMethod.POST, statusPath(mid), status(ChannelStatus.CLOSED, "停用商户号")).assertSuccess(200)
            .assertText("status", ChannelStatus.CLOSED.getCode());

        assertAll(() -> assertNotNull(testMapper.selectMid(mid.getMidId()).get("closed_at")),
            () -> assertEquals(4, changes(ChangeTargetType.CHANNEL_MID, mid.getMidId()).size()),
            () -> assertEquals(0, changes(ChangeTargetType.CHANNEL_MID, blocked.getMidId()).size()));
        command(HttpMethod.POST, statusPath(mid), status(ChannelStatus.ACTIVE)).assertBusinessRejected("当前状态 CLOSED");
    }

    /** 以指定账号创建 MID。 */
    ChannelCostResponseChecker createMid(ChannelAccount owner, String externalMidCode, String entityNo) {
        return command(HttpMethod.POST, MID_PATH, ChannelConfigRequestBuilder.mid(channel.getChannelId(),
            owner.getChannelAccountId(), externalMidCode, entityNo));
    }

    /** 改绑到目标账号。 */
    ChannelCostResponseChecker rebind(ChannelMid mid, ChannelAccount target) {
        return command(HttpMethod.PUT, MID_PATH + "/" + mid.getMidId() + "/account",
            ChannelConfigRequestBuilder.rebind(target.getChannelAccountId()));
    }

    /** 状态路径。 */
    static String statusPath(ChannelMid mid) {
        return MID_PATH + "/" + mid.getMidId() + "/status";
    }
}
