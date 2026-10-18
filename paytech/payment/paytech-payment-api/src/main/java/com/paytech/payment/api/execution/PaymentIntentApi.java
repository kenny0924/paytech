package com.paytech.payment.api.execution;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.payment.api.execution.request.CreateIntentRequestBody;
import com.paytech.payment.api.execution.request.PayRequestBody;
import com.paytech.payment.api.execution.request.TerminateIntentRequestBody;
import com.paytech.payment.api.execution.response.CreateIntentResponseBody;
import com.paytech.payment.api.execution.response.GetIntentResponseBody;
import com.paytech.payment.api.execution.response.GetSubmissionResponseBody;
import com.paytech.payment.api.execution.response.PayResponseBody;
import com.paytech.payment.api.execution.response.TerminateIntentResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Payment 私有支付受理合同，由 Commerce 等已授权内部系统调用。 */
@HttpExchange("/payment-execution/v1")
public interface PaymentIntentApi {

    /**
     * 创建支付意图
     * 
     * @param body
     *            请求
     * @return 响应
     *         <li>httpCode=200表示本次请求成功</li>
     */
    @PostExchange("/intents")
    ResponseEntity<CreateIntentResponseBody> createIntent(@Valid @RequestBody CreateIntentRequestBody body);

    /**
     * 查询支付意图
     *
     * @param intentId
     *            支付意图 ID，正十进制 long 字符串，无前导零
     * @param clientId
     *            Client ID，格式同上；必须与 Intent 归属一致
     * @return 来源业务、当前状态及活动和成功尝试 ID
     *         <li>查询成功返回 200；不存在或不属于当前 Client 返回 404。</li>
     */
    @GetExchange("/intents/{intentId}")
    ResponseEntity<GetIntentResponseBody> getIntent(
        @PathVariable("intentId") @NotBlank(message = "支付意图 ID 必填") @Pattern(regexp = "[1-9][0-9]{0,18}",
            message = "支付意图 ID 必须为无前导零的正整数字符串") @DecimalMax(value = "9223372036854775807",
                message = "支付意图 ID 超出有效范围") String intentId,
        @RequestParam("clientId") @NotBlank(message = "Client ID 必填") @Pattern(regexp = "[1-9][0-9]{0,18}",
            message = "Client ID 必须为无前导零的正整数字符串") @DecimalMax(value = "9223372036854775807",
                message = "Client ID 超出有效范围") String clientId);

    /**
     * 按来源提交查询原支付结果（EX-I06）
     *
     * @param intentId
     *            支付意图 ID，正十进制 long 字符串，无前导零
     * @param sourceSubmissionId
     *            来源提交 ID，格式同上
     * @param clientId
     *            Client ID，格式同上；必须与 Intent 归属一致
     * @return 原支付尝试的状态、已捕获金额、可选交互与失败代码
     *         <li>查询成功返回 200；Intent 不存在或不属于当前 Client、该提交没有支付尝试（含只申请了工具）或不属于该 Intent 返回 404。</li>
     *         <li>不返回 3DS 跳转地址。</li>
     */
    @GetExchange("/intents/{intentId}/submissions/{sourceSubmissionId}")
    ResponseEntity<GetSubmissionResponseBody> getSubmission(
        @PathVariable("intentId") @NotBlank(message = "支付意图 ID 必填") @Pattern(regexp = "[1-9][0-9]{0,18}",
            message = "支付意图 ID 必须为无前导零的正整数字符串") @DecimalMax(value = "9223372036854775807",
                message = "支付意图 ID 超出有效范围") String intentId,
        @PathVariable("sourceSubmissionId") @NotBlank(message = "来源提交 ID 必填") @Pattern(regexp = "[1-9][0-9]{0,18}",
            message = "来源提交 ID 必须为无前导零的正整数字符串") @DecimalMax(value = "9223372036854775807",
                message = "来源提交 ID 超出有效范围") String sourceSubmissionId,
        @RequestParam("clientId") @NotBlank(message = "Client ID 必填") @Pattern(regexp = "[1-9][0-9]{0,18}",
            message = "Client ID 必须为无前导零的正整数字符串") @DecimalMax(value = "9223372036854775807",
                message = "Client ID 超出有效范围") String clientId);

    /**
     * 终止支付意图
     *
     * @param intentId
     *            支付意图 ID
     * @param body
     *            请求
     * @return 当前状态与终止决定
     *         <li>正常业务结果统一返回 200，上游通过 decision 判断是否 PENDING。</li>
     *         <li>重复请求按当前事实返回；不自动撤销授权或退款。</li>
     */
    @PostExchange("/intents/{intentId}/terminate")
    ResponseEntity<TerminateIntentResponseBody> terminateIntent(@PathVariable("intentId") String intentId,
        @Valid @RequestBody TerminateIntentRequestBody body);

    /**
     * 受理支付
     *
     * @param intentId
     *            支付意图 ID
     * @param idempotencyKey
     *            请求幂等键
     *            <li>固定为 {@code submission:<sourceSubmissionId>}，重试沿用原值。</li>
     * @param body
     *            支付请求
     * @return 支付尝试 ID 和当前状态
     *         <li>同一提交且内容一致时返回原尝试，不重复执行支付。</li>
     *         <li>PROCESSING 返回 202；SUCCEEDED、FAILED、CANCELLED 返回 200。</li>
     *         <li>支付结果以 status 为准，HTTP 200 不代表支付成功。</li>
     */
    @PostExchange("/intents/{intentId}/pay")
    ResponseEntity<PayResponseBody> pay(@PathVariable("intentId") String intentId,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody PayRequestBody body);
}
