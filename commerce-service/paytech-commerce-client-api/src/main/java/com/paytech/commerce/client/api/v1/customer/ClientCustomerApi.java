package com.paytech.commerce.client.api.v1.customer;

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

import com.paytech.commerce.client.api.v1.common.ClientApiConstraints;
import com.paytech.commerce.client.api.v1.common.ClientApiPaths;
import com.paytech.commerce.client.api.v1.customer.request.CreateCustomerRequestBody;
import com.paytech.commerce.client.api.v1.customer.response.CustomerResponseBody;
import com.paytech.commerce.client.api.v1.customer.response.ListStoredCardsResponseBody;
import com.paytech.foundation.common.http.ApiHeaderNames;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * Client Server 的 Customer 与已存卡契约（Customer 与存卡支付技术方案 §2.2）。
 *
 * <p>
 * Customer 的身份只来自 Client 服务端传入的 consumerReference；Client 只能对其系统中已认证的用户使用，访客不得传入。 删除不可撤销，同一引用之后再次使用时新建 Customer。已存卡由消费者在 Hosted
 * Checkout 支付成功时同意保存后产生，本接口只提供查询与删除，只返回 Token、前 6 后 4 掩码卡号与有效期。
 */
@HttpExchange(value = ClientApiPaths.V1, accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientCustomerApi {

    /**
     * 创建 Customer；同一 Client 下该引用已有有效 Customer 时返回原 Customer。
     *
     * @param clientId
     *            可信接入层提供的 Client 主键
     * @param credentialId
     *            可信接入层提供的 API Credential 主键
     * @param idempotencyKey
     *            预留给上游中间件的接入 Header，Commerce 不做专用 Key 校验
     * @param requestBody
     *            Client 命名空间内的消费者引用
     * @return 新建时 201 并带 Location；已存在时 200 返回原 Customer
     */
    @PostExchange(value = "/customers", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CustomerResponseBody> createCustomer(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @RequestHeader(ApiHeaderNames.IDEMPOTENCY_KEY) String idempotencyKey,
        @RequestBody @NotNull @Valid CreateCustomerRequestBody requestBody);

    /**
     * 按公开编号读取 Customer；已删除的返回删除状态。
     *
     * @param clientId
     *            可信接入层提供的 Client 主键
     * @param credentialId
     *            可信接入层提供的 API Credential 主键
     * @param customerNo
     *            不透明、大小写敏感的 Customer 编号
     * @return Customer；不存在或不属于该 Client 返回 404
     */
    @GetExchange("/customers/{customerNo}")
    CustomerResponseBody getCustomer(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("customerNo") @NotBlank @Size(max = 64) String customerNo);

    /**
     * 按消费者引用读取有效 Customer。
     *
     * @param clientId
     *            可信接入层提供的 Client 主键
     * @param credentialId
     *            可信接入层提供的 API Credential 主键
     * @param consumerReference
     *            Client 命名空间内的消费者引用
     * @return 有效 Customer；没有时返回 404
     */
    @GetExchange("/customers")
    CustomerResponseBody getCustomerByReference(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @RequestParam("consumerReference") @NotBlank @Size(max = 128)
        @Pattern(regexp = ClientApiConstraints.CONSUMER_REFERENCE) String consumerReference);

    /**
     * 删除 Customer，不可撤销；其全部已存卡随之失效。已删除的再次删除返回原删除结果。
     *
     * @param clientId
     *            可信接入层提供的 Client 主键
     * @param credentialId
     *            可信接入层提供的 API Credential 主键
     * @param customerNo
     *            不透明、大小写敏感的 Customer 编号
     * @param idempotencyKey
     *            预留给上游中间件的接入 Header，Commerce 不做专用 Key 校验
     * @return 已删除的 Customer
     */
    @DeleteExchange("/customers/{customerNo}")
    CustomerResponseBody deleteCustomer(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("customerNo") @NotBlank @Size(max = 64) String customerNo,
        @RequestHeader(ApiHeaderNames.IDEMPOTENCY_KEY) String idempotencyKey);

    /**
     * 列出 Customer 有效且未过期的已存卡；已删除的 Customer 返回空列表。
     *
     * @param clientId
     *            可信接入层提供的 Client 主键
     * @param credentialId
     *            可信接入层提供的 API Credential 主键
     * @param customerNo
     *            不透明、大小写敏感的 Customer 编号
     * @return 已存卡列表
     */
    @GetExchange("/customers/{customerNo}/stored-cards")
    ListStoredCardsResponseBody listStoredCards(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("customerNo") @NotBlank @Size(max = 64) String customerNo);

    /**
     * 删除一张已存卡；之后不能再用于支付。已删除的再次删除同样成功。
     *
     * @param clientId
     *            可信接入层提供的 Client 主键
     * @param credentialId
     *            可信接入层提供的 API Credential 主键
     * @param customerNo
     *            不透明、大小写敏感的 Customer 编号
     * @param storedCardToken
     *            已存卡 Token
     * @param idempotencyKey
     *            预留给上游中间件的接入 Header，Commerce 不做专用 Key 校验
     * @return 204；Customer 或已存卡不存在返回 404
     */
    @DeleteExchange("/customers/{customerNo}/stored-cards/{storedCardToken}")
    ResponseEntity<Void> deleteStoredCard(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @PathVariable("customerNo") @NotBlank @Size(max = 64) String customerNo,
        @PathVariable("storedCardToken") @NotBlank @Size(max = 64) String storedCardToken,
        @RequestHeader(ApiHeaderNames.IDEMPOTENCY_KEY) String idempotencyKey);
}
