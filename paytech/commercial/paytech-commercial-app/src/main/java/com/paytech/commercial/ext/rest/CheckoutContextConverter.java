package com.paytech.commercial.ext.rest;

import com.paytech.commercial.domain.CheckoutContextView;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;

/** 领域结果到轻量 Wire 的一次性转换；不访问数据库或执行校验。 */
final class CheckoutContextConverter {
    /** 工具类不创建实例。 */
    private CheckoutContextConverter() {
    }
    /** 从已验证候选构造稳定 Wire 响应，不重新查询事实。 */
    static ResolveCheckoutContextResponseBody convert(CheckoutContextView c) {
        var k = c.classification();
        var e = c.eligibility();
        var t = c.clientTerms();
        var p = c.partnerTerms();
        var l = c.legal();
        var r = c.revisions();
        return new ResolveCheckoutContextResponseBody(c.contextAsOfTime(), c.evaluatedAt(), c.clientId(),
                c.orderNo(), c.entityNo(), c.sourceType(),
                new ResolveCheckoutContextResponseBody.Classification(k.businessProfileId(), k.classificationId(), k.industryCode(),
                        k.mccCode()),
                new ResolveCheckoutContextResponseBody.Eligibility(e.affiliationId(), e.priorityNo(), e.entityScopeId(),
                        e.entitySourceRelationId()),
                new ResolveCheckoutContextResponseBody.ClientTerms(t.agreementId(), t.planId(), t.versionId()),
                p == null
                        ? null
                        : new ResolveCheckoutContextResponseBody.PartnerTerms(p.localPartnerId(), p.agreementId(),
                                p.agreementOperatingEntityId(), p.sharePlanId(), p.shareVersionId(),
                                p.serviceChargePlanId(),
                                p.serviceChargeVersionId(), p.platformSignatoryPartyId()),
                new ResolveCheckoutContextResponseBody.Legal(l.clientAgreementId(), l.consumerTermsDocumentId(),
                        l.refundPolicyDocumentId(),
                        l.privacyNoticeDocumentId(), l.sellerDisclosureId(),
                        l.documents().stream()
                                .map(d -> new ResolveCheckoutContextResponseBody.FileEvidence(d.documentId(), d.fileId(),
                                        d.contentHash()))
                                .toList(),
                        l.parties().stream()
                                .map(f -> new ResolveCheckoutContextResponseBody.PartyEvidence(f.partyId(), f.partyProfileId(),
                                        f.profileContentHash()))
                                .toList()),
                new ResolveCheckoutContextResponseBody.Revisions(r.clientChangeVersion(), r.entityChangeVersion(),
                        r.clientAgreementChangeVersion(), r.partnerAgreementChangeVersion()));
    }
}
