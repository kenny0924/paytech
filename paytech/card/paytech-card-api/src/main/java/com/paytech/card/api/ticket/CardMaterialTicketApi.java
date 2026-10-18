package com.paytech.card.api.ticket;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.ticket.request.IssueCardMaterialTicketRequestBody;
import com.paytech.card.api.ticket.response.IssueCardMaterialTicketResponseBody;

import jakarta.validation.Valid;

/**
 * 一次性读卡票据私网接口（design/05 §2.1）：Payment 在发起渠道资金动作前（事务外、Intent 锁内、已完成自身准入校验）为本次动作申请票据，
 * 再把票据作为卡字段句柄交给 Platform 标准 API；Platform 凭票据向 Card 读卡一次。
 *
 * <p>
 * Card 只校验工具存在、未过期且 CVV 仍可领取；签发随机 256 位票据，只保存其 SHA-256。票据原值只出现在本响应中，调用方不得记录日志或持久化。
 */
@HttpExchange(url = "/card/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CardMaterialTicketApi {

    /**
     * 签发读卡票据
     *
     * @param caller
     *            调用方服务名，只接受 payment-service
     * @param body
     *            工具引用、支付尝试与动作、用途与有效秒数
     * @return 票据与截止时间
     *         <li>httpCode=200 表示已签发；工具不存在为 404；工具已过期或 CVV 已领取（不可再用卡）为 400；调用方不符为 404（不暴露资源是否存在）。</li>
     */
    @PostExchange("/material-tickets")
    IssueCardMaterialTicketResponseBody issueTicket(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @Valid @RequestBody IssueCardMaterialTicketRequestBody body);
}
