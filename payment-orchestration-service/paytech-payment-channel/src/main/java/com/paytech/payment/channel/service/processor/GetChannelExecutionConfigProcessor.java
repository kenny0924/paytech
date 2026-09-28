package com.paytech.payment.channel.service.processor;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payment.channel.core.configuration.ChannelProperties;
import com.paytech.payment.channel.core.dal.dataobject.MidCapability;
import com.paytech.payment.channel.core.enums.ChannelOperation;
import com.paytech.payment.channel.core.enums.ChannelStatus;
import com.paytech.payment.channel.core.enums.ExecutionPurpose;
import com.paytech.payment.channel.domain.CapabilityView;
import com.paytech.payment.channel.domain.ChannelExecutionConfigView;
import com.paytech.payment.channel.domain.ChannelExecutionContext;
import com.paytech.payment.channel.domain.MidCapabilityContext;
import com.paytech.payment.channel.domainservice.repository.*;
import com.paytech.payment.channel.integration.ChannelAccountConfigReader;
import com.paytech.payment.channel.service.processor.request.GetChannelExecutionConfigRequest;

import lombok.AllArgsConstructor;

/** 受控执行边界的配置入口；原交易坐标必须由调用方从权威交易事实取得。 */
@Component
@AllArgsConstructor
public class GetChannelExecutionConfigProcessor
    extends AbstractProcessor<GetChannelExecutionConfigRequest, ChannelExecutionConfigView> {
    private final ChannelProperties properties;
    private final ChannelMidRepository mids;
    private final MidCapabilityRepository capabilities;
    private final ChannelAccountRepository accounts;
    private final PaymentChannelRepository channels;
    private final ChannelAccountConfigReader configReader;
    private final PlatformTransactionManager transactionManager;
    private final MidCapabilityDetailRepository details;

    /** 新支付需要完整交易事实；原交易后续操作与原操作恢复分别表达用途。 */
    @Override
    protected void validate(GetChannelExecutionConfigRequest request) {
        if (request.getPurpose() == ExecutionPurpose.NEW_PAYMENT) {
            if (request.getCriteria() == null || request.getAmount() == null
                || !List.of(ChannelOperation.PAY, ChannelOperation.AUTHORIZE, ChannelOperation.AUTHENTICATE)
                    .contains(request.getOperation())) {
                throw new InvalidParameterException("新支付必须提供交易维度和金额，且操作必须属于新支付流程");
            }
        } else if (request.getPurpose() == ExecutionPurpose.ORIGINAL_TRANSACTION && !List
            .of(ChannelOperation.CAPTURE, ChannelOperation.VOID, ChannelOperation.REFUND, ChannelOperation.QUERY)
            .contains(request.getOperation())) {
            throw new InvalidParameterException("原交易后续处理仅支持捕获、撤销、退款和查单");
        }
    }

    /** 本地一致读取结束后才 Reveal；不持有跨网络事务、配置锁或明文缓存。 */
    @Override
    protected ChannelExecutionConfigView processRequest(GetChannelExecutionConfigRequest request) {
        var read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var context = read.execute(status -> loadAndCheck(request));
        var accountConfiguration = configReader.read(context);
        var result = new ChannelExecutionConfigView();
        result.setChannelCode(context.getChannel().getChannelCode());
        result.setExternalMidCode(context.getMid().getExternalMidCode());
        result.setAccountConfiguration(accountConfiguration);
        return result;
    }

    /** 指定坐标缺失、跨 OE 或跨 Channel 均拒绝，绝不重选三元组。 */
    ChannelExecutionContext loadAndCheck(GetChannelExecutionConfigRequest request) {
        var channel = channels.findById(request.getChannelId());
        var account = accounts.findById(request.getChannelAccountId());
        var mid = mids.findById(request.getMidId());
        if (channel == null || account == null || mid == null) {
            throw new BizCheckFailException("冻结渠道、账号或 MID 不存在");
        }
        if (!Objects.equals(channel.getChannelId(), account.getChannelId())
            || !Objects.equals(channel.getChannelId(), mid.getChannelId())
            || !request.getEntityNo().equals(account.getEntityNo())
            || !request.getEntityNo().equals(mid.getEntityNo())) {
            throw new BizCheckFailException("冻结渠道、账号或 MID 的 Channel/OE 归属不一致");
        }
        var context = new ChannelExecutionContext(channel, account, mid);
        checkStatus(context, request);
        if (request.getPurpose() == ExecutionPurpose.NEW_PAYMENT) {
            checkNewPaymentCapabilities(request);
        }
        return context;
    }

    /** 原交易允许暂停及关闭状态；当前绑定只约束新支付，不覆盖历史冻结账号。 */
    void checkStatus(ChannelExecutionContext context, GetChannelExecutionConfigRequest request) {
        var states = List.of(context.getChannel().getChannelStatus(), context.getAccount().getAccountStatus(),
            context.getMid().getMidStatus());
        if (states.contains(ChannelStatus.DRAFT)) {
            throw new BizCheckFailException("DRAFT 渠道配置不能用于执行");
        }
        if (request.getPurpose() == ExecutionPurpose.NEW_PAYMENT
            && (states.stream().anyMatch(state -> state != ChannelStatus.ACTIVE)
                || !request.getChannelAccountId().equals(context.getMid().getChannelAccountId()))) {
            throw new BizCheckFailException("新支付要求启用配置及 MID 当前绑定账号");
        }
    }

    /** 执行新支付前重新匹配动作及金额能力；原操作恢复不套用当前新交易准入限制。 */
    void checkNewPaymentCapabilities(GetChannelExecutionConfigRequest request) {
        var scopes = capabilities.findByMidIds(List.of(request.getMidId()));
        var values = details.findByCapabilityIds(scopes.stream().map(MidCapability::getMidCapabilityId).toList());
        var resolved = new MidCapabilityContext(scopes, values).resolve(request.getCriteria(),
            properties.getCapabilityDefinitions());
        String action = "ACTION." + request.getOperation().getCode();
        if (resolved.stream().noneMatch(
            value -> action.equals(value.getCapabilityCode()) && Boolean.TRUE.equals(value.getBooleanValue()))) {
            throw new BizCheckFailException("MID 未明确支持本次新支付操作");
        }
        for (var value : resolved) {
            checkAmount(request, value);
        }
    }

    /** 使用精确主单位金额与本次币种匹配后的上下限比较，不在渠道查询中舍入。 */
    void checkAmount(GetChannelExecutionConfigRequest request, CapabilityView value) {
        if ("AMOUNT.MIN".equals(value.getCapabilityCode()) && request.getAmount().compareTo(value.getDecimalValue()) < 0
            || "AMOUNT.MAX".equals(value.getCapabilityCode())
                && request.getAmount().compareTo(value.getDecimalValue()) > 0) {
            throw new BizCheckFailException("交易金额不满足 MID 技术限制");
        }
    }
}
