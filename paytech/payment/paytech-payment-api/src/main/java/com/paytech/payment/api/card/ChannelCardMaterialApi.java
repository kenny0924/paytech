package com.paytech.payment.api.card;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.payment.api.card.request.RevealChannelCardMaterialRequestBody;
import com.paytech.payment.api.card.response.RevealChannelCardMaterialResponseBody;

import jakarta.validation.Valid;

/**
 * 渠道执行读卡私网接口（PayerMax 卡直付对接技术方案第 2 节）：Platform 执行支付类标准 API 时凭支付工具 Token 读取卡数据。
 *
 * <p>
 * 响应含卡原值：服务端日志只记元数据，调用方只在内存中使用，不得记录或持久化。CVV 只能领取一次。Caller Header 仅防误用，部署须限制为私网调用。
 */
@HttpExchange(url = "/payment-card/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface ChannelCardMaterialApi {

    /**
     * 读取渠道执行所需的卡数据
     *
     * @param caller
     *            调用方服务名，只接受 platform-support-service
     * @param body
     *            支付工具 Token、支付尝试 ID 与用途
     * @return 卡数据
     *         <li>httpCode=200 表示读取成功；Token 无效、与支付尝试不符、当前不允许用卡或 CVV 已领取均为 4xx，不返回任何卡数据。</li>
     */
    @PostExchange("/channel-card-materials/reveal")
    RevealChannelCardMaterialResponseBody reveal(@RequestHeader("X-Caller-Service") String caller,
        @Valid @RequestBody RevealChannelCardMaterialRequestBody body);
}
