package com.paytech.card.api.material;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.material.request.RevealChannelCardMaterialRequestBody;
import com.paytech.card.api.material.response.RevealChannelCardMaterialResponseBody;

import jakarta.validation.Valid;

/**
 * 渠道执行读卡私网接口（design/05 §2.1，自 paytech-payment-api 迁入）：channel-connector 执行支付类标准 API 时凭 Card 签发给 Payment 的一次性票据读取卡数据。
 *
 * <p>
 * 响应含卡原值：服务端日志只记元数据，调用方只在内存中使用，不得记录或持久化。票据只能使用一次：Card 在本地事务内标记已用，再经 Vault 取回 DEK 解密、
 * 从 Redis 一次性领取 CVV。票据已用、已过期、用途不符或调用方不符均为 4xx，不返回任何卡数据；票据已用后 CVV 不可再取。
 */
@HttpExchange(url = "/card/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface ChannelCardMaterialApi {

    /**
     * 凭票据读取渠道执行所需的卡数据
     *
     * @param caller
     *            调用方服务名，接受 channel-connector-service 与 platform-support-service（统一对外出口第一段过渡，第二段收回 platform-support-service）
     * @param body
     *            一次性票据
     * @return 卡数据
     *         <li>httpCode=200 表示读取成功；票据不存在为 404；已用、已过期、用途不符或 CVV 已领取为 400；调用方不符为 404（不暴露资源是否存在）。</li>
     */
    @PostExchange("/channel-card-materials/reveal")
    RevealChannelCardMaterialResponseBody reveal(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @Valid @RequestBody RevealChannelCardMaterialRequestBody body);
}
