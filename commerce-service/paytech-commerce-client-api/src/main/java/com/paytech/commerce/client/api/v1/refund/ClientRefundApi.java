package com.paytech.commerce.client.api.v1.refund;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.commerce.client.api.v1.common.ClientApiConstraints;
import com.paytech.commerce.client.api.v1.common.ClientApiPaths;
import com.paytech.commerce.client.api.v1.refund.request.CreateRefundRequestBody;
import com.paytech.commerce.client.api.v1.refund.response.CreateRefundResponseBody;
import com.paytech.commerce.client.api.v1.refund.response.GetRefundResponseBody;
import com.paytech.commerce.client.api.v1.refund.response.ListOrderRefundsResponseBody;
import com.paytech.foundation.common.http.ApiHeaderNames;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Client Server 创建与查询 Commerce 退款的 Owner 契约。 */
@HttpExchange(value = ClientApiPaths.V1, accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientRefundApi extends ClientRefundableAmountApi {

    /**
     * 创建全额或部分退款；币种继承订单。
     * <li>相同 clientRefundId 和原内容返回原 201 回执，不重复预占或提交。</li>
     * <li>同业务退款号修改订单、金额或原因时返回冲突。</li>
     *
     * @param clientId
     *            可信 Gateway 认证的 Client 标识
     * @param credentialId
     *            可信 Gateway 认证的 Client API Credential 标识
     * @param orderNo
     *            不透明、大小写敏感的 Commerce 订单号
     * @param idempotencyKey
     *            预留给上游中间件的请求 Header，Commerce 不做专用 Key 校验，不参与业务防重
     * @param requestBody
     *            Client 提交的退款内容，不允许指定币种或 OE
     * @return 本地持久化受理后的不可变 201 回执及资源 Location，不表示渠道退款成功
     */
    @PostExchange(value = "/orders/{orderNo}/refunds", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CreateRefundResponseBody> createRefund(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo,
        @RequestHeader(ApiHeaderNames.IDEMPOTENCY_KEY) String idempotencyKey,
        @RequestBody @NotNull @Valid CreateRefundRequestBody requestBody);

    /**
     * 按创建时间、退款号倒序查询同一订单的退款，游标只用于当前 Client 和 Order。
     * <li>无退款时返回空列表；查询不要求 Checkout Session，不推进退款。</li>
     * <li>续页读取锚点之后的当前退款状态，不冻结跨页状态快照。</li>
     *
     * @param clientId
     *            可信 Gateway 认证的 Client 标识
     * @param credentialId
     *            可信 Gateway 认证的 Client API Credential 标识
     * @param orderNo
     *            不透明、大小写敏感的 Commerce 订单号
     * @param limit
     *            每页数量，默认 20，允许 1 至 100
     * @param cursor
     *            上一页返回的大小写敏感、URL-safe、不透明版本化游标；首屏省略
     * @return 当前页和下一页游标；没有后续数据时 nextCursor 为 null
     */
    @GetExchange("/orders/{orderNo}/refunds")
    ListOrderRefundsResponseBody listOrderRefunds(@RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo,
        @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit,
        @RequestParam(name = "cursor", required = false) @Size(max = 512) String cursor);

    /**
     * 按公开退款号读取当前退款状态，不查询外部 Payment。
     * <li>只读取退款事实及关联订单号，不要求 Checkout Session，不推进退款。</li>
     *
     * @param clientId
     *            可信 Gateway 认证的 Client 标识
     * @param credentialId
     *            可信 Gateway 认证的 Client API Credential 标识
     * @param refundRequestNo
     *            不透明、大小写敏感的 Commerce 退款号
     * @return 属于该 Client 的当前退款表示；未知或跨 Client 均返回不存在
     */
    @GetExchange("/refunds/{refundRequestNo}")
    GetRefundResponseBody getRefund(@RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("refundRequestNo") @NotBlank @Size(max = 64) String refundRequestNo);

    /**
     * 按 Client 命名空间内永久唯一的业务退款号查询；Operations 来源退款没有该标识。
     * <li>只读取退款事实及关联订单号，不要求 Checkout Session，不调用 Payment 或推进退款。</li>
     *
     * @param clientId
     *            可信 Gateway 认证的 Client 标识
     * @param credentialId
     *            可信 Gateway 认证的 Client API Credential 标识
     * @param clientRefundId
     *            Client 自行分配、大小写敏感的业务退款号
     * @return 与按公开退款号查询相同的当前表示；未知或跨 Client 均返回不存在
     */
    @GetExchange("/refunds")
    GetRefundResponseBody getRefundByClientRefundId(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @RequestParam("clientRefundId") @NotBlank @Size(max = 128) @Pattern(
            regexp = ClientApiConstraints.CLIENT_REFERENCE) String clientRefundId);
}
