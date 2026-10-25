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
                clientTerms(t),
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
                                .toList(),
                        seller(l.seller())),
                derived(c.derived()),
                new ResolveCheckoutContextResponseBody.Revisions(r.clientChangeVersion(), r.entityChangeVersion(),
                        r.clientAgreementChangeVersion(), r.partnerAgreementChangeVersion()),
                relationship(c.relationshipSnapshot()));
    }

    /** 卖方公开文本原样投影。 */
    private static ResolveCheckoutContextResponseBody.Seller seller(CheckoutContextView.Seller s) {
        return new ResolveCheckoutContextResponseBody.Seller(s.legalName(), s.registrationNumber(),
                s.taxRegistrationNumber(), s.addressText(), s.supportEmail(), s.supportPhone());
    }

    /** 商业关系快照原样投影，绑定保持角色顺序。 */
    private static ResolveCheckoutContextResponseBody.RelationshipSnapshot relationship(
            CheckoutContextView.RelationshipSnapshot r) {
        return new ResolveCheckoutContextResponseBody.RelationshipSnapshot(r.effectiveModelVersion(),
                r.snapshotModelVersion(), r.fundFlow(), r.businessMode(),
                r.bindings().stream().map(b -> new ResolveCheckoutContextResponseBody.RoleBinding(b.role(),
                        b.subjectType(), b.subjectId(), b.partyId())).toList());
    }

    /** Client 合同条款与命中的全部开通项。 */
    private static ResolveCheckoutContextResponseBody.ClientTerms clientTerms(CheckoutContextView.ClientTerms t) {
        return new ResolveCheckoutContextResponseBody.ClientTerms(t.agreementId(), t.planId(), t.versionId(),
                t.businessMode(), t.fundMode(), t.pricingAuthority(), t.referralPartnerId(), t.agreementProducts(),
                t.capabilities().stream()
                        .map(k -> new ResolveCheckoutContextResponseBody.Capability(k.agreementCapabilityId(),
                                k.capabilityCode(), k.status(), null, null, k.rollingReserveRate(),
                                k.rollingReserveDays(), k.paymentMethodCode(), k.capabilityKind(),
                                k.conditions().stream().map(c -> new ResolveCheckoutContextResponseBody.CapabilityCondition(
                                        c.dimensionCode(), c.values())).toList(),
                                k.require3ds()))
                        .toList());
    }

    /** 四项派生原样投影。 */
    private static ResolveCheckoutContextResponseBody.Derived derived(CheckoutContextView.Derived d) {
        return new ResolveCheckoutContextResponseBody.Derived(d.sellerPartyId(), d.acquiringEntityNo(),
                d.taxResponsibleParty(), d.fundsBookOwner());
    }
}
