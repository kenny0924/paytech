package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.ClientReviewRiskClientImpl.require;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PayoutClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutAccountCommandRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutCheckerDecisionRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutMakerReviewRequestBody;
import com.paytech.operations.console.bff.service.model.payout.request.PayoutSubmitBeneficiaryRequestBody;
import com.paytech.payout.api.PayoutHeaders;
import com.paytech.payout.api.balance.BalanceExplanationApi;
import com.paytech.payout.api.balance.response.BalanceExplanationResponseBody;
import com.paytech.payout.api.beneficiary.BeneficiaryAccountApi;
import com.paytech.payout.api.beneficiary.BeneficiaryRequirementApi;
import com.paytech.payout.api.beneficiary.BeneficiaryReviewApi;
import com.paytech.payout.api.beneficiary.request.BeneficiaryAccountCommandRequestBody;
import com.paytech.payout.api.beneficiary.request.CheckerDecisionRequestBody;
import com.paytech.payout.api.beneficiary.request.MakerReviewRequestBody;
import com.paytech.payout.api.beneficiary.request.RevealBeneficiaryAccountRequestBody;
import com.paytech.payout.api.beneficiary.request.SubmitBeneficiaryAccountRequestBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryRequirementResponseBody;
import com.paytech.payout.api.beneficiary.response.RevealBeneficiaryAccountResponseBody;
import com.paytech.payout.api.enums.BeneficiaryActorType;

import lombok.AllArgsConstructor;

/**
 * payout 的 HTTP 转发：浏览器字段与会话操作人组装为 payout 请求，回执核对后原样返回
 * <li>操作人类型固定 OPERATIONS、操作人 ID 为会话 Account ID；运营命令不传 Owner（归属由 payout 按账户判断）。</li>
 * <li>不转发浏览器 Authorization、Cookie 或 Idempotency-Key；HTTP 请求幂等归 Gateway。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PayoutClientImpl implements PayoutClient {
    private final BeneficiaryAccountApi accounts;
    private final BeneficiaryReviewApi reviews;
    private final BalanceExplanationApi balances;
    private final BeneficiaryRequirementApi requirements;

    /** 队列分页；分页回执与元素不得缺失。 */
    @Override
    public PagingResponseBody<BeneficiaryAccountResponseBody> listReviews(String stage, String ownerType, String ownerId,
        int page, int size) {
        var result = reviews.listBeneficiaryReviews(stage, ownerType, ownerId, page, size);
        require(result != null && result.items() != null && result.page() == page && result.size() == size);
        result.items().forEach(PayoutClientImpl::requireAccount);
        return result;
    }

    /** Owner 账户列表；元素必须属于请求的 Owner。 */
    @Override
    public List<BeneficiaryAccountResponseBody> listAccounts(String ownerType, String ownerId, String status,
        String currency) {
        var result = accounts.listBeneficiaryAccounts(PayoutHeaders.OPERATIONS_BFF, ownerType, ownerId, status, currency);
        require(result != null && result.getItems() != null);
        result.getItems().forEach(item -> {
            requireAccount(item);
            require(ownerType.equals(item.getOwnerType()) && ownerId.equals(item.getOwnerId()));
        });
        return result.getItems();
    }

    /** 账户详情；主键必须一致，事件时间线不可缺失。 */
    @Override
    public BeneficiaryAccountResponseBody getAccount(String accountId) {
        var account = accounts.getBeneficiaryAccount(PayoutHeaders.OPERATIONS_BFF, accountId, null, null);
        requireSame(accountId, account);
        require(account.getEvents() != null);
        return account;
    }

    /** 代提交；回执必须是本 Owner 的新账户或原账户。 */
    @Override
    public ResponseEntity<BeneficiaryAccountResponseBody> submit(OperatorContext operator,
        PayoutSubmitBeneficiaryRequestBody request) {
        var body = new SubmitBeneficiaryAccountRequestBody();
        body.setOwnerType(request.getOwnerType());
        body.setOwnerId(request.getOwnerId());
        body.setRailType(request.getRailType());
        body.setBankCountry(request.getBankCountry());
        body.setCurrency(request.getCurrency());
        body.setFields(request.getFields());
        body.setActorType(BeneficiaryActorType.OPERATIONS.getCode());
        body.setActorId(operator.accountId());
        var response = accounts.submitBeneficiaryAccount(body);
        require(response != null && response.getBody() != null);
        var account = response.getBody();
        requireAccount(account);
        require(request.getOwnerType().equals(account.getOwnerType()) && request.getOwnerId().equals(account.getOwnerId()));
        return ResponseEntity.status(response.getStatusCode()).body(account);
    }

    /** Maker 意见。 */
    @Override
    public BeneficiaryAccountResponseBody makerReview(OperatorContext operator, String accountId,
        PayoutMakerReviewRequestBody request) {
        var body = new MakerReviewRequestBody();
        body.setDecision(request.getDecision());
        body.setReasonCode(request.getReasonCode());
        body.setNote(request.getNote());
        body.setClientNote(request.getClientNote());
        body.setContentHash(request.getContentHash());
        body.setExpectedVersion(request.getExpectedVersion());
        body.setActorId(operator.accountId());
        return requireSame(accountId, accounts.submitMakerReview(accountId, body));
    }

    /** Checker 决定。 */
    @Override
    public BeneficiaryAccountResponseBody checkerDecision(OperatorContext operator, String accountId,
        PayoutCheckerDecisionRequestBody request) {
        var body = new CheckerDecisionRequestBody();
        body.setDecision(request.getDecision());
        body.setReasonCode(request.getReasonCode());
        body.setClientNote(request.getClientNote());
        body.setContentHash(request.getContentHash());
        body.setExpectedVersion(request.getExpectedVersion());
        body.setActorId(operator.accountId());
        return requireSame(accountId, accounts.submitCheckerDecision(accountId, body));
    }

    /** 停用。 */
    @Override
    public BeneficiaryAccountResponseBody suspend(OperatorContext operator, String accountId,
        PayoutAccountCommandRequestBody request) {
        return requireSame(accountId, accounts.suspendBeneficiaryAccount(accountId, command(operator, request)));
    }

    /** 恢复。 */
    @Override
    public BeneficiaryAccountResponseBody resume(OperatorContext operator, String accountId,
        PayoutAccountCommandRequestBody request) {
        return requireSame(accountId, accounts.resumeBeneficiaryAccount(accountId, command(operator, request)));
    }

    /** 关闭。 */
    @Override
    public BeneficiaryAccountResponseBody close(OperatorContext operator, String accountId,
        PayoutAccountCommandRequestBody request) {
        return requireSame(accountId, accounts.closeBeneficiaryAccount(accountId, command(operator, request)));
    }

    /** 原值查看；回执主键必须一致。 */
    @Override
    public RevealBeneficiaryAccountResponseBody reveal(OperatorContext operator, String accountId, String reasonCode) {
        var body = new RevealBeneficiaryAccountRequestBody();
        body.setActorId(operator.accountId());
        body.setReasonCode(reasonCode);
        var response = accounts.revealBeneficiaryAccount(PayoutHeaders.OPERATIONS_BFF, accountId, body);
        require(response != null && response.getBody() != null
            && accountId.equals(response.getBody().getBeneficiaryAccountId()));
        return response.getBody();
    }

    /** 字段要求；回执必须对应请求的组合。 */
    @Override
    public BeneficiaryRequirementResponseBody requirement(String railType, String bankCountry, String currency) {
        var result = requirements.getBeneficiaryRequirement(railType, bankCountry, currency);
        require(result != null && result.getFields() != null && railType.equals(result.getRailType())
            && currency.equals(result.getCurrency()));
        return result;
    }

    /** 余额解释；回执必须属于请求的 Owner。 */
    @Override
    public BalanceExplanationResponseBody explainBalance(String ownerType, String ownerId) {
        var result = balances.explainBalance(ownerType, ownerId);
        require(result != null && result.getPositions() != null && result.getUsableAccounts() != null
            && ownerType.equals(result.getOwnerType()) && ownerId.equals(result.getOwnerId()));
        return result;
    }

    /** 运营状态命令：不带 Owner，操作人为会话账号。 */
    static BeneficiaryAccountCommandRequestBody command(OperatorContext operator, PayoutAccountCommandRequestBody request) {
        var body = new BeneficiaryAccountCommandRequestBody();
        body.setReasonCode(request.getReasonCode());
        body.setExpectedVersion(request.getExpectedVersion());
        body.setActorType(BeneficiaryActorType.OPERATIONS.getCode());
        body.setActorId(operator.accountId());
        return body;
    }

    /** 回执必须是请求的账户。 */
    static BeneficiaryAccountResponseBody requireSame(String accountId, BeneficiaryAccountResponseBody account) {
        requireAccount(account);
        require(accountId.equals(account.getBeneficiaryAccountId()));
        return account;
    }

    /** 账户视图的标识、Owner、状态与版本不可缺失。 */
    static void requireAccount(BeneficiaryAccountResponseBody account) {
        require(account != null && account.getBeneficiaryAccountId() != null && account.getOwnerType() != null
            && account.getOwnerId() != null && account.getStatus() != null && account.getVersion() != null);
    }
}
