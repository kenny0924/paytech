package com.paytech.commercial.domain;

import static com.paytech.commercial.domain.CheckoutResolutionContext.id;
import static com.paytech.commercial.domain.CheckoutResolutionContext.require;

import java.util.List;
import java.util.Objects;

import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ResponsiblePartyType;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.*;
import com.paytech.commercial.domain.ClientMasterCandidates.Candidate;
import com.paytech.commercial.domain.ClientMasterCandidates.ClientFacts;
import com.paytech.commercial.domain.ClientMasterCandidates.Classification;
import com.paytech.commercial.domain.ClientMasterCandidates.LegalDocument;
import com.paytech.commercial.domain.ClientMasterCandidates.LocalPartner;
import com.paytech.commercial.domain.ClientMasterCandidates.RootVersion;
import com.paytech.commercial.domain.ClientMasterCandidates.SellerDisclosure;

import lombok.Value;
import lombok.experimental.Accessors;

/** 一个完整候选的原始业务对象，贯穿外部资格、当前事实复核和最终查询结果构造。 */
@Value
@Accessors(fluent = true)
public class CheckoutCandidateContext {
    /** 同一次解析的输入和冻结商业时点。 */
    CheckoutQuery query;
    /** client-master 返回的 Client，不复制 Client 身份。 */
    ClientFacts client;
    /** 本次使用的 Primary 经营分类。 */
    Classification classification;
    /** client-master 返回的 OE、授权、来源及准确 Scope。 */
    Candidate eligibility;
    /** 与 Client 和 OE 准确匹配的原合同。 */
    Agreement agreement;
    /** 已验证的 Client 条款计划与不可变版本。 */
    CheckoutPricingContext clientTerms;
    /** 合同订阅产品与本次支付方式命中的开通项。 */
    CheckoutSubscriptionContext subscription;
    /** PARTNER 来源的合同与双条款，其他来源为空。 */
    PartnerTerms partnerTerms;
    /** 原消费者法律文档、卖方披露及参与方资料。 */
    Legal legal;
    /** client-master 返回的 Client 根修订。 */
    RootVersion clientRoot;
    /** client-master 返回的 OE 根修订。 */
    RootVersion entityRoot;
    /** 原 Client 合同业务根。 */
    BusinessChangeRecord agreementRoot;
    /** 原 Partner 合同业务根；非 Partner 来源为空。 */
    BusinessChangeRecord partnerAgreementRoot;

    /** 对外资格调用只需要原 OE 身份，不传合同或法律文件。 */
    public String entityNo() {
        return eligibility.entityNo();
    }

    /** Partner 资格只在来源为 Partner 时参与，不补造角色身份。 */
    public Long localPartnerId() {
        return partnerTerms == null ? null : partnerTerms.partner().localPartnerId();
    }

    /** 判断需要复核当前终止状态的原合同。 */
    boolean referencesAgreement(Long agreementId) {
        return Objects.equals(agreement.agreementId(), agreementId)
            || partnerTerms != null && Objects.equals(partnerTerms.agreement().agreementId(), agreementId);
    }

    /** 在外部资格检查前确认所有待返回的业务引用真实有效。 */
    void validateEvidence() {
        java.util.Arrays
            .asList(client.clientId(), classification.businessProfileId(), classification.classificationId(),
                eligibility.affiliationId(), eligibility.scopeId(), eligibility.sourceRelationId(),
                agreement.agreementId(), clientTerms.plan().termPlanId(), clientTerms.version().termVersionId(),
                legal.seller().disclosureId())
            .forEach(CheckoutResolutionContext::id);
        subscription.capabilities().forEach(matched -> id(matched.capability().getAgreementCapabilityId()));
        require(legal.documents().size() == 3);
        legal.documents().forEach(document -> {
            id(document.legalDocumentId());
            id(document.contentFileId());
        });
        legal.parties().forEach(party -> {
            id(party.partyId());
            id(party.profileId());
        });
        if (partnerTerms != null) {
            id(partnerTerms.partner().localPartnerId());
            id(partnerTerms.agreement().agreementId());
            id(partnerTerms.association().agreementOperatingEntityId());
            id(partnerTerms.share().plan().termPlanId());
            id(partnerTerms.share().version().termVersionId());
            id(partnerTerms.serviceCharge().plan().termPlanId());
            id(partnerTerms.serviceCharge().version().termVersionId());
            id(partnerTerms.signatory().partyId());
        }
    }

    /** 全部检查完成后才投影多表查询结果；不再读取数据库或重新决定候选。 */
    public CheckoutContextView toView() {
        return new CheckoutContextView(query.asOfText(), query.evaluatedAt().toString(), id(client.clientId()),
            query.orderNo(), entityNo(), eligibility.sourceType(),
            new CheckoutContextView.Classification(id(classification.businessProfileId()),
                id(classification.classificationId()), classification.industryCode(), classification.mccCode()),
            new CheckoutContextView.Eligibility(id(eligibility.affiliationId()), eligibility.priorityNo(),
                id(eligibility.scopeId()), id(eligibility.sourceRelationId())),
            clientTermsView(), partnerView(), legalView(), derivedView(),
            new CheckoutContextView.Revisions(id(clientRoot.version()), id(entityRoot.version()),
                id(agreementRoot.currentVersion()),
                partnerAgreementRoot == null ? null : id(partnerAgreementRoot.currentVersion())));
    }

    /** Client 合同、条款版本、商业关系、订阅产品与命中的开通项。 */
    private CheckoutContextView.ClientTerms clientTermsView() {
        return new CheckoutContextView.ClientTerms(id(agreement.agreementId()), id(clientTerms.plan().termPlanId()),
            id(clientTerms.version().termVersionId()), agreement.businessMode().getCode(), agreement.fundMode().getCode(),
            agreement.pricingAuthority().getCode(),
            agreement.referralPartnerId() == null ? null : id(agreement.referralPartnerId()),
            subscription.products().stream().map(ProductCode::getCode).toList(),
            subscription.capabilities().stream().map(CheckoutCandidateContext::capabilityView).toList());
    }

    /** 一个命中开通项的投影；维度来自目录行，保证金比例去掉尾随零。 */
    private static CheckoutContextView.Capability capabilityView(CheckoutSubscriptionContext.MatchedCapability matched) {
        AgreementCapability capability = matched.capability();
        return new CheckoutContextView.Capability(id(capability.getAgreementCapabilityId()),
            capability.getCapabilityCode(), capability.getStatus().getCode(),
            matched.catalog().getDimensionCode() == null ? null : matched.catalog().getDimensionCode().getCode(),
            matched.catalog().getDimensionValue(),
            capability.getRollingReserveRate() == null ? null
                : capability.getRollingReserveRate().stripTrailingZeros().toPlainString(),
            capability.getRollingReserveDays());
    }

    /**
     * D02 四项派生，由合同业务模式决定，收单只冻结（technique §2.5）。
     * <li>MOR：卖方为 OE Party，税务责任在 OE；PSP：卖方为 Client Party，税务责任在 Client。</li>
     * <li>资金经平台结算（本期唯一受理的资金模式），两种模式的资金账簿都归收单 OE。</li>
     */
    private CheckoutContextView.Derived derivedView() {
        boolean merchantOfRecord = agreement.businessMode() == BusinessMode.MOR;
        return new CheckoutContextView.Derived(id(merchantOfRecord ? eligibility.oePartyId() : client.partyId()),
            entityNo(),
            (merchantOfRecord ? ResponsiblePartyType.OPERATING_ENTITY : ResponsiblePartyType.CLIENT).getCode(),
            ResponsiblePartyType.OPERATING_ENTITY.getCode());
    }

    /** Partner 专属结果由原合同及两个已验证条款构造。 */
    private CheckoutContextView.PartnerTerms partnerView() {
        if (partnerTerms == null) {
            return null;
        }
        return new CheckoutContextView.PartnerTerms(id(partnerTerms.partner().localPartnerId()),
            id(partnerTerms.agreement().agreementId()), id(partnerTerms.association().agreementOperatingEntityId()),
            id(partnerTerms.share().plan().termPlanId()), id(partnerTerms.share().version().termVersionId()),
            id(partnerTerms.serviceCharge().plan().termPlanId()),
            id(partnerTerms.serviceCharge().version().termVersionId()), id(partnerTerms.signatory().partyId()));
    }

    /** 保留三类法律文档的既定顺序和参与方去重语义。 */
    private CheckoutContextView.Legal legalView() {
        return new CheckoutContextView.Legal(id(agreement.agreementId()),
            id(legal.documents().get(0).legalDocumentId()), id(legal.documents().get(1).legalDocumentId()),
            id(legal.documents().get(2).legalDocumentId()), id(legal.seller().disclosureId()),
            legal.documents().stream()
                .map(document -> new CheckoutContextView.FileEvidence(id(document.legalDocumentId()),
                    id(document.contentFileId()), document.contentHash()))
                .toList(),
            legal.parties().stream().map(party -> new CheckoutContextView.PartyEvidence(id(party.partyId()),
                id(party.profileId()), party.contentHash())).toList());
    }

    /** Partner 来源所需的完整合同和双条款。 */
    @Value
    @Accessors(fluent = true)
    public static class PartnerTerms {
        /** client-master 返回的 Local Partner。 */
        LocalPartner partner;
        /** 原 Partner 合同。 */
        Agreement agreement;
        /** 原合同覆盖 OE 的关联。 */
        AgreementOperatingEntity association;
        /** 分成条款。 */
        CheckoutPricingContext share;
        /** 平台服务费条款。 */
        CheckoutPricingContext serviceCharge;
        /** 唯一平台签约 Party 的原合同角色。 */
        AgreementParty signatory;
    }

    /** 已匹配和批准的法律原对象，后续仅取其不可变引用。 */
    @Value
    @Accessors(fluent = true)
    public static class Legal {
        /** 消费者条款、退款政策、隐私声明，按合同顺序排列。 */
        List<LegalDocument> documents;
        /** 原卖方披露。 */
        SellerDisclosure seller;
        /** 去重的参与方资料。 */
        List<PartyProfileFacts> parties;

        /** 只冻结集合结构，继续持有本次读取的实际业务对象。 */
        public Legal(List<LegalDocument> documents, SellerDisclosure seller, List<PartyProfileFacts> parties) {
            this.documents = List.copyOf(documents);
            this.seller = seller;
            this.parties = List.copyOf(parties);
        }
    }
}
