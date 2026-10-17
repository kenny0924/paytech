package com.paytech.payment.integration.channel;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.payment.channel.core.enums.ChannelOperation;
import com.paytech.payment.channel.core.enums.ExecutionPurpose;
import com.paytech.payment.channel.domain.CapabilityView;
import com.paytech.payment.channel.domain.ChannelOperationResult;
import com.paytech.payment.channel.domain.MidCandidateView;
import com.paytech.payment.channel.service.processor.ExecuteChannelOperationProcessor;
import com.paytech.payment.channel.service.processor.QueryMidCandidatesProcessor;
import com.paytech.payment.channel.service.processor.request.ExecuteChannelOperationRequest;
import com.paytech.payment.channel.service.processor.request.GetChannelExecutionConfigRequest;
import com.paytech.payment.channel.service.processor.request.MidCriteria;
import com.paytech.payment.channel.service.processor.request.QueryMidCandidatesRequest;
import com.paytech.payment.execution.core.enums.ChannelExecutionOutcome;
import com.paytech.payment.execution.domain.ChannelExecutionCommand;
import com.paytech.payment.execution.domain.ChannelExecutionResult;
import com.paytech.payment.execution.integration.PaymentChannelClient;
import com.paytech.payment.routing.domain.RouteCandidate;
import com.paytech.payment.routing.domain.RouteContext;

import lombok.AllArgsConstructor;

/**
 * Execution 的 Channel 端口实现：Channel 与 Execution 是互相隔离的能力模块，由 Web 组合根调用 Channel 公开 Processor 并转换领域模型。
 * <li>两个调用都在数据库事务外进行。</li>
 */
@Component
@AllArgsConstructor
public class PaymentChannelClientImpl implements PaymentChannelClient {
    private final QueryMidCandidatesProcessor queryMidCandidatesProcessor;
    private final ExecuteChannelOperationProcessor executeChannelOperationProcessor;

    /** 查询 MID 候选并投影为路由候选（动作能力与本币种金额上下限）。 */
    @Override
    public List<RouteCandidate> queryRouteCandidates(RouteContext context) {
        var request = new QueryMidCandidatesRequest();
        request.setEntityNo(context.getEntityNo());
        request.setCriteria(criteria(context.getPaymentMethodCode(), context.getCountryCode(),
            context.getAmount().getCurrency(), context.getMccCode()));
        return queryMidCandidatesProcessor.process(request).stream().map(PaymentChannelClientImpl::candidate).toList();
    }

    /** 以新支付用途执行冻结坐标上的渠道动作。 */
    @Override
    public ChannelExecutionResult execute(ChannelExecutionCommand command) {
        var request = new ExecuteChannelOperationRequest();
        request.setTarget(target(command));
        request.setStandardApiCode(command.getStandardApiCode());
        request.setInput(command.getInput());
        ChannelOperationResult result = executeChannelOperationProcessor.process(request);
        return new ChannelExecutionResult(
            EnumUtils.getEnumByCode(ChannelExecutionOutcome.class, result.getOutcome().getCode()), result.getOutputs(),
            result.getFailureCode());
    }

    /** 新支付的冻结坐标与交易维度。 */
    static GetChannelExecutionConfigRequest target(ChannelExecutionCommand command) {
        var target = new GetChannelExecutionConfigRequest();
        target.setEntityNo(command.getEntityNo());
        target.setChannelId(command.getChannelId());
        target.setChannelAccountId(command.getChannelAccountId());
        target.setMidId(command.getMidId());
        target.setOperation(EnumUtils.getEnumByCode(ChannelOperation.class, command.getOperation()));
        target.setPurpose(ExecutionPurpose.NEW_PAYMENT);
        target.setCriteria(criteria(command.getPaymentMethodCode(), command.getCountryCode(), command.getCurrency(),
            command.getMccCode()));
        target.setAmount(command.getAmount());
        return target;
    }

    /** MID 能力匹配维度；卡组织暂不参与。 */
    static MidCriteria criteria(String paymentMethodCode, String countryCode, String currency, String mccCode) {
        var criteria = new MidCriteria();
        criteria.setPaymentMethodCode(paymentMethodCode);
        criteria.setCountryCode(countryCode);
        criteria.setCurrency(currency);
        criteria.setMccCode(mccCode);
        return criteria;
    }

    /** 候选能力投影：ACTION.* 为 true 的动作与 AMOUNT.MIN/MAX。 */
    static RouteCandidate candidate(MidCandidateView view) {
        var candidate = new RouteCandidate();
        candidate.setChannelId(view.getChannelId());
        candidate.setChannelAccountId(view.getChannelAccountId());
        candidate.setMidId(view.getMidId());
        var actions = new LinkedHashSet<String>();
        for (CapabilityView capability : view.getCapabilities()) {
            String code = capability.getCapabilityCode();
            if (code.startsWith("ACTION.") && Boolean.TRUE.equals(capability.getBooleanValue())) {
                actions.add(code.substring("ACTION.".length()));
            }
            candidate.setMinAmount("AMOUNT.MIN".equals(code) ? decimal(capability) : candidate.getMinAmount());
            candidate.setMaxAmount("AMOUNT.MAX".equals(code) ? decimal(capability) : candidate.getMaxAmount());
        }
        candidate.setSupportedActions(actions);
        return candidate;
    }

    /** 金额能力值。 */
    static BigDecimal decimal(CapabilityView capability) {
        return capability.getDecimalValue();
    }
}
