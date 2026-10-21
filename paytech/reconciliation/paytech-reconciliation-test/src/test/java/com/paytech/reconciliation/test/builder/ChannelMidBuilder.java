package com.paytech.reconciliation.test.builder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.paytech.channel.connector.api.channel.response.ChannelMidResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidSummaryResponseBody;
import com.paytech.channel.connector.api.channel.response.MidCapabilityResponseBody;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * channel-connector MID 响应 DTO 的合法模板：同一 MID 的列表摘要（含 channelCode）与详情（含能力组币种）
 * <li>MID 详情没有 channelCode，只有 channelId；渠道编码只在列表摘要里（核对渠道时需用列表或另查渠道）。</li>
 * <li>默认 ACTIVE、经营主体 AE001、渠道 NI，能力组币种 USD；用例可改状态、主体、渠道或追加币种构造负例。</li>
 */
public final class ChannelMidBuilder {
    /** 测试渠道主键（与渠道编码 NI 对应）。 */
    public static final String CHANNEL_ID = "77";

    private final ChannelMidSummaryResponseBody summary = new ChannelMidSummaryResponseBody();

    private final ChannelMidResponseBody detail = new ChannelMidResponseBody();

    private ChannelMidBuilder() {
    }

    /** ACTIVE MID，能力组只有 USD 一个币种。 */
    public static ChannelMidBuilder active(long midId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ChannelMidBuilder builder = new ChannelMidBuilder();
        String id = String.valueOf(midId);
        String accountId = String.valueOf(midId + 1);
        builder.summary.setMidId(id);
        builder.summary.setChannelId(CHANNEL_ID);
        builder.summary.setChannelCode(ReconciliationTestConstants.CHANNEL_CODE);
        builder.summary.setChannelAccountId(accountId);
        builder.summary.setAccountName("Account " + accountId);
        builder.summary.setExternalMidCode("MID" + midId);
        builder.summary.setEntityNo(ReconciliationTestConstants.ENTITY_NO);
        builder.summary.setStatus("ACTIVE");
        builder.summary.setActivatedAt(now);
        builder.summary.setUpdateTime(now);
        builder.detail.setMidId(id);
        builder.detail.setChannelId(CHANNEL_ID);
        builder.detail.setChannelAccountId(accountId);
        builder.detail.setExternalMidCode("MID" + midId);
        builder.detail.setEntityNo(ReconciliationTestConstants.ENTITY_NO);
        builder.detail.setStatus("ACTIVE");
        builder.detail.setActivatedAt(now);
        builder.detail.setCreateTime(now);
        builder.detail.setUpdateTime(now);
        builder.detail.setCapabilities(new ArrayList<>());
        return builder.currency(ReconciliationTestConstants.CURRENCY);
    }

    /** 追加一个启用的能力组（卡支付，指定币种）。 */
    public ChannelMidBuilder currency(String currency) {
        MidCapabilityResponseBody capability = new MidCapabilityResponseBody();
        capability.setMidCapabilityId(String.valueOf(ReconciliationTestConstants.nextId()));
        capability.setMidId(detail.getMidId());
        capability.setPaymentMethodCode("CARD");
        capability.setCurrency(currency);
        capability.setStatus("ENABLED");
        capability.setCreateTime(detail.getCreateTime());
        capability.setUpdateTime(detail.getUpdateTime());
        capability.setDetails(List.of());
        detail.getCapabilities().add(capability);
        summary.setCapabilityGroupCount(detail.getCapabilities().size());
        summary.setEnabledCapabilityGroupCount(detail.getCapabilities().size());
        return this;
    }

    /** MID 状态（摘要与详情同时修改）：DRAFT、ACTIVE、SUSPENDED、CLOSED。 */
    public ChannelMidBuilder status(String status) {
        summary.setStatus(status);
        detail.setStatus(status);
        return this;
    }

    /** 经营主体（摘要与详情同时修改）。 */
    public ChannelMidBuilder entityNo(String entityNo) {
        summary.setEntityNo(entityNo);
        detail.setEntityNo(entityNo);
        return this;
    }

    /** 渠道（摘要的渠道编码与两者的渠道主键同时修改）。 */
    public ChannelMidBuilder channel(String channelId, String channelCode) {
        summary.setChannelId(channelId);
        summary.setChannelCode(channelCode);
        detail.setChannelId(channelId);
        return this;
    }

    /** 列表摘要。 */
    public ChannelMidSummaryResponseBody summary() {
        return summary;
    }

    /** 详情。 */
    public ChannelMidResponseBody detail() {
        return detail;
    }
}
