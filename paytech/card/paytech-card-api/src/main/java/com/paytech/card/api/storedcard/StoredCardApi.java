package com.paytech.card.api.storedcard;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.storedcard.request.InvalidateStoredCardsRequestBody;
import com.paytech.card.api.storedcard.response.InvalidateStoredCardsResponseBody;
import com.paytech.card.api.storedcard.response.StoredCardListResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Pattern;

/**
 * 已存卡私网接口（Customer 与存卡支付技术方案 §2.4；第 12 步自 paytech-payment-api 迁入，design/05 §3）：acquiring 与 consumer 在核对
 * Client 与 Customer 后调用。
 *
 * <p>
 * 只返回对外 Token、前 6 后 4 掩码卡号与有效期，不返回卡号、内部 ID、指纹或完整 BIN。已存卡由 Card 消费 PaymentSucceeded 时按消费者同意生成，
 * 这里不提供新建。列出接受 acquiring-service 与 consumer-service；删除与失效只接受 consumer-service；其他调用方为 404。
 */
@HttpExchange(url = "/card/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface StoredCardApi {

    /**
     * 列出某 Customer 有效且未过期的已存卡
     *
     * @param caller
     *            调用方服务名，只接受 acquiring-service 或 consumer-service
     * @param clientId
     *            Client ID
     * @param customerId
     *            Commerce Customer ID
     * @return 已存卡列表
     *         <li>httpCode=200；没有已存卡时返回空列表。</li>
     */
    @GetExchange("/stored-cards")
    StoredCardListResponseBody listStoredCards(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @RequestParam("clientId") @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 无效") @DecimalMax(value = "9223372036854775807", message = "Client ID 超出有效范围") String clientId,
        @RequestParam("customerId") @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Customer ID 无效") @DecimalMax(value = "9223372036854775807", message = "Customer ID 超出有效范围") String customerId);

    /**
     * 删除一张已存卡
     *
     * @param caller
     *            调用方服务名，只接受 consumer-service
     * @param storedCardToken
     *            对外已存卡 Token
     * @param clientId
     *            Client ID
     * @param customerId
     *            Commerce Customer ID
     * @return 无内容
     *         <li>httpCode=204 表示已删除（已删除的再次删除同样返回 204）；不存在或不属于该 Customer 返回 404。</li>
     */
    @DeleteExchange("/stored-cards/{storedCardToken}")
    ResponseEntity<Void> deleteStoredCard(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @PathVariable("storedCardToken") @Pattern(regexp = "sc_[A-Za-z0-9_-]{28}", message = "已存卡 Token 无效") String storedCardToken,
        @RequestParam("clientId") @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 无效") @DecimalMax(value = "9223372036854775807", message = "Client ID 超出有效范围") String clientId,
        @RequestParam("customerId") @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Customer ID 无效") @DecimalMax(value = "9223372036854775807", message = "Customer ID 超出有效范围") String customerId);

    /**
     * 让某 Customer 的全部有效已存卡失效（consumer 删除 Customer 后的补偿任务调用）
     *
     * @param caller
     *            调用方服务名，只接受 consumer-service
     * @param customerId
     *            Commerce Customer ID
     * @param body
     *            Client ID
     * @return 本次失效条数
     *         <li>httpCode=200；可重复调用，重复调用返回 0。</li>
     */
    @PostExchange("/customers/{customerId}/stored-cards/invalidation")
    InvalidateStoredCardsResponseBody invalidateStoredCards(@RequestHeader(CardApiConstants.CALLER_HEADER) String caller,
        @PathVariable("customerId") @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Customer ID 无效") @DecimalMax(value = "9223372036854775807", message = "Customer ID 超出有效范围") String customerId,
        @Valid @RequestBody InvalidateStoredCardsRequestBody body);
}
