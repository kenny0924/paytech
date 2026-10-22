package com.paytech.payment.execution.integration.impl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.candidate.ChannelCandidateQueryApi;
import com.paytech.channel.connector.api.candidate.request.MidCriteriaBody;
import com.paytech.channel.connector.api.candidate.request.QueryMidCandidatesRequestBody;
import com.paytech.channel.connector.api.candidate.response.MidCandidateBody;
import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.channel.connector.api.operation.ChannelOperationApi;
import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;
import com.paytech.channel.connector.api.operation.response.ChannelOperationResponseBody;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.payment.execution.core.enums.ChannelExecutionOutcome;
import com.paytech.payment.execution.domain.ChannelExecutionCommand;
import com.paytech.payment.execution.domain.ChannelExecutionResult;
import com.paytech.payment.execution.integration.PaymentChannelClient;
import com.paytech.payment.routing.domain.RouteCandidate;
import com.paytech.payment.routing.domain.RouteContext;

import lombok.AllArgsConstructor;

/**
 * 渠道端口的 HTTP 实现：候选查询与渠道操作均经 channel-connector 合同（D06），两个调用都在数据库事务外进行。
 * <li>能力代码、Platform 输出键与 NOT_SENT 判定都由 channel-connector 解释；本类只做合同 DTO 与领域模型的转换。</li>
 * <li>渠道操作的 HTTP 失败（不可达、超时、4xx/5xx）由调用方按结果未知处理，不重试。</li>
 */
@Component
@AllArgsConstructor
public class PaymentChannelClientImpl implements PaymentChannelClient {
    private final ChannelCandidateQueryApi channelCandidateQueryApi;
    private final ChannelOperationApi channelOperationApi;

    /** 查询 Intent 冻结 OE 内的 MID 候选并转为路由候选。 */
    @Override
    public List<RouteCandidate> queryRouteCandidates(RouteContext context) {
        var request = new QueryMidCandidatesRequestBody();
        request.setEntityNo(context.getEntityNo());
        request.setCriteria(criteria(context.getPaymentMethodCode(), context.getSchemeCode(), context.getCountryCode(),
            context.getAmount().getCurrency(), context.getMccCode()));
        return channelCandidateQueryApi.queryMidCandidates(request).getCandidates().stream()
            .map(PaymentChannelClientImpl::candidate).toList();
    }

    /** 按命令的用途在冻结坐标上执行渠道动作（新支付或原交易查单）。 */
    @Override
    public ChannelExecutionResult execute(ChannelExecutionCommand command) {
        ChannelOperationResponseBody response = channelOperationApi.execute(request(command));
        ChannelExecutionOutcome outcome = EnumUtils.getEnumByCode(ChannelExecutionOutcome.class, response.getOutcome());
        if (outcome == null) {
            return ChannelExecutionResult.unknown();
        }
        return new ChannelExecutionResult(outcome,
            EnumUtils.getEnumByCode(ChannelPaymentStatus.class, response.getPaymentStatus()), response.getRedirectUrl(),
            response.getNextActionType(), response.getChannelTransactionRef(), response.getChannelStatus(),
            response.getFailureCode(), response.getFailureMessage(), amount(response.getAmount()),
            response.getCurrency());
    }

    /** 冻结坐标与标准 API 输入；新支付另带交易维度与金额。ID 与金额为十进制字符串。 */
    static ExecuteChannelOperationRequestBody request(ChannelExecutionCommand command) {
        var request = new ExecuteChannelOperationRequestBody();
        request.setEntityNo(command.getEntityNo());
        request.setChannelId(command.getChannelId().toString());
        request.setChannelAccountId(command.getChannelAccountId().toString());
        request.setMidId(command.getMidId().toString());
        request.setOperation(command.getOperation().getCode());
        request.setPurpose(command.getPurpose().getCode());
        if (command.getPurpose() == ExecutionPurpose.NEW_PAYMENT) {
            request.setCriteria(criteria(command.getPaymentMethodCode(), command.getSchemeCode(),
                command.getCountryCode(), command.getCurrency(), command.getMccCode()));
            request.setAmount(command.getAmount().toPlainString());
        }
        request.setInput(new LinkedHashMap<>(command.getInput()));
        return request;
    }

    /** 查单返回的金额；不是合法十进制时按未返回处理，由调用方视为金额不一致。 */
    static BigDecimal amount(String amount) {
        if (amount == null) {
            return null;
        }
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** MID 能力匹配维度；卡组织未识别时不传，只命中配置为 * 的能力。 */
    static MidCriteriaBody criteria(String paymentMethodCode, String schemeCode, String countryCode, String currency,
        String mccCode) {
        var criteria = new MidCriteriaBody();
        criteria.setPaymentMethodCode(paymentMethodCode);
        criteria.setSchemeCode(schemeCode);
        criteria.setCountryCode(countryCode);
        criteria.setCurrency(currency);
        criteria.setMccCode(mccCode);
        return criteria;
    }

    /** channel-connector 已解析的支持操作、金额上下限与 3DS 模式。 */
    static RouteCandidate candidate(MidCandidateBody body) {
        var candidate = new RouteCandidate();
        candidate.setChannelId(Long.valueOf(body.getChannelId()));
        candidate.setChannelCode(body.getChannelCode());
        candidate.setChannelAccountId(Long.valueOf(body.getChannelAccountId()));
        candidate.setMidId(Long.valueOf(body.getMidId()));
        candidate.setSupportedActions(new LinkedHashSet<>(body.getSupportedOperations()));
        candidate.setMinAmount(decimal(body.getMinAmount()));
        candidate.setMaxAmount(decimal(body.getMaxAmount()));
        candidate.setThreeDsMode(body.getThreeDsMode());
        return candidate;
    }

    /** 精确十进制金额；未声明为 null。 */
    static BigDecimal decimal(String amount) {
        return amount == null ? null : new BigDecimal(amount);
    }
}
