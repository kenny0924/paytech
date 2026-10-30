package com.paytech.payout.app.ext.rest.converter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.payout.api.beneficiary.request.BeneficiaryAccountCommandRequestBody;
import com.paytech.payout.api.beneficiary.request.BeneficiaryAccountFields;
import com.paytech.payout.api.beneficiary.request.CheckerDecisionRequestBody;
import com.paytech.payout.api.beneficiary.request.MakerReviewRequestBody;
import com.paytech.payout.api.beneficiary.request.RevealBeneficiaryAccountRequestBody;
import com.paytech.payout.api.beneficiary.request.SubmitBeneficiaryAccountRequestBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody.BeneficiaryAccountEventItem;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody.SharedOwnerItem;
import com.paytech.payout.api.beneficiary.response.RevealBeneficiaryAccountResponseBody;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.api.enums.BeneficiaryRevealReason;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccountEvent;
import com.paytech.payout.domain.BeneficiaryAccountDetails;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.service.processor.request.BeneficiaryAccountCommandRequest;
import com.paytech.payout.service.processor.request.CheckerDecisionRequest;
import com.paytech.payout.service.processor.request.MakerReviewRequest;
import com.paytech.payout.service.processor.request.RevealBeneficiaryAccountRequest;
import com.paytech.payout.service.processor.request.SubmitBeneficiaryAccountRequest;

/** 收款账户 HTTP 协议转换；响应只带脱敏户名与末四位，从不包含 Token 或原值（Reveal 响应除外）。 */
@Component
public class BeneficiaryAccountConverter {

    /** 提交命令（含原值，只交给 Processor）。 */
    public SubmitBeneficiaryAccountRequest toSubmitRequest(SubmitBeneficiaryAccountRequestBody body) {
        BeneficiaryAccountFields fields = body.getFields();
        BeneficiaryAccountDetails details = new BeneficiaryAccountDetails(fields.getHolderName(), fields.getIban(),
            fields.getAccountNumber(), fields.getBankCode(), fields.getSwiftBic(), fields.getBankName(),
            fields.getAccountType());
        return new SubmitBeneficiaryAccountRequest(PayoutWire.parse(BeneficiaryOwnerType.class, body.getOwnerType()),
            PayoutWire.id(body.getOwnerId()), PayoutWire.parse(BeneficiaryRailType.class, body.getRailType()),
            body.getBankCountry(), body.getCurrency(), details, PayoutWire.parse(BeneficiaryActorType.class, body.getActorType()),
            PayoutWire.id(body.getActorId()), PayoutWire.sourceKey());
    }

    /** 设默认、关闭、停用、恢复命令。 */
    public BeneficiaryAccountCommandRequest toCommandRequest(String beneficiaryAccountId,
        BeneficiaryAccountCommandRequestBody body) {
        PayoutWire.requireOwnerPair(body.getOwnerType(), body.getOwnerId());
        return new BeneficiaryAccountCommandRequest(PayoutWire.id(beneficiaryAccountId),
            PayoutWire.parse(BeneficiaryOwnerType.class, body.getOwnerType()), PayoutWire.id(body.getOwnerId()),
            body.getReasonCode(), PayoutWire.id(body.getExpectedVersion()),
            PayoutWire.parse(BeneficiaryActorType.class, body.getActorType()), PayoutWire.id(body.getActorId()),
            PayoutWire.sourceKey());
    }

    /** Maker 意见命令。 */
    public MakerReviewRequest toMakerRequest(String beneficiaryAccountId, MakerReviewRequestBody body) {
        return new MakerReviewRequest(PayoutWire.id(beneficiaryAccountId),
            PayoutWire.parse(MakerReviewDecision.class, body.getDecision()), body.getReasonCode(), body.getNote(),
            body.getClientNote(), body.getContentHash(),
            PayoutWire.id(body.getExpectedVersion()), PayoutWire.id(body.getActorId()), PayoutWire.sourceKey());
    }

    /** Checker 决定命令。 */
    public CheckerDecisionRequest toCheckerRequest(String beneficiaryAccountId, CheckerDecisionRequestBody body) {
        return new CheckerDecisionRequest(PayoutWire.id(beneficiaryAccountId),
            PayoutWire.parse(CheckerDecision.class, body.getDecision()), body.getReasonCode(), body.getClientNote(),
            body.getContentHash(),
            PayoutWire.id(body.getExpectedVersion()), PayoutWire.id(body.getActorId()), PayoutWire.sourceKey());
    }

    /** Reveal 命令。 */
    public RevealBeneficiaryAccountRequest toRevealRequest(boolean operationsCaller, String beneficiaryAccountId,
        RevealBeneficiaryAccountRequestBody body) {
        return new RevealBeneficiaryAccountRequest(PayoutWire.id(beneficiaryAccountId), PayoutWire.id(body.getActorId()),
            PayoutWire.parse(BeneficiaryRevealReason.class, body.getReasonCode()), operationsCaller, PayoutWire.sourceKey());
    }

    /** 账户视图响应。 */
    public BeneficiaryAccountResponseBody toResponseBody(BeneficiaryAccountView view) {
        BeneficiaryAccount account = view.getAccount();
        BeneficiaryAccountResponseBody body = new BeneficiaryAccountResponseBody();
        body.setBeneficiaryAccountId(account.getId().toString());
        body.setOwnerType(account.getOwnerType().getCode());
        body.setOwnerId(account.getOwnerId().toString());
        body.setAccountCode(account.getAccountCode());
        body.setRailType(account.getRailType().getCode());
        body.setBankCountry(account.getBankCountry());
        body.setCurrency(account.getCurrency());
        body.setBankName(account.getBankName());
        body.setBankCode(account.getBankCode());
        body.setHolderNameMasked(account.getHolderNameMasked());
        body.setAccountLast4(account.getAccountLast4());
        body.setContentHash(account.getContentHash());
        body.setStatus(account.getStatus().getCode());
        body.setReasonCode(account.getReasonCode());
        body.setReasonNote(account.getReasonNote());
        setReview(body, account);
        body.setCoolingUntil(account.getCoolingUntil());
        body.setUsable(view.isUsable());
        body.setDefaultAccount(Boolean.TRUE.equals(account.getIsDefault()));
        if (view.isOperationsView()) {
            body.setReviewMakerNote(account.getReviewMakerNote());
            body.setSharedOwners(view.getSharedAccounts().stream().map(BeneficiaryAccountConverter::toSharedOwner).toList());
        }
        body.setVersion(account.getVersion().toString());
        body.setCreateTime(account.getCreateTime());
        body.setUpdateTime(account.getUpdateTime());
        body.setEvents(view.getEvents() == null ? null : view.getEvents().stream().map(this::toEventItem).toList());
        return body;
    }

    /** 账户视图列表响应。 */
    public List<BeneficiaryAccountResponseBody> toResponseBodies(List<BeneficiaryAccountView> views) {
        return views.stream().map(this::toResponseBody).toList();
    }

    /** 原值响应。 */
    public RevealBeneficiaryAccountResponseBody toRevealResponseBody(String beneficiaryAccountId,
        BeneficiaryAccountDetails details) {
        RevealBeneficiaryAccountResponseBody body = new RevealBeneficiaryAccountResponseBody();
        body.setBeneficiaryAccountId(beneficiaryAccountId);
        body.setHolderName(details.getHolderName());
        body.setIban(details.getIban());
        body.setAccountNumber(details.getAccountNumber());
        body.setBankCode(details.getBankCode());
        body.setSwiftBic(details.getSwiftBic());
        body.setAccountType(details.getAccountType());
        return body;
    }

    /** 提交人与核验结果字段。 */
    static void setReview(BeneficiaryAccountResponseBody body, BeneficiaryAccount account) {
        body.setSubmittedByType(account.getSubmittedByType().getCode());
        body.setSubmittedBy(account.getSubmittedBy().toString());
        body.setReviewMakerId(PayoutWire.text(account.getReviewMakerId()));
        body.setReviewMakerDecision(PayoutWire.code(account.getReviewMakerDecision()));
        body.setCheckerId(PayoutWire.text(account.getCheckerId()));
        body.setCheckerDecision(PayoutWire.code(account.getCheckerDecision()));
        body.setDecisionTime(account.getDecisionTime());
    }

    /** 共用同一账号的其他 Owner 账户（不含 Token 或原值）。 */
    static SharedOwnerItem toSharedOwner(BeneficiaryAccount shared) {
        SharedOwnerItem item = new SharedOwnerItem();
        item.setOwnerType(shared.getOwnerType().getCode());
        item.setOwnerId(shared.getOwnerId().toString());
        item.setAccountCode(shared.getAccountCode());
        item.setStatus(shared.getStatus().getCode());
        return item;
    }

    /** 一条事件。 */
    BeneficiaryAccountEventItem toEventItem(BeneficiaryAccountEvent event) {
        BeneficiaryAccountEventItem item = new BeneficiaryAccountEventItem();
        item.setEventNo(event.getEventNo());
        item.setFromStatus(PayoutWire.code(event.getFromStatus()));
        item.setToStatus(event.getToStatus().getCode());
        item.setAction(event.getAction().getCode());
        item.setReasonCode(event.getReasonCode());
        item.setActorType(event.getActorType().getCode());
        item.setActorId(PayoutWire.text(event.getActorId()));
        item.setOccurredTime(event.getOccurredTime());
        return item;
    }
}
