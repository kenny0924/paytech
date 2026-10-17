package com.paytech.channel.connector.api.operation;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;
import com.paytech.channel.connector.api.operation.response.ChannelOperationResponseBody;

import jakarta.validation.Valid;

/**
 * 渠道操作执行（服务发现名 {@code channel-connector-service}，无 context path）。
 * <li>校验冻结坐标后在事务外经 Platform 标准 API 执行，不自动重试；Platform 读取账号与卡数据，channel-connector 不 Reveal。</li>
 * <li>冻结坐标不可用、跨 OE、账号未配置或 Platform 保证未发出请求时 200 + NOT_SENT；超时、网络与未知错误 200 + ERROR。</li>
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
