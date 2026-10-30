package com.paytech.payout.app.ext.rest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payout.api.PayoutHeaders;
import com.paytech.payout.api.beneficiary.BeneficiaryAccountApi;
import com.paytech.payout.api.beneficiary.request.BeneficiaryAccountCommandRequestBody;
import com.paytech.payout.api.beneficiary.request.CheckerDecisionRequestBody;
import com.paytech.payout.api.beneficiary.request.MakerReviewRequestBody;
import com.paytech.payout.api.beneficiary.request.RevealBeneficiaryAccountRequestBody;
import com.paytech.payout.api.beneficiary.request.SubmitBeneficiaryAccountRequestBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountListResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.beneficiary.response.RevealBeneficiaryAccountResponseBody;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.app.ext.rest.converter.BeneficiaryAccountConverter;
import com.paytech.payout.app.ext.rest.converter.PayoutWire;
import com.paytech.payout.service.processor.CloseBeneficiaryAccountProcessor;
import com.paytech.payout.service.processor.GetBeneficiaryAccountProcessor;
import com.paytech.payout.service.processor.ListBeneficiaryAccountsProcessor;
import com.paytech.payout.service.processor.ResumeBeneficiaryAccountProcessor;
import com.paytech.payout.service.processor.RevealBeneficiaryAccountProcessor;
import com.paytech.payout.service.processor.SetDefaultBeneficiaryAccountProcessor;
import com.paytech.payout.service.processor.SubmitBeneficiaryAccountProcessor;
import com.paytech.payout.service.processor.SubmitCheckerDecisionProcessor;
import com.paytech.payout.service.processor.SubmitMakerReviewProcessor;
import com.paytech.payout.service.processor.SuspendBeneficiaryAccountProcessor;
import com.paytech.payout.service.processor.request.GetBeneficiaryAccountRequest;
import com.paytech.payout.service.processor.request.ListBeneficiaryAccountsRequest;
import com.paytech.payout.service.processor.response.SubmitBeneficiaryAccountResponse;

import lombok.AllArgsConstructor;

/** 收款账户命令与查询的 HTTP 协议适配：每个入口委托一个 Processor。 */
@RestController
@AllArgsConstructor
public class BeneficiaryAccountController implements BeneficiaryAccountApi {
    private final BeneficiaryAccountConverter converter;
    private final GetBeneficiaryAccountProcessor getProcessor;
    private final CloseBeneficiaryAccountProcessor closeProcessor;
    private final ListBeneficiaryAccountsProcessor listProcessor;
    private final ResumeBeneficiaryAccountProcessor resumeProcessor;
    private final RevealBeneficiaryAccountProcessor revealProcessor;
    private final SubmitMakerReviewProcessor makerReviewProcessor;
    private final SuspendBeneficiaryAccountProcessor suspendProcessor;
    private final SubmitBeneficiaryAccountProcessor submitProcessor;
    private final SetDefaultBeneficiaryAccountProcessor setDefaultProcessor;
    private final SubmitCheckerDecisionProcessor checkerDecisionProcessor;

    /** 新建 201，同内容重复提交 200。 */
    @Override
    public ResponseEntity<BeneficiaryAccountResponseBody> submitBeneficiaryAccount(SubmitBeneficiaryAccountRequestBody body) {
        SubmitBeneficiaryAccountResponse response = submitProcessor.process(converter.toSubmitRequest(body));
        return ResponseEntity.status(response.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(converter.toResponseBody(response.getView()));
    }

    /** Owner 账户列表。 */
    @Override
    public BeneficiaryAccountListResponseBody listBeneficiaryAccounts(String callerService, String ownerType, String ownerId,
        String status, String currency) {
        BeneficiaryAccountListResponseBody body = new BeneficiaryAccountListResponseBody();
        body.setItems(converter.toResponseBodies(listProcessor.process(new ListBeneficiaryAccountsRequest(
            PayoutWire.parse(BeneficiaryOwnerType.class, ownerType), PayoutWire.id(ownerId),
            PayoutWire.parse(BeneficiaryAccountStatus.class, status), currency, operations(callerService)))));
        return body;
    }

    /** 账户详情。 */
    @Override
    public BeneficiaryAccountResponseBody getBeneficiaryAccount(String callerService, String beneficiaryAccountId,
        String ownerType, String ownerId) {
        PayoutWire.requireOwnerPair(ownerType, ownerId);
        return converter.toResponseBody(getProcessor.process(new GetBeneficiaryAccountRequest(
            PayoutWire.id(beneficiaryAccountId), PayoutWire.parse(BeneficiaryOwnerType.class, ownerType),
            PayoutWire.id(ownerId), operations(callerService))));
    }

    /** 设默认。 */
    @Override
    public BeneficiaryAccountResponseBody setDefaultBeneficiaryAccount(String beneficiaryAccountId,
        BeneficiaryAccountCommandRequestBody body) {
        return converter.toResponseBody(setDefaultProcessor.process(converter.toCommandRequest(beneficiaryAccountId, body)));
    }

    /** 关闭。 */
    @Override
    public BeneficiaryAccountResponseBody closeBeneficiaryAccount(String beneficiaryAccountId,
        BeneficiaryAccountCommandRequestBody body) {
        return converter.toResponseBody(closeProcessor.process(converter.toCommandRequest(beneficiaryAccountId, body)));
    }

    /** 停用。 */
    @Override
    public BeneficiaryAccountResponseBody suspendBeneficiaryAccount(String beneficiaryAccountId,
        BeneficiaryAccountCommandRequestBody body) {
        return converter.toResponseBody(suspendProcessor.process(converter.toCommandRequest(beneficiaryAccountId, body)));
    }

    /** 恢复。 */
    @Override
    public BeneficiaryAccountResponseBody resumeBeneficiaryAccount(String beneficiaryAccountId,
        BeneficiaryAccountCommandRequestBody body) {
        return converter.toResponseBody(resumeProcessor.process(converter.toCommandRequest(beneficiaryAccountId, body)));
    }

    /** Maker 意见。 */
    @Override
    public BeneficiaryAccountResponseBody submitMakerReview(String beneficiaryAccountId, MakerReviewRequestBody body) {
        return converter.toResponseBody(makerReviewProcessor.process(converter.toMakerRequest(beneficiaryAccountId, body)));
    }

    /** Checker 决定。 */
    @Override
    public BeneficiaryAccountResponseBody submitCheckerDecision(String beneficiaryAccountId,
        CheckerDecisionRequestBody body) {
        return converter.toResponseBody(checkerDecisionProcessor.process(
            converter.toCheckerRequest(beneficiaryAccountId, body)));
    }

    /** 原值查看：no-store。 */
    @Override
    public ResponseEntity<RevealBeneficiaryAccountResponseBody> revealBeneficiaryAccount(String callerService,
        String beneficiaryAccountId, RevealBeneficiaryAccountRequestBody body) {
        RevealBeneficiaryAccountResponseBody response = converter.toRevealResponseBody(beneficiaryAccountId,
            revealProcessor.process(converter.toRevealRequest(operations(callerService), beneficiaryAccountId, body)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    /** 调用方是否为 Operations BFF。 */
    static boolean operations(String callerService) {
        return PayoutHeaders.OPERATIONS_BFF.equals(callerService);
    }
}
