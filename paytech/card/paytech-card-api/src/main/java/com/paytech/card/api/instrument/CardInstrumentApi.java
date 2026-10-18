package com.paytech.card.api.instrument;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.instrument.request.CreateCardInstrumentRequestBody;
import com.paytech.card.api.instrument.response.CreateCardInstrumentResponseBody;

import jakarta.validation.Valid;

/**
 * 卡交易工具私网接口（design/05 §2.2）：Payment 申请卡支付工具时先由 Card 在自己的库内保存卡、凭证与工具，再在本地事务内保存支付工具 Token。
 *
 * <p>
 * 请求含卡原值：服务端与调用方日志只记元数据，CVV 只以本卡 DEK 加密暂存 Redis，不落库、不哈希。同一 Client 的同一来源提交重放返回原工具，
 * 不再访问 Vault、不覆盖 CVV；换卡、换 Customer 或改变保存意愿返回 409。
 */
@HttpExchange(url = "/card/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CardInstrumentApi {

    /**
     * 创建卡交易工具
     *
     * @param caller
     *            调用方服务名，只接受 payment-service
     * @param body
     *            Client、来源提交、手输卡或已存卡、Customer、保存意愿与期限上限
     * @return 工具引用、掩码卡号、实际期限与风控属性
     *         <li>httpCode=200 表示已创建或重放原工具；卡号校验失败、卡已过期、已存卡不可用为 400；来源提交已用于另一张卡为 409；Vault 或 Redis 不可用为 503。</li>
     */
    @PostExchange("/instruments")
    CreateCardInstrumentResponseBody createInstrument(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @Valid @RequestBody CreateCardInstrumentRequestBody body);
}
