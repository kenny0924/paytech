package com.paytech.channel.connector.service.processor;

import java.util.List;
import java.util.Objects;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.core.configuration.ChannelProperties;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.channel.connector.domain.CapabilityView;
import com.paytech.channel.connector.domain.ChannelExecutionContext;
import com.paytech.channel.connector.domain.MidCapabilityContext;
import com.paytech.channel.connector.domainservice.repository.*;
import com.paytech.channel.connector.service.processor.request.GetChannelExecutionConfigRequest;

/**
 * 冻结坐标执行入口的公共校验：按用途检查三元组归属、状态与新支付能力，在一致快照中读取，不持有跨网络事务。
 *
 * <p>
 * 执行配置读取（Reveal）与进程内执行渠道操作（Reveal 由 protocol 初始化函数完成）共用同一套规则。
 */
public abstract class AbstractChannelExecutionProcessor<Q, R> extends AbstractProcessor<Q, R> {
    private final ChannelProperties properties;
    private final ChannelMidRepository mids;
    private final MidCapabilityRepository capabilities;
    private final ChannelAccountRepository accounts;
    private final PaymentChannelRepository channels;
    private final PlatformTransactionManager transactionManager;
    private final MidCapabilityDetailRepository details;

    /** 注入冻结坐标校验所需的本模块仓储。 */
    protected AbstractChannelExecutionProcessor(ChannelProperties properties, ChannelMidRepository mids,
        MidCapabilityRepository capabilities, ChannelAccountRepository accounts, PaymentChannelRepository channels,
        PlatformTransactionManager transactionManager, MidCapabilityDetailRepository details) {
        this.properties = properties;
        this.mids = mids;
        this.capabilities = capabilities;
        this.accounts = accounts;
        this.channels = channels;
        this.transactionManager = transactionManager;
        this.details = details;
    }

    /** 本次请求的冻结坐标与用途。 */
    protected abstract GetChannelExecutionConfigRequest target(Q request);

    /** 新支付需要完整交易事实；原交易后续操作与原操作恢复分别表达用途。 */
    @Override
    protected void validate(Q request) {
        var target = target(request);
        if (target.getPurpose() == ExecutionPurpose.NEW_PAYMENT) {
            if (target.getCriteria() == null || target.getAmount() == null
                || !List.of(ChannelOperation.PAY, ChannelOperation.AUTHORIZE, ChannelOperation.AUTHENTICATE)
                    .contains(target.getOperation())) {
                throw new InvalidParameterException("新支付必须提供交易维度和金额，且操作必须属于新支付流程");
            }
        } else if (target.getPurpose() == ExecutionPurpose.ORIGINAL_TRANSACTION && !List
            .of(ChannelOperation.CAPTURE, ChannelOperation.VOID, ChannelOperation.REFUND, ChannelOperation.QUERY)
            .contains(target.getOperation())) {
            throw new InvalidParameterException("原交易后续处理仅支持捕获、撤销、退款和查单");
        }
    }

    /** 在可重复读的只读事务中读取并校验冻结坐标；返回后再进行任何外部访问。 */
    protected ChannelExecutionContext readChecked(GetChannelExecutionConfigRequest request) {
        var read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(status -> loadAndCheck(request));
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
        String action = ChannelCapabilityCodes.ACTION_PREFIX + request.getOperation().getCode();
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
        if (ChannelCapabilityCodes.AMOUNT_MIN.equals(value.getCapabilityCode())
            && request.getAmount().compareTo(value.getDecimalValue()) < 0
            || ChannelCapabilityCodes.AMOUNT_MAX.equals(value.getCapabilityCode())
                && request.getAmount().compareTo(value.getDecimalValue()) > 0) {
            throw new BizCheckFailException("交易金额不满足 MID 技术限制");
        }
    }
}
