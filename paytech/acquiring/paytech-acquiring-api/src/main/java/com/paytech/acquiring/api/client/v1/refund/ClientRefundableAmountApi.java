package com.paytech.acquiring.api.client.v1.refund;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.acquiring.api.client.v1.common.ClientApiPaths;
import com.paytech.acquiring.api.client.v1.refund.response.GetRefundableAmountResponseBody;
import com.paytech.foundation.common.http.ApiHeaderNames;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Client 订单可退款金额查询契约。 */
@HttpExchange(value = ClientApiPaths.V1, accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientRefundableAmountApi {
    /**
     * 查询已扣除退款预占的本地金额快照；实际退款必须在写入时重新校验。
     * <li>未知订单与其他 Client 的订单统一返回 ORDER_NOT_FOUND。</li>
     * <li>只读取订单与退款位置，不依赖 Checkout Session 或调用 Payment。</li>
     *
     * @param clientId
     *            可信身份中的 Client 主键
     * @param credentialId
     *            可信 API Credential 主键
     * @param orderNo
     *            Client 所属的大小写敏感订单号
     * @return 金额、币种和本地事实时点
     */
    @GetExchange("/orders/{orderNo}/refundable-amount")
    GetRefundableAmountResponseBody getRefundableAmount(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo);
}
