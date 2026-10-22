package com.paytech.channel.connector.api.serviceexecution;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.serviceexecution.request.ExecuteServiceChannelRequestBody;
import com.paytech.channel.connector.api.serviceexecution.response.ServiceChannelExecutionResponseBody;

import jakarta.validation.Valid;

/**
 * 服务渠道通用执行入口（服务发现名 {@code channel-connector-service}，无 context path；只供内部服务经私网调用，不经 Gateway）
 * <li>只接受服务渠道（SERVICE）：调用方须持有「调用方 × 渠道 × 标准 API」的 ACTIVE 授权；支付渠道一律拒绝，只能经支付入口执行。</li>
 * <li>不处理 Idempotency-Key；调用方不得自动重试，200 + ERROR 与超时都按结果未知处理。</li>
 * <li>下列 Problem 保证没有发出任何外部请求：400（调用方缺失或与 Token 不符、非服务渠道、渠道非 ACTIVE、请求体非法）、
 * 403 CHANNEL_CALL_NOT_GRANTED、404 NOT_FOUND（渠道不存在）/ STANDARD_API_NOT_FOUND / INTEGRATION_IMPLEMENTATION_NOT_FOUND、
 * 422 VALIDATION_ERROR（input 不满足标准 API 请求字段，errors[] 位置以 input. 开头）、500 INTEGRATION_CONFIG_INVALID、
 * 503 INTEGRATION_INITIALIZATION_FAILED / INTEGRATION_BUDGET_EXHAUSTED / CHANNEL_EXECUTION_BUSY。</li>
 */
@HttpExchange("/channel-connector/v1")
public interface ServiceChannelExecutionApi {

    /**
     * 执行服务渠道的一个标准 API
     *
     * @param callerService 调用方服务名（{@value ChannelConnectorHeaders#CALLER_HEADER}）；携带已验证内部 Token 时可省略，提供时须与 Token 一致
     * @param requestBody 渠道编码、标准 API 编码与标准 API 输入
     * @return 200 表示流程已开始执行第一个原子节点，可能已经发出外部请求；executeStatus=ERROR 表示结果无法确认
     */
    @PostExchange("/service-executions")
    ServiceChannelExecutionResponseBody execute(
        @RequestHeader(value = ChannelConnectorHeaders.CALLER_HEADER, required = false) String callerService,
        @Valid @RequestBody ExecuteServiceChannelRequestBody requestBody);
}
