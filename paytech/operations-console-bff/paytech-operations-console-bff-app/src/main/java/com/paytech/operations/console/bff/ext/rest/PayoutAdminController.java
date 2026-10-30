package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PayoutClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutAccountCommandRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutCheckerDecisionRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutMakerReviewRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutRevealRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutSubmitBeneficiaryRequestBody;
import com.paytech.operations.console.bff.service.model.payout.response.PayoutBeneficiaryDetailResponseBody;
import com.paytech.operations.console.bff.service.model.payout.response.PayoutOwnerAccountsResponseBody;
import com.paytech.operations.console.bff.service.model.payout.response.PayoutReviewItemResponseBody;
import com.paytech.operations.console.bff.service.processor.PayoutBeneficiaryDetailsProcessor;
import com.paytech.operations.console.bff.service.processor.PayoutOwnerAccountsProcessor;
import com.paytech.operations.console.bff.service.processor.PayoutReviewQueueProcessor;
import com.paytech.operations.console.bff.service.processor.request.PayoutBeneficiaryDetailsRequest;
import com.paytech.operations.console.bff.service.processor.request.PayoutOwnerAccountsRequest;
import com.paytech.operations.console.bff.service.processor.request.PayoutReviewQueueRequest;
import com.paytech.payout.api.balance.response.BalanceExplanationResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryRequirementResponseBody;
import com.paytech.payout.api.beneficiary.response.RevealBeneficiaryAccountResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 收款账户核验与 Owner 账户 / 余额的浏览器入口（technique 8.5，块 147）
 * <li>Gateway 绑定：队列、列表、详情、余额 PAYOUT_BENEFICIARY_READ；代提交 SUBMIT；Maker / Checker REVIEW；Reveal REVEAL；
 * 停用 / 恢复 / 关闭 MANAGE，并经 PLT-I02 实时授权。四眼、状态与原因规则由 payout 判断。</li>
 * <li>队列、列表与详情由 Processor 聚合 Owner 法定名与运营操作人显示名；其余为简单转发。操作人取会话 Account ID。</li>
 * <li>全部路径为日志 metadata-only（含原值、户名与法定名）；拦截器为全部响应设置 Cache-Control: no-store。</li>
 * <li>Query 按路由白名单严格匹配，路径变量统一 accountId。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/payout-admin/v1")
public class PayoutAdminController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String OWNER_TYPE = "CLIENT|LOCAL_PARTNER";
    private static final Set<Integer> QUEUE_SIZES = Set.of(10, 20, 50);

    private final PayoutClient payout;
    private final PayoutReviewQueueProcessor reviewQueue;
    private final PayoutOwnerAccountsProcessor ownerAccounts;
    private final PayoutBeneficiaryDetailsProcessor details;

    /** 核验队列：MAKER 待核验、CHECKER 待复核、DONE 近 30 天已处理；每页 10、20 或 50 条。 */
    @GetMapping("/beneficiary-reviews")
    public PagingResponseBody<PayoutReviewItemResponseBody> listBeneficiaryReviews(HttpServletRequest servletRequest,
        OperatorContext operator, @RequestParam("stage") @Pattern(regexp = "MAKER|CHECKER|DONE") String stage,
        @RequestParam(value = "ownerType", required = false) @Pattern(regexp = OWNER_TYPE) String ownerType,
        @RequestParam(value = "ownerId", required = false) @Pattern(regexp = ID) String ownerId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("stage", "ownerType", "ownerId", "page", "size"));
        if ((ownerType == null) != (ownerId == null)) {
            throw new InvalidParameterException("ownerType 与 ownerId 必须同时给出");
        }
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        if (!QUEUE_SIZES.contains(paging.size())) {
            throw new InvalidParameterException("核验队列每页只允许 10、20 或 50 条");
        }
        return reviewQueue.process(new PayoutReviewQueueRequest(operator, stage, ownerType, ownerId, paging.page(),
            paging.size()));
    }

    /** Owner 的全部收款账户与 Owner 主档摘要。 */
    @GetMapping("/beneficiary-accounts")
    public PayoutOwnerAccountsResponseBody listBeneficiaryAccounts(HttpServletRequest servletRequest,
        OperatorContext operator, @RequestParam("ownerType") @Pattern(regexp = OWNER_TYPE) String ownerType,
        @RequestParam("ownerId") @Pattern(regexp = ID) String ownerId,
        @RequestParam(value = "status", required = false)
        @Pattern(regexp = "PENDING_REVIEW|VERIFIED|REJECTED|SUSPENDED|CLOSED") String status,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency) {
        requireQueryKeys(servletRequest, Set.of("ownerType", "ownerId", "status", "currency"));
        return ownerAccounts.process(new PayoutOwnerAccountsRequest(operator, ownerType, ownerId, status, currency));
    }

    /** 核验详情：账户、事件时间线、共用提示、Owner 法定名与操作人显示名（不含原值）。 */
    @GetMapping("/beneficiary-accounts/{accountId}")
    public PayoutBeneficiaryDetailResponseBody getBeneficiaryAccount(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable("accountId") @Pattern(regexp = ID) String accountId) {
        requireQueryKeys(servletRequest, Set.of());
        return details.process(new PayoutBeneficiaryDetailsRequest(operator, accountId));
    }

    /** 代 Owner 提交收款账户；保持 payout 的 201（新建）/ 200（同内容原账户）。 */
    @PostMapping("/beneficiary-accounts")
    public ResponseEntity<BeneficiaryAccountResponseBody> submitBeneficiaryAccount(HttpServletRequest servletRequest,
        OperatorContext operator, @RequestBody @NotNull @Valid PayoutSubmitBeneficiaryRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return payout.submit(operator, body);
    }

    /** Maker 核验意见。 */
    @PostMapping("/beneficiary-accounts/{accountId}/maker-reviews")
    public BeneficiaryAccountResponseBody submitMakerReview(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("accountId") @Pattern(regexp = ID) String accountId,
        @RequestBody @NotNull @Valid PayoutMakerReviewRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return payout.makerReview(operator, accountId, body);
    }

    /** Checker 复核决定；与 Maker 或运营提交人同一人时 payout 返回 403 FOUR_EYES_REQUIRED。 */
    @PostMapping("/beneficiary-accounts/{accountId}/checker-decisions")
    public BeneficiaryAccountResponseBody submitCheckerDecision(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable("accountId") @Pattern(regexp = ID) String accountId,
        @RequestBody @NotNull @Valid PayoutCheckerDecisionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return payout.checkerDecision(operator, accountId, body);
    }

    /** 查看原值：payout 记 REVEAL 事件后返回；响应不缓存，路径只记日志元数据。 */
    @PostMapping("/beneficiary-accounts/{accountId}/reveal")
    public RevealBeneficiaryAccountResponseBody revealBeneficiaryAccount(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable("accountId") @Pattern(regexp = ID) String accountId,
        @RequestBody @NotNull @Valid PayoutRevealRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return payout.reveal(operator, accountId, body.getReasonCode());
    }

    /** 停用（VERIFIED → SUSPENDED）。 */
    @PostMapping("/beneficiary-accounts/{accountId}/suspend")
    public BeneficiaryAccountResponseBody suspendBeneficiaryAccount(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable("accountId") @Pattern(regexp = ID) String accountId,
        @RequestBody @NotNull @Valid PayoutAccountCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return payout.suspend(operator, accountId, body);
    }

    /** 恢复（SUSPENDED → PENDING_REVIEW，重新核验）。 */
    @PostMapping("/beneficiary-accounts/{accountId}/resume")
    public BeneficiaryAccountResponseBody resumeBeneficiaryAccount(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable("accountId") @Pattern(regexp = ID) String accountId,
        @RequestBody @NotNull @Valid PayoutAccountCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return payout.resume(operator, accountId, body);
    }

    /** 关闭（终态）。 */
    @PostMapping("/beneficiary-accounts/{accountId}/close")
    public BeneficiaryAccountResponseBody closeBeneficiaryAccount(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable("accountId") @Pattern(regexp = ID) String accountId,
        @RequestBody @NotNull @Valid PayoutAccountCommandRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return payout.close(operator, accountId, body);
    }

    /** 代提交表单的字段要求；组合不支持时 404 BENEFICIARY_REQUIREMENT_NOT_FOUND。 */
    @GetMapping("/beneficiary-requirements")
    public BeneficiaryRequirementResponseBody getBeneficiaryRequirement(HttpServletRequest servletRequest,
        @RequestParam("railType") @Pattern(regexp = "LOCAL|SWIFT") String railType,
        @RequestParam("bankCountry") @Pattern(regexp = "[A-Z]{2}") String bankCountry,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency) {
        requireQueryKeys(servletRequest, Set.of("railType", "bankCountry", "currency"));
        return payout.requirement(railType, bankCountry, currency);
    }

    /** Owner 各 OE × 币种的余额与当前不能提款的原因。 */
    @GetMapping("/balances")
    public BalanceExplanationResponseBody explainBalance(HttpServletRequest servletRequest,
        @RequestParam("ownerType") @Pattern(regexp = OWNER_TYPE) String ownerType,
        @RequestParam("ownerId") @Pattern(regexp = ID) String ownerId) {
        requireQueryKeys(servletRequest, Set.of("ownerType", "ownerId"));
        return payout.explainBalance(ownerType, ownerId);
    }
}
