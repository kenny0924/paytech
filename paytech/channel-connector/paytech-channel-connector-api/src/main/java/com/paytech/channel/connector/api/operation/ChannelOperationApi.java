package com.paytech.channel.connector.api.operation;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;
import com.paytech.channel.connector.api.operation.response.ChannelOperationResponseBody;

import jakarta.validation.Valid;

/**
 * 渠道操作执行（服务发现名 {@code channel-connector-service}，无 context path）。
 * <li>只供 Payment 调用、只接受支付渠道：校验冻结坐标后在本进程、事务外执行标准 API（支付渠道执行舱），不自动重试；账号凭据与卡数据由本服务读取。</li>
 * <li>冻结渠道为服务渠道时不发出请求，200 + NOT_SENT（failureCode INTEGRATION_CONFIG_INVALID，与配置无效同口径）。</li>
 * <li>冻结坐标不可用、跨 OE、账号未配置或执行引擎保证未发出请求时 200 + NOT_SENT；超时、网络与未知错误 200 + ERROR。</li>
 * <li>请求校验失败返回 422、用途与操作不匹配返回 400：均未发出渠道请求，但调用方无法区分时应按结果未知处理。</li>
 */
@HttpExchange("/channel-connector/v1")
public interface ChannelOperationApi {
    /**
     * 执行一次渠道操作
     *
     * @param requestBody 冻结坐标、操作与标准 API 输入
     * @return 已映射的执行结论
     */
    @PostExchange("/operations")
    ChannelOperationResponseBody execute(@Valid @RequestBody ExecuteChannelOperationRequestBody requestBody);
}
