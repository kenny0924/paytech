package com.paytech.commercial.domain;

import static com.paytech.commercial.domain.CheckoutResolutionContext.id;
import static com.paytech.commercial.domain.CheckoutResolutionContext.require;

import java.util.List;
import java.util.Objects;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.*;

import lombok.Value;
import lombok.experimental.Accessors;

/** 一个完整候选的原始业务对象，贯穿外部资格、当前事实复核和最终查询结果构造。 */
@Value
@Accessors(fluent = true)
public class CheckoutCandidateContext {
    /** 同一次解析的输入和冻结商业时点。 */
    CheckoutQuery query;
    /** 原 Client 对象，不复制 Client 身份。 */
    Client client;
    /** 本次使用的 Primary 经营分类。 */
    ClientBusinessClassification classification;
    /** 原 OE、授权、来源及准确 Scope。 */
    EntityEligibility eligibility;
    /** 与 Client 和 OE 准确匹配的原合同。 */
    Agreement agreement;
    /** 已验证的 Client 条款计划与不可变版本。 */
    CheckoutPricingContext clientTerms;
    /** PARTNER 来源的合同与双条款，其他来源为空。 */
    PartnerTerms partnerTerms;
    /** 原消费者法律文档、卖方披露及参与方资料。 */
    Legal legal;
    /** 原 Client 业务根。 */
    BusinessChangeRecord clientRoot;
    /** 原 OE 业务根。 */
    BusinessChangeRecord entityRoot;
    /** 原 Client 合同业务根。 */
    BusinessChangeRecord agreementRoot;
    /** 原 Partner 合同业务根；非 Partner 来源为空。 */
    BusinessChangeRecord partnerAgreementRoot;

    /** 对外资格调用只需要原 OE 身份，不传合同或法律文件。 */
    public String entityNo() {
        return eligibility.entity().entityNo();
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
                eligibility.affiliation().affiliationId(), eligibility.scope().scopeId(),
                eligibility.source().sourceRelationId(), agreement.agreementId(), clientTerms.plan().termPlanId(),
                clientTerms.version().termVersionId(), legal.seller().disclosureId())
            .forEach(CheckoutResolutionContext::id);
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
            query.orderNo(), entityNo(), eligibility.source().sourceType().getCode(),
            new CheckoutContextView.Classification(id(classification.businessProfileId()),
                id(classification.classificationId()), classification.industryCode(), classification.mccCode()),
            new CheckoutContextView.Eligibility(id(eligibility.affiliation().affiliationId()),
                eligibility.affiliation().priorityNo(), id(eligibility.scope().scopeId()),
                id(eligibility.source().sourceRelationId())),
            new CheckoutContextView.ClientTerms(id(agreement.agreementId()), id(clientTerms.plan().termPlanId()),
                id(clientTerms.version().termVersionId())),
            partnerView(), legalView(),
            new CheckoutContextView.Revisions(id(clientRoot.currentVersion()), id(entityRoot.currentVersion()),
                id(agreementRoot.currentVersion()),
                partnerAgreementRoot == null ? null : id(partnerAgreementRoot.currentVersion())));
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

    /** 原准入对象的组合；没有与对象重复的身份字段。 */
    @Value
    @Accessors(fluent = true)
    public static class EntityEligibility {
        /** Client/OE 原关联。 */
        ClientEntityAffiliation affiliation;
        /** 原经营主体。 */
        OperatingEntity entity;
        /** 原来源关系。 */
        EntitySourceRelation source;
        /** 批准的准确范围。 */
        OperatingEntityScope scope;

        /** 同 OE 后继记录不能代替已选授权，即使当前也合法。 */
        boolean sameAuthorization(EntityEligibility current) {
            return entity.entityNo().equals(current.entity.entityNo())
                && affiliation.affiliationId().equals(current.affiliation.affiliationId())
                && affiliation.priorityNo().equals(current.affiliation.priorityNo())
                && scope.scopeId().equals(current.scope.scopeId())
                && source.sourceRelationId().equals(current.source.sourceRelationId());
        }
    }

    /** Partner 来源所需的完整合同和双条款。 */
    @Value
    @Accessors(fluent = true)
    public static class PartnerTerms {
        /** 原 Local Partner。 */
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
        List<ConsumerLegalDocument> documents;
        /** 原卖方披露。 */
        SellerDisclosure seller;
        /** 去重的原参与方资料。 */
        List<PartyProfile> parties;

        /** 只冻结集合结构，继续持有本次读取的实际业务对象。 */
        public Legal(List<ConsumerLegalDocument> documents, SellerDisclosure seller, List<PartyProfile> parties) {
            this.documents = List.copyOf(documents);
            this.seller = seller;
            this.parties = List.copyOf(parties);
        }
    }
}
