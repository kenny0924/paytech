package com.paytech.payout.api.beneficiary;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.payout.api.PayoutHeaders;
import com.paytech.payout.api.beneficiary.request.BeneficiaryAccountCommandRequestBody;
import com.paytech.payout.api.beneficiary.request.CheckerDecisionRequestBody;
import com.paytech.payout.api.beneficiary.request.MakerReviewRequestBody;
import com.paytech.payout.api.beneficiary.request.RevealBeneficiaryAccountRequestBody;
import com.paytech.payout.api.beneficiary.request.SubmitBeneficiaryAccountRequestBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountListResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.beneficiary.response.RevealBeneficiaryAccountResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Owner 收款账户的提交、核验、默认、停用 / 恢复、关闭、原值查看与查询（technique 8.1 P1—P10）
 * <li>状态：提交即 PENDING_REVIEW → Maker 意见 → Checker 决定 VERIFIED（进入冷却）/ REJECTED；VERIFIED → SUSPENDED → 恢复为
 * PENDING_REVIEW 重新核验；PENDING_REVIEW / VERIFIED / SUSPENDED → CLOSED。REJECTED、CLOSED 为终态；冻结字段变化必须新建账户。</li>
 * <li>原值只进 Vault：库内、日志、事件与补偿载荷都没有户名或账号原值；提交与 Reveal 所在前缀为 MVC 日志 metadata-only。</li>
 * <li>同一 Owner 的全部写命令在 Redis 锁 PAYOUT_BENEFICIARY_OWNER:{ownerType}:{ownerId} 内串行；Vault、account、Platform 调用都在本地事务之外。</li>
 * <li>调用方：Client BFF 与 Operations BFF；停用、恢复、Maker、Checker、Reveal 只由 Operations BFF 调用。</li>
 */
@HttpExchange(url = "/payout/v1/beneficiary-accounts", accept = "application/json")
public interface BeneficiaryAccountApi {

    /**
     * 提交新收款账户（P1）
     *
     * @param body Owner、收款方式、国家、币种、字段原值与提交人
     * @return 201 与新账户（PENDING_REVIEW）；同 Owner 以同一内容重复提交时 200 与原账户
     *         <li>同 Owner 同账号已有未终结账户且内容不同：409 BENEFICIARY_DUPLICATE；已有账户 ID 与状态只在 Problem detail 中，
     *         格式为"同一 Owner 已有相同账号的收款账户: {已有账户 ID}, 状态 {状态}"，不另设扩展字段；调用方需要完整账户时按 ID 查询详情（P3）。</li>
     *         <li>收款方式 × 国家 × 币种无字段清单：422 BENEFICIARY_UNSUPPORTED_ROUTE；请求体字段约束不符：422 VALIDATION_ERROR；
     *         按字段清单校验的缺失、格式或不适用字段：400 INVALID_REQUEST。</li>
     *         <li>Vault 不可用：503，不落库。</li>
     */
    @PostExchange
    ResponseEntity<BeneficiaryAccountResponseBody> submitBeneficiaryAccount(
        @Valid @RequestBody SubmitBeneficiaryAccountRequestBody body);

    /**
     * 查询 Owner 的收款账户列表（P2）
     *
     * @param callerService 调用方服务名；为 Operations BFF 时返回 sharedOwners 与 reviewMakerNote
     * @param ownerType Owner 类型：CLIENT、LOCAL_PARTNER
     * @param ownerId Owner 标识
     * @param status 可选状态过滤
     * @param currency 可选币种过滤
     * @return 账户列表（不含事件），按币种、创建时间升序
     */
    @GetExchange
    BeneficiaryAccountListResponseBody listBeneficiaryAccounts(
        @RequestHeader(value = PayoutHeaders.CALLER_SERVICE, required = false) String callerService,
        @RequestParam("ownerType") @NotBlank @Pattern(regexp = "CLIENT|LOCAL_PARTNER") String ownerType,
        @RequestParam("ownerId") @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String ownerId,
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "PENDING_REVIEW|VERIFIED|REJECTED|SUSPENDED|CLOSED") String status,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency);

    /**
     * 查询收款账户详情（P3，含事件时间线，不含原值）
     *
     * @param callerService 调用方服务名；为 Operations BFF 时返回 sharedOwners 与 reviewMakerNote
     * @param beneficiaryAccountId 收款账户 ID
     * @param ownerType 可选；Client BFF 必须传当前 Owner，与账户不符时 404
     * @param ownerId 可选；与 ownerType 同时给出
     * @return 账户与事件；不存在或不属于该 Owner 时 404 BENEFICIARY_ACCOUNT_NOT_FOUND
     */
    @GetExchange("/{beneficiaryAccountId}")
    BeneficiaryAccountResponseBody getBeneficiaryAccount(
        @RequestHeader(value = PayoutHeaders.CALLER_SERVICE, required = false) String callerService,
        @PathVariable("beneficiaryAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String beneficiaryAccountId,
        @RequestParam(value = "ownerType", required = false) @Pattern(regexp = "CLIENT|LOCAL_PARTNER") String ownerType,
        @RequestParam(value = "ownerId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String ownerId);

    /**
     * 设为同 Owner、同币种的默认账户（P4）
     *
     * @param beneficiaryAccountId 收款账户 ID
     * @param body 归属、期望版本与操作人
     * @return 设为默认后的账户；原默认账户同时清除；已是默认时原样返回
     *         <li>非 VERIFIED：409 BENEFICIARY_STATUS_CONFLICT；版本不符：409 CONCURRENT_REQUEST。</li>
     */
    @PostExchange("/{beneficiaryAccountId}/default")
    BeneficiaryAccountResponseBody setDefaultBeneficiaryAccount(
        @PathVariable("beneficiaryAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String beneficiaryAccountId,
        @Valid @RequestBody BeneficiaryAccountCommandRequestBody body);

    /**
     * 关闭收款账户（P5，终态，清除默认）
     *
     * @param beneficiaryAccountId 收款账户 ID
     * @param body 归属、关闭原因（BeneficiaryCloseReason）、期望版本与操作人
     * @return CLOSED 账户；REJECTED / CLOSED 再关闭时 409 BENEFICIARY_STATUS_CONFLICT；通知 Owner
     */
    @PostExchange("/{beneficiaryAccountId}/close")
    BeneficiaryAccountResponseBody closeBeneficiaryAccount(
        @PathVariable("beneficiaryAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String beneficiaryAccountId,
        @Valid @RequestBody BeneficiaryAccountCommandRequestBody body);

    /**
     * 停用收款账户（P6，VERIFIED → SUSPENDED，清除默认）
     *
     * @param beneficiaryAccountId 收款账户 ID
     * @param body 停用原因（BeneficiarySuspendReason）、期望版本与运营操作人
     * @return SUSPENDED 账户；非 VERIFIED 时 409 BENEFICIARY_STATUS_CONFLICT；通知 Owner
     */
    @PostExchange("/{beneficiaryAccountId}/suspend")
    BeneficiaryAccountResponseBody suspendBeneficiaryAccount(
        @PathVariable("beneficiaryAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String beneficiaryAccountId,
        @Valid @RequestBody BeneficiaryAccountCommandRequestBody body);

    /**
     * 恢复收款账户（P7，SUSPENDED → PENDING_REVIEW，清空 Maker / Checker 结果，重新核验与冷却）
     *
     * @param beneficiaryAccountId 收款账户 ID
     * @param body 期望版本与运营操作人
     * @return PENDING_REVIEW 账户；非 SUSPENDED 时 409 BENEFICIARY_STATUS_CONFLICT
     */
    @PostExchange("/{beneficiaryAccountId}/resume")
    BeneficiaryAccountResponseBody resumeBeneficiaryAccount(
        @PathVariable("beneficiaryAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String beneficiaryAccountId,
        @Valid @RequestBody BeneficiaryAccountCommandRequestBody body);

    /**
     * 出具 Maker 核验意见（P8，状态仍为 PENDING_REVIEW）
     *
     * @param beneficiaryAccountId 收款账户 ID
     * @param body 意见、拒绝原因、审阅的内容摘要、期望版本与 Maker
     * @return 记录意见后的账户
     *         <li>非 PENDING_REVIEW 或已有 Maker 意见：409 BENEFICIARY_STATUS_CONFLICT；摘要或版本不符：409 CONCURRENT_REQUEST。</li>
     */
    @PostExchange("/{beneficiaryAccountId}/maker-reviews")
    BeneficiaryAccountResponseBody submitMakerReview(
        @PathVariable("beneficiaryAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String beneficiaryAccountId,
        @Valid @RequestBody MakerReviewRequestBody body);

    /**
     * 作出 Checker 复核决定（P9）
     *
     * @param beneficiaryAccountId 收款账户 ID
     * @param body 决定、拒绝原因、审阅的内容摘要、期望版本与 Checker
     * @return APPROVED → VERIFIED（coolingUntil = 决定时间 + 冷却小时）；REJECTED → REJECTED；均通知 Owner
     *         <li>Checker 与 Maker 或运营代提交人同一人：403 FOUR_EYES_REQUIRED。</li>
     *         <li>尚无 Maker 意见、Maker 建议拒绝却批准：409 BENEFICIARY_STATUS_CONFLICT；摘要或版本不符：409 CONCURRENT_REQUEST。</li>
     */
    @PostExchange("/{beneficiaryAccountId}/checker-decisions")
    BeneficiaryAccountResponseBody submitCheckerDecision(
        @PathVariable("beneficiaryAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String beneficiaryAccountId,
        @Valid @RequestBody CheckerDecisionRequestBody body);

    /**
     * 查看收款账户原值（P10，Vault 用途 PAYOUT_BENEFICIARY_REVIEW）
     *
     * @param callerService 调用方服务名；不是 Operations BFF 时 403 BENEFICIARY_REVEAL_FORBIDDEN
     * @param beneficiaryAccountId 收款账户 ID
     * @param body 查看人与原因
     * @return 200 与原值，Cache-Control: no-store；成功记 REVEAL 事件后才返回；Vault 不可用时 503
     */
    @PostExchange("/{beneficiaryAccountId}/reveal")
    ResponseEntity<RevealBeneficiaryAccountResponseBody> revealBeneficiaryAccount(
        @RequestHeader(value = PayoutHeaders.CALLER_SERVICE, required = false) String callerService,
        @PathVariable("beneficiaryAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String beneficiaryAccountId,
        @Valid @RequestBody RevealBeneficiaryAccountRequestBody body);
}
