package com.paytech.payment.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.math.BigDecimal;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.payment.channel.core.dal.dataobject.*;
import com.paytech.payment.channel.core.enums.*;
import com.paytech.payment.test.dal.ChannelTestMapper;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Getter;

/** 只操作专用 Channel 测试数据库；Fixture ID 使用正式 Snowflake 接口。 */
@TestComponent
@RequiredArgsConstructor
public class ChannelDataMocker implements Clear {
    private final IdGenerator ids;
    private final ChannelTestMapper mapper;
    private boolean reusable = true;
    private boolean cleanupOwned;

    /** 拒绝共享库和脏基线；缺表时明确提示手工初始化入口。 */
    public void assertEmpty() {
        assertTrue(reusable, "测试库清理失败，须人工核查后重新运行");
        try {
            assertEquals("mor_payment_channel_test", mapper.databaseName());
            assertEquals(0, mapper.countRows() + mapper.countExecutionRows(), "专用库非空，不自动删除原有数据");
            cleanupOwned = true;
        } catch (org.springframework.dao.DataAccessException failure) {
            throw new AssertionError("请先人工审查并应用 paytech-payment-test/src/test/resources/init/ddl.sql 到 mor-local MySQL", failure);
        }
    }

    /** 为当前类准备同 OE 的启用渠道、账号、MID 和一项 PAY 能力。 */
    public Fixture activeFixture() {
        var now = Instant.parse("2026-09-21T00:00:00Z");
        var channel = new PaymentChannel();
        channel.setChannelId(ids.nextId());
        channel.setChannelCode("NI");
        channel.setChannelName("合成测试渠道");
        channel.setChannelStatus(ChannelStatus.ACTIVE);
        channel.setCreateTime(now);
        channel.setUpdateTime(now);
        mapper.insertPaymentChannel(channel);
        var account = account(channel.getChannelId(), "00001", "A".repeat(43));
        var mid = mid(channel.getChannelId(), account.getChannelAccountId(), "00001");
        var fixture = new Fixture(channel, account, mid);
        capability(fixture, "*", "*", "AED", "ACTION.PAY", true);
        return fixture;
    }

    /** 为卡直付准备指定渠道编码与 OE 的启用三元组，以及 CARD 支付方式在该币种下的 PAY 能力。 */
    public Fixture cardFixture(String channelCode, String entityNo, String currency) {
        var now = Instant.parse("2026-09-21T00:00:00Z");
        var channel = new PaymentChannel();
        channel.setChannelId(ids.nextId());
        channel.setChannelCode(channelCode);
        channel.setChannelName("合成卡直付渠道");
        channel.setChannelStatus(ChannelStatus.ACTIVE);
        channel.setCreateTime(now);
        channel.setUpdateTime(now);
        mapper.insertPaymentChannel(channel);
        var account = account(channel.getChannelId(), entityNo, "B".repeat(43));
        var mid = mid(channel.getChannelId(), account.getChannelAccountId(), entityNo);
        var fixture = new Fixture(channel, account, mid);
        capability(fixture, "*", "*", currency, "ACTION.PAY", true);
        return fixture;
    }

    /** 插入同一渠道可存在的另一技术账号。 */
    public ChannelAccount account(long channelId, String entityNo, String token) {
        var account = new ChannelAccount();
        account.setChannelAccountId(ids.nextId());
        account.setChannelId(channelId);
        account.setEntityNo(entityNo);
        account.setAccountName("测试技术账号");
        account.setAccountVaultToken(token);
        account.setAccountStatus(ChannelStatus.ACTIVE);
        account.setCreateTime(Instant.parse("2026-09-21T00:00:00Z"));
        account.setUpdateTime(account.getCreateTime());
        mapper.insertChannelAccount(account);
        return account;
    }

    /** 插入由给定 OE 和账号拥有的 MID。 */
    public ChannelMid mid(long channelId, long accountId, String entityNo) {
        var mid = new ChannelMid();
        mid.setMidId(ids.nextId());
        mid.setChannelId(channelId);
        mid.setChannelAccountId(accountId);
        mid.setEntityNo(entityNo);
        mid.setExternalMidCode("test-mid-" + mid.getMidId());
        mid.setMidStatus(ChannelStatus.ACTIVE);
        mid.setCreateTime(Instant.parse("2026-09-21T00:00:00Z"));
        mid.setUpdateTime(mid.getCreateTime());
        mapper.insertChannelMid(mid);
        return mid;
    }

    /** 插入一个完整条件组及能力明细，不调用被测入口安排数据。 */
    public void capability(Fixture fixture, String scheme, String country, String currency, String code, boolean enabled) {
        var scope = new MidCapability();
        scope.setMidCapabilityId(ids.nextId());
        scope.setMidId(fixture.getMid().getMidId());
        scope.setPaymentMethodCode("CARD");
        scope.setSchemeCode(scheme);
        scope.setCountryCode(country);
        scope.setCurrency(currency);
        scope.setMccCode("5734");
        scope.setCapabilityStatus(CapabilityStatus.ENABLED);
        scope.setCreateTime(fixture.getMid().getCreateTime());
        scope.setUpdateTime(scope.getCreateTime());
        mapper.insertMidCapability(scope);
        var detail = new MidCapabilityDetail();
        detail.setMidCapabilityDetailId(ids.nextId());
        detail.setMidCapabilityId(scope.getMidCapabilityId());
        detail.setCapabilityCode(code);
        detail.setValueType(CapabilityValueType.BOOLEAN);
        detail.setBooleanValue(enabled ? 1 : 0);
        detail.setCreateTime(scope.getCreateTime());
        detail.setUpdateTime(scope.getCreateTime());
        mapper.insertMidCapabilityDetail(detail);
    }

    /** 在明确币种条件下增加精确金额上下限，复用已有组而不制造重复唯一键。 */
    public void amountBounds(Fixture fixture, BigDecimal minimum, BigDecimal maximum) {
        long scopeId = mapper.capabilityId(fixture.getMid().getMidId());
        for (String code : java.util.List.of("AMOUNT.MIN", "AMOUNT.MAX")) {
            var detail = new MidCapabilityDetail();
            detail.setMidCapabilityDetailId(ids.nextId());
            detail.setMidCapabilityId(scopeId);
            detail.setCapabilityCode(code);
            detail.setValueType(CapabilityValueType.DECIMAL);
            detail.setDecimalValue("AMOUNT.MIN".equals(code) ? minimum : maximum);
            detail.setCreateTime(fixture.getMid().getCreateTime());
            detail.setUpdateTime(detail.getCreateTime());
            mapper.insertMidCapabilityDetail(detail);
        }
    }

    /** 按依赖顺序清理所有本测试库记录，任一失败即禁止环境复用。 */
    @Override
    public void clear() {
        if (!cleanupOwned) {
            return;
        }
        assertEquals("mor_payment_channel_test", mapper.databaseName());
        try {
            assertAll(mapper::clearTransactionRefs, mapper::clearActions, mapper::clearAttempts,
                mapper::clearInstrumentTokens, mapper::clearIntents, mapper::clearDetails, mapper::clearCapabilities,
                mapper::clearMids, mapper::clearAccounts, mapper::clearChannels);
            assertEquals(0, mapper.countRows() + mapper.countExecutionRows());
            cleanupOwned = false;
        } catch (RuntimeException | AssertionError failure) {
            reusable = false;
            throw failure;
        }
    }

    /** 保留本例三元组供请求 Builder 和断言使用，不作为生产模型。 */
    @Getter
    @AllArgsConstructor
    public static class Fixture {
        private final PaymentChannel channel;
        private final ChannelAccount account;
        private final ChannelMid mid;
    }
}
