package com.paytech.operations.console.bff.integration;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutAccountCommandRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutCheckerDecisionRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutMakerReviewRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutSubmitBeneficiaryRequestBody;
import com.paytech.payout.api.balance.response.BalanceExplanationResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryRequirementResponseBody;
import com.paytech.payout.api.beneficiary.response.RevealBeneficiaryAccountResponseBody;

/**
 * payout 收款账户与余额解释的本地端口；状态规则、四眼、冷却、查重与阻断原因都归 payout
 * <li>读取与 Reveal 以 X-Caller-Service=operations-console-bff 调用，取得运营视图（共用提示）；命令以会话 Account ID 作为操作人。</li>
 * <li>每个方法只调用一次 payout，不重试；回执中的账户 ID 必须与请求一致。</li>
 *
 * @author Claude
 */
public interface PayoutClient {

    /**
     * 核验队列分页
     *
     * @param stage MAKER、CHECKER 或 DONE
     * @param ownerType 可选 Owner 类型
     * @param ownerId 可选 Owner 标识
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 账户分页（含共用提示）
     */
    PagingResponseBody<BeneficiaryAccountResponseBody> listReviews(String stage, String ownerType, String ownerId, int page,
        int size);

    /**
     * Owner 的收款账户
     *
     * @param ownerType Owner 类型
     * @param ownerId Owner 标识
     * @param status 可选状态
     * @param currency 可选币种
     * @return 账户（不含事件）
     */
    List<BeneficiaryAccountResponseBody> listAccounts(String ownerType, String ownerId, String status, String currency);

    /**
     * 收款账户详情（含事件时间线）
     *
     * @param accountId 收款账户 ID
     * @return 账户；不存在时 payout 的 404 原样抛出
     */
    BeneficiaryAccountResponseBody getAccount(String accountId);

    /**
     * 运营代 Owner 提交收款账户
     *
     * @param operator 提交人会话
     * @param request 账户内容
     * @return payout 的 201（新账户）或 200（同内容原账户）
     */
    ResponseEntity<BeneficiaryAccountResponseBody> submit(OperatorContext operator, PayoutSubmitBeneficiaryRequestBody request);

    /**
     * Maker 核验意见
     *
     * @param operator Maker 会话
     * @param accountId 收款账户 ID
     * @param request 意见、原因、内容摘要与期望版本
     * @return 记录意见后的账户
     */
    BeneficiaryAccountResponseBody makerReview(OperatorContext operator, String accountId, PayoutMakerReviewRequestBody request);

    /**
     * Checker 复核决定
     *
     * @param operator Checker 会话
     * @param accountId 收款账户 ID
     * @param request 决定、原因、内容摘要与期望版本
     * @return 决定后的账户
     */
    BeneficiaryAccountResponseBody checkerDecision(OperatorContext operator, String accountId,
        PayoutCheckerDecisionRequestBody request);

    /**
     * 停用收款账户
     *
     * @param operator 操作人会话
     * @param accountId 收款账户 ID
     * @param request 停用原因与期望版本
     * @return SUSPENDED 账户
     */
    BeneficiaryAccountResponseBody suspend(OperatorContext operator, String accountId, PayoutAccountCommandRequestBody request);

    /**
     * 恢复收款账户（重新核验）
     *
     * @param operator 操作人会话
     * @param accountId 收款账户 ID
     * @param request 期望版本
     * @return PENDING_REVIEW 账户
     */
    BeneficiaryAccountResponseBody resume(OperatorContext operator, String accountId, PayoutAccountCommandRequestBody request);

    /**
     * 关闭收款账户
     *
     * @param operator 操作人会话
     * @param accountId 收款账户 ID
     * @param request 关闭原因与期望版本
     * @return CLOSED 账户
     */
    BeneficiaryAccountResponseBody close(OperatorContext operator, String accountId, PayoutAccountCommandRequestBody request);

    /**
     * 查看收款账户原值
     *
     * @param operator 查看人会话
     * @param accountId 收款账户 ID
     * @param reasonCode MAKER_REVIEW 或 CHECKER_REVIEW
     * @return 原值；不缓存、不写普通日志
     */
    RevealBeneficiaryAccountResponseBody reveal(OperatorContext operator, String accountId, String reasonCode);

    /**
     * 收款方式 × 国家 × 币种的字段要求
     *
     * @param railType LOCAL 或 SWIFT
     * @param bankCountry 开户国家
     * @param currency 币种
     * @return 字段要求；组合不支持时 payout 的 404 原样抛出
     */
    BeneficiaryRequirementResponseBody requirement(String railType, String bankCountry, String currency);

    /**
     * Owner 余额解释
     *
     * @param ownerType Owner 类型
     * @param ownerId Owner 标识
     * @return 各 OE × 币种余额、阻断原因与可用账户
     */
    BalanceExplanationResponseBody explainBalance(String ownerType, String ownerId);
}
