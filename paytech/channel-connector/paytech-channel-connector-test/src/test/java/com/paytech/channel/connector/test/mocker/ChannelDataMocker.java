package com.paytech.channel.connector.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.math.BigDecimal;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.channel.connector.core.dal.dataobject.*;
import com.paytech.channel.connector.core.enums.*;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Getter;

/**
 * 只操作 mor_channel_connector 的本库 28 张表；Fixture ID 使用正式 Snowflake 接口
 * <li>10 §3.1 渠道表、渠道成本四表、变更记录表，以及渠道协议表（2026-10-22 第二段：t_channel 合并原接入方，协议表改名，新增调用授权表）。</li>
 * <li>清理按子表在前的依赖顺序执行；异步验证运行由 VerifyRunFixture（更高优先级）先确认结束。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class ChannelDataMocker implements Clear {
    private static final String DATABASE = "mor_channel_connector";

    private final IdGenerator ids;
    private final ChannelTestMapper mapper;
    private boolean reusable = true;
    private boolean cleanupOwned;

    /**
     * 只允许本服务专用测试库：mor_channel_connector，或并行任务隔离用的 mor_channel_connector_{后缀}
     * <li>隔离库经 SPRING_DATASOURCE_URL 指定；不允许指向其他服务的库。</li>
     */
    void assertDedicatedDatabase() {
        String name = mapper.databaseName();
        assertTrue(DATABASE.equals(name) || name != null && name.matches(DATABASE + "_[a-z0-9]{1,16}"),
            "channel-connector 测试只允许本地 mor_channel_connector 或其隔离副本: " + name);
    }

    /** 拒绝共享库和脏基线；缺表时明确提示手工初始化入口。 */
    public void assertEmpty() {
        assertTrue(reusable, "测试库清理失败，须人工核查后重新运行");
        try {
            assertDedicatedDatabase();
            assertEquals(0, totalRows(), "专用库非空，不自动删除原有数据");
            cleanupOwned = true;
        } catch (org.springframework.dao.DataAccessException failure) {
            throw new AssertionError("请先人工审查并应用 paytech-channel-connector-test/src/test/resources/init/ddl.sql 到 mor-local MySQL",
                failure);
        }
    }

    /** 为当前类准备同 OE 的启用渠道、账号、MID 和一项 PAY 能力。 */
    public Fixture activeFixture() {
        var channel = channel("NI", "合成测试渠道", ChannelType.PAYMENT, ChannelStatus.ACTIVE);
        var account = account(channel.getChannelId(), "00001", "A".repeat(43));
        var mid = mid(channel.getChannelId(), account.getChannelAccountId(), "00001");
        var fixture = new Fixture(channel, account, mid);
        capability(fixture, "*", "*", "AED", "ACTION.PAY", true);
        return fixture;
    }

    /** 为卡直付准备指定渠道编码与 OE 的启用三元组，以及 CARD 支付方式在该币种下的 PAY 能力。 */
    public Fixture cardFixture(String channelCode, String entityNo, String currency) {
        var channel = channel(channelCode, "合成卡直付渠道", ChannelType.PAYMENT, ChannelStatus.ACTIVE);
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

    /** 在已有条件组上增加一个布尔能力明细，复用已有组而不制造重复唯一键。 */
    public void capabilityDetail(Fixture fixture, String code, boolean enabled) {
        var detail = new MidCapabilityDetail();
        detail.setMidCapabilityDetailId(ids.nextId());
        detail.setMidCapabilityId(mapper.capabilityId(fixture.getMid().getMidId()));
        detail.setCapabilityCode(code);
        detail.setValueType(CapabilityValueType.BOOLEAN);
        detail.setBooleanValue(enabled ? 1 : 0);
        detail.setCreateTime(fixture.getMid().getCreateTime());
        detail.setUpdateTime(detail.getCreateTime());
        mapper.insertMidCapabilityDetail(detail);
    }

    /** 在已有条件组上增加一个 CODE 能力明细（例如 SECURITY.3DS_MODE），复用已有组而不制造重复唯一键。 */
    public void codeCapabilityDetail(Fixture fixture, String code, String value) {
        var detail = new MidCapabilityDetail();
        detail.setMidCapabilityDetailId(ids.nextId());
        detail.setMidCapabilityId(mapper.capabilityId(fixture.getMid().getMidId()));
        detail.setCapabilityCode(code);
        detail.setValueType(CapabilityValueType.CODE);
        detail.setCodeValue(value);
        detail.setCreateTime(fixture.getMid().getCreateTime());
        detail.setUpdateTime(detail.getCreateTime());
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

    // ---- CC-B 追加：渠道基础配置测试的指定状态夹具 ----

    /** 插入指定编码与状态的支付渠道（未配置出站地址）。 */
    public Channel channel(String channelCode, ChannelStatus status) {
        return channel(channelCode, "合成渠道 " + channelCode, ChannelType.PAYMENT, status);
    }

    /** 插入指定编码与状态的服务渠道（未配置出站地址）。 */
    public Channel serviceChannel(String channelCode, ChannelStatus status) {
        return channel(channelCode, "合成服务渠道 " + channelCode, ChannelType.SERVICE, status);
    }

    /** 插入指定编码、名称、类型与状态的渠道：版本 1，未配置出站地址。 */
    public Channel channel(String channelCode, String channelName, ChannelType type, ChannelStatus status) {
        var channel = new Channel();
        channel.setChannelId(ids.nextId());
        channel.setChannelCode(channelCode);
        channel.setChannelName(channelName);
        channel.setChannelType(type);
        channel.setChannelStatus(status);
        channel.setVersion(1);
        channel.setCreateTime(Instant.parse("2026-09-21T00:00:00Z"));
        channel.setUpdateTime(channel.getCreateTime());
        mapper.insertChannel(channel);
        return channel;
    }

    /** 插入指定状态的技术账号；token 为空表示未配置。 */
    public ChannelAccount account(long channelId, String entityNo, String token, ChannelStatus status) {
        var account = new ChannelAccount();
        account.setChannelAccountId(ids.nextId());
        account.setChannelId(channelId);
        account.setEntityNo(entityNo);
        account.setAccountName("测试技术账号");
        account.setAccountVaultToken(token);
        account.setAccountStatus(status);
        account.setCreateTime(Instant.parse("2026-09-21T00:00:00Z"));
        account.setUpdateTime(account.getCreateTime());
        mapper.insertChannelAccount(account);
        return account;
    }

    /** 插入指定状态的 MID。 */
    public ChannelMid mid(long channelId, long accountId, String entityNo, ChannelStatus status) {
        var mid = new ChannelMid();
        mid.setMidId(ids.nextId());
        mid.setChannelId(channelId);
        mid.setChannelAccountId(accountId);
        mid.setEntityNo(entityNo);
        mid.setExternalMidCode("test-mid-" + mid.getMidId());
        mid.setMidStatus(status);
        mid.setCreateTime(Instant.parse("2026-09-21T00:00:00Z"));
        mid.setUpdateTime(mid.getCreateTime());
        mapper.insertChannelMid(mid);
        return mid;
    }

    /** 按依赖顺序清理本库全部表，任一失败即禁止环境复用。 */
    @Override
    public void clear() {
        if (!cleanupOwned) {
            return;
        }
        assertDedicatedDatabase();
        try {
            assertAll(mapper::clearConfigChanges, mapper::clearChannelCostComponents, mapper::clearChannelCostScopes,
                mapper::clearChannelCostVersions, mapper::clearChannelCostPlans, mapper::clearCompensationEvents,
                mapper::clearEventExceptions, mapper::clearCallbackItems,
                mapper::clearCallbacks, mapper::clearInteractions, mapper::clearRoutingPolicyMids,
                mapper::clearRoutingPolicyVersions, mapper::clearRoutingPolicies, mapper::clearDetails,
                mapper::clearCapabilities, mapper::clearMids, mapper::clearAccounts, mapper::clearChannelCallGrants,
                mapper::clearVerifyRunSteps, mapper::clearVerifyRuns, mapper::clearVerifyCases,
                mapper::clearVerifyAccounts, mapper::clearImplementations, mapper::clearChannelApis,
                mapper::clearChannelScripts, mapper::clearApiFields, mapper::clearChannels,
                mapper::clearStandardApis);
            assertEquals(0, totalRows());
            cleanupOwned = false;
        } catch (RuntimeException | AssertionError failure) {
            reusable = false;
            throw failure;
        }
    }

    /** 本库 28 张表的总行数。 */
    int totalRows() {
        return mapper.countRows() + mapper.countSchemaOnlyRows() + mapper.countChannelCostRows()
            + mapper.countConfigChangeRows() + mapper.countProtocolRows();
    }

    /** 保留本例三元组供请求 Builder 和断言使用，不作为生产模型。 */
    @Getter
    @AllArgsConstructor
    public static class Fixture {
        private final Channel channel;
        private final ChannelAccount account;
        private final ChannelMid mid;
    }
}
