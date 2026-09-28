package com.paytech.commerce.client.api.v1.order;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.commerce.client.api.v1.common.ClientApiConstraints;
import com.paytech.commerce.client.api.v1.common.ClientApiPaths;
import com.paytech.commerce.client.api.v1.order.response.CancelOrderResponseBody;
import com.paytech.commerce.client.api.v1.order.response.GetOrderResponseBody;
import com.paytech.foundation.common.http.ApiHeaderNames;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Client Server 的订单查询和取消契约。 */
@HttpExchange(value = ClientApiPaths.V1, accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientOrderApi {

    /**
     * 按公开订单号读取当前本地视图，不同步调用 Payment 或 Finance。
     *
     * @param clientId
     *            可信接入层提供的 Client 主键
     * @param credentialId
     *            可信接入层提供的 API Credential 主键
     * @param orderNo
     *            不透明、大小写敏感的 Commerce 订单号
     * @return 当前本地订单、Checkout 链接与准备状态、支付、退款和资金可用事实
     */
    @GetExchange("/orders/{orderNo}")
    GetOrderResponseBody getOrder(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo);

    /**
     * 按 Client 内永久唯一的非空业务订单号读取同一订单视图。
     * <li>Query 白名单、单值及无 Body 协议限制由上游接入层处理。</li>
     *
     * @param clientId
     *            可信接入层提供的 Client 主键
     * @param credentialId
     *            可信接入层提供的 API Credential 主键
     * @param clientOrderId
     *            当前 Client 命名空间内大小写敏感的业务订单号
     * @return 当前本地订单、Checkout 链接与准备状态、支付、退款和资金可用事实
     */
    @GetExchange("/orders")
    GetOrderResponseBody getOrderByClientOrderId(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @RequestParam("clientOrderId")
        @NotBlank @Size(max = 128) @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
        String clientOrderId);

    /**
     * 请求取消未支付订单，无 Body 或 Query。已取消订单返回原取消时间，不重复执行。
     * 未取得 Payment 确定终止结果时返回 {@code 202 Accepted}，可遵循 Retry-After 再次请求取消。
     * 业务按订单事实判断，不读取或冻结请求幂等记录；订单 GET 只读取本地状态。
     * Direct Pay 允许没有 Checkout Session，此时成功响应不返回 checkoutStatus。
     *
     * @param clientId
     *            可信接入层提供的 Client 主键
     * @param credentialId
     *            可信接入层提供的 API Credential 主键
     * @param orderNo
     *            不透明、大小写敏感的 Commerce 订单号
     * @param idempotencyKey
     *            预留给上游中间件的接入 Header，Commerce 不做专用 Key 校验，不传入取消用例
     * @return 200 取消终态或 202 待定结果；已支付返回 409 REVERSAL_REQUIRED
     */
    @PostExchange("/orders/{orderNo}/cancel")
    ResponseEntity<CancelOrderResponseBody> cancelOrder(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo,
        @RequestHeader(ApiHeaderNames.IDEMPOTENCY_KEY) String idempotencyKey);
}
