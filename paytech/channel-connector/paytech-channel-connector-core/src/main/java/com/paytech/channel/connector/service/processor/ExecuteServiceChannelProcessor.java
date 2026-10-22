package com.paytech.channel.connector.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ServiceExecutionErrorCode;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.channel.connector.protocol.domain.execution.StandardApiNotSentException;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelCallGrantService;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.service.processor.request.ExecuteServiceChannelRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 服务渠道通用执行入口（technique 第 6.2 节）：调用方身份已由入站适配确认，本类依次校验渠道、调用授权与账号，再在事务外执行标准 API
 * <li>渠道不存在 404；非服务渠道（支付渠道只经支付入口）400；渠道非 ACTIVE 400；无 ACTIVE 授权 403 CHANNEL_CALL_NOT_GRANTED。</li>
 * <li>取该渠道唯一的 ACTIVE 账号注入 input.channelAccount（凭据由 protocol 初始化函数在本服务内 Reveal）；没有账号时不提供账号材料。
 * 调用方自带的 channelAccount 一律移除，避免借用其他渠道账号的 Vault 引用。</li>
 * <li>以上任一校验失败都不发出外部请求。执行在服务渠道执行舱中进行；未发出类失败按原 NOT_SENT 集合映射为 Problem，
 * 第一个节点开始后的任何失败由流程引擎记为 ERROR 结果（200）。</li>
 * <li>本入口不写库，只有渠道、授权与账号的普通一致性读；不在事务内调用外部服务，不自动重试。</li>
 */
@Component
@AllArgsConstructor
public class ExecuteServiceChannelProcessor extends AbstractProcessor<ExecuteServiceChannelRequest, ExecutionResult> {
    /** 账号材料在标准 API 输入中的属性名，与支付入口一致。 */
    static final String CHANNEL_ACCOUNT = "channelAccount";

    private final ChannelService channelService;
    private final ChannelCallGrantService grantService;
    private final ChannelAccountRepository channelAccountRepository;
    private final ExecuteStandardApiProcessor executeStandardApiProcessor;

    /** 校验渠道、授权与账号后在事务外执行。 */
    @Override
    protected ExecutionResult processRequest(ExecuteServiceChannelRequest request) {
        // 渠道：存在、服务渠道、ACTIVE
        ChannelIdentity channel = loadExecutableChannel(request.getChannelCode());

        // 调用授权
        if (!grantService.hasActiveGrant(request.getCallerService(), channel.getChannelId(),
            request.getStandardApiCode())) {
            throw new FailException(ServiceExecutionErrorCode.CHANNEL_CALL_NOT_GRANTED, "调用方未获授权: "
                + request.getCallerService() + ", 渠道: " + channel.getChannelCode() + ", 标准 API: "
                + request.getStandardApiCode());
        }

        // 账号材料与执行
        JsonNode input = inputWithAccount(request.getInput(), channel, activeAccount(channel));
        return execute(request, channel, input);
    }

    /** 渠道须存在（否则 404）、为服务渠道且 ACTIVE（否则 400）。 */
    ChannelIdentity loadExecutableChannel(String channelCode) {
        ChannelIdentity channel = channelService.findChannelByCode(channelCode);
        if (channel == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "渠道不存在: " + channelCode);
        }
        if (!channel.service()) {
            throw new BizCheckFailException("支付渠道只能经支付入口执行: " + channelCode);
        }
        if (channel.getChannelStatus() != ChannelStatus.ACTIVE) {
            throw new BizCheckFailException("渠道未激活: " + channelCode + ", 状态: " + channel.getChannelStatus().getCode());
        }
        return channel;
    }

    /** 服务渠道唯一的 ACTIVE 账号；没有返回 null，多于一个属于数据损坏。 */
    ChannelAccount activeAccount(ChannelIdentity channel) {
        List<ChannelAccount> accounts = channelAccountRepository.findActiveByChannel(channel.getChannelId());
        if (accounts.size() > 1) {
            throw new ErrorException("服务渠道存在多个 ACTIVE 凭据: " + channel.getChannelCode());
        }
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    /**
     * 复制调用方输入，移除其自带的账号材料；有已配置的 ACTIVE 账号时注入账号身份与 Vault 引用（不含明文）
     * <li>服务渠道账号不绑定 OE：不注入 entityNo 键（标准 API 请求字段不声明它，账号配置函数要求身份不带 entityNo）。</li>
     */
    static JsonNode inputWithAccount(JsonNode input, ChannelIdentity channel, ChannelAccount account) {
        if (!input.isObject()) {
            return input;
        }
        ObjectNode copy = ((ObjectNode)input).deepCopy();
        copy.remove(CHANNEL_ACCOUNT);
        if (account == null || account.getAccountVaultToken() == null) {
            return copy;
        }
        ObjectNode material = JsonNodeFactory.instance.objectNode();
        material.put("channelCode", channel.getChannelCode());
        material.put("channelAccountId", account.getChannelAccountId().toString());
        material.put("accountVaultToken", account.getAccountVaultToken());
        copy.set(CHANNEL_ACCOUNT, material);
        return copy;
    }

    /**
     * 事务外执行标准 API；未发出类失败映射为通用入口的 Problem
     * <li>输入不满足请求字段：422 VALIDATION_ERROR 及字段位置；标准 API 或实现不存在：404；配置无效：500；
     * 初始化失败、预算耗尽、执行舱繁忙：503。代码沿用原 Platform 合同，CHANNEL_EXECUTION_BUSY 为本期新增。</li>
     */
    ExecutionResult execute(ExecuteServiceChannelRequest request, ChannelIdentity channel, JsonNode input) {
        try {
            return executeStandardApiProcessor.process(new ExecuteStandardApiRequest(request.getStandardApiCode(),
                channel.getChannelCode(), input, request.getCallerService()));
        } catch (StandardApiNotSentException notSent) {
            throw problem(notSent);
        }
    }

    /** 未发出原因到 Problem 异常的映射，保留原因为 Cause。 */
    static RuntimeException problem(StandardApiNotSentException notSent) {
        return switch (notSent.getReason()) {
            case VALIDATION_ERROR -> new RequestBodyValidationException(notSent.getFields().stream()
                .map(field -> new ProblemFieldError(field, notSent.getMessage())).toList(), notSent);
            case STANDARD_API_NOT_FOUND -> new FailException(ServiceExecutionErrorCode.STANDARD_API_NOT_FOUND,
                notSent.getMessage(), notSent);
            case INTEGRATION_IMPLEMENTATION_NOT_FOUND -> new FailException(
                ServiceExecutionErrorCode.INTEGRATION_IMPLEMENTATION_NOT_FOUND, notSent.getMessage(), notSent);
            case INTEGRATION_CONFIG_INVALID -> new ErrorException(ServiceExecutionErrorCode.INTEGRATION_CONFIG_INVALID,
                notSent.getMessage(), notSent);
            case INTEGRATION_INITIALIZATION_FAILED -> new ErrorException(
                ServiceExecutionErrorCode.INTEGRATION_INITIALIZATION_FAILED, notSent.getMessage(), notSent);
            case INTEGRATION_BUDGET_EXHAUSTED -> new ErrorException(
                ServiceExecutionErrorCode.INTEGRATION_BUDGET_EXHAUSTED, notSent.getMessage(), notSent);
            case CHANNEL_EXECUTION_BUSY -> new ErrorException(ServiceExecutionErrorCode.CHANNEL_EXECUTION_BUSY,
                notSent.getMessage(), notSent);
        };
    }
}
