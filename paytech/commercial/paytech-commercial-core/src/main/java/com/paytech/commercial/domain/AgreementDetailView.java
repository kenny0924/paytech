package com.paytech.commercial.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.domain.enums.AgreementStatus;

import lombok.Getter;

/**
 * 合同详情组合结果：合同、参与方、文件、覆盖 OE、订阅、开通项、约束与 BCR Root 版本的多表组合
 * <li>不携带 DataObject，供 app 入站适配器转换为 ResponseBody；集合按主键或代码稳定排序。</li>
 */
@Getter
public class AgreementDetailView {
    /** 合同 ID */
    private final long agreementId;

    /** 合同编号 */
    private final String agreementNo;

    /** 合同名称 */
    private final String agreementName;

    /** 合同类型 */
    private final AgreementAgreementType agreementType;

    /** 业务模式 */
    private final BusinessMode businessMode;

    /** 资金模式 */
    private final FundMode fundMode;

    /** 定价权归属 */
    private final PricingAuthority pricingAuthority;

    /** 推荐伙伴 */
    private final Long referralPartnerId;

    /** Client */
    private final Long clientId;

    /** Local Partner */
    private final Long localPartnerId;

    /** CLIENT 合同 OE */
    private final String entityNo;

    /** PARTNER 合同覆盖 OE，升序 */
    private final List<String> operatingEntityNos;

    /** 合同状态 */
    private final AgreementStatus status;

    /** 生效起点 */
    private final Instant effectiveFrom;

    /** 生效终点 */
    private final Instant effectiveTo;

    /** 签署完成时刻 */
    private final Instant signedTime;

    /** 激活时刻 */
    private final Instant activatedTime;

    /** 合同 BCR Root 当前版本 */
    private final long changeVersion;

    /** 参与方角色，按主键升序 */
    private final List<Party> parties;

    /** 文件引用，按主键升序 */
    private final List<Document> documents;

    /** 订阅的产品，按代码排序 */
    private final List<ProductCode> productCodes;

    /** 开通项，按主键升序 */
    private final List<Capability> capabilities;

    /**
     * 由同一只读快照内读取的行构造
     *
     * @param agreement 合同
     * @param changeVersion Root 当前版本
     * @param parties 参与方
     * @param documents 文件
     * @param operatingEntities 覆盖 OE
     * @param products 订阅
     * @param capabilities 开通项
     * @param limits 开通项的全部约束
     */
    public AgreementDetailView(Agreement agreement, long changeVersion, List<AgreementParty> parties,
        List<AgreementDocumentRef> documents, List<AgreementOperatingEntity> operatingEntities,
        List<AgreementProduct> products, List<AgreementCapability> capabilities, List<AgreementCapabilityLimit> limits) {
        this.agreementId = agreement.getAgreementId();
        this.agreementNo = agreement.getAgreementNo();
        this.agreementName = agreement.getAgreementName();
        this.agreementType = agreement.getAgreementType();
        this.businessMode = agreement.getBusinessMode();
        this.fundMode = agreement.getFundMode();
        this.pricingAuthority = agreement.getPricingAuthority();
        this.referralPartnerId = agreement.getReferralPartnerId();
        this.clientId = agreement.getClientId();
        this.localPartnerId = agreement.getLocalPartnerId();
        this.entityNo = agreement.getEntityNo();
        this.operatingEntityNos = operatingEntities.stream().map(AgreementOperatingEntity::getEntityNo).sorted().toList();
        this.status = agreement.getStatus();
        this.effectiveFrom = agreement.getEffectiveFrom();
        this.effectiveTo = agreement.getEffectiveTo();
        this.signedTime = agreement.getSignedTime();
        this.activatedTime = agreement.getActivatedTime();
        this.changeVersion = changeVersion;
        this.parties = parties.stream().sorted(Comparator.comparing(AgreementParty::getAgreementPartyId)).map(Party::new)
            .toList();
        this.documents = documents.stream().sorted(Comparator.comparing(AgreementDocumentRef::getAgreementDocumentId))
            .map(Document::new).toList();
        this.productCodes = products.stream().map(AgreementProduct::getProductCode)
            .sorted(Comparator.comparing(ProductCode::getCode)).toList();
        Map<Long, List<AgreementCapabilityLimit>> byCapability = limits.stream()
            .collect(Collectors.groupingBy(AgreementCapabilityLimit::getAgreementCapabilityId));
        this.capabilities = capabilities.stream()
            .sorted(Comparator.comparing(AgreementCapability::getAgreementCapabilityId))
            .map(capability -> new Capability(capability,
                byCapability.getOrDefault(capability.getAgreementCapabilityId(), List.of())))
            .toList();
    }

    /** 参与方的一个角色及其签署结果。 */
    @Getter
    public static class Party {
        /** 角色行 ID */
        private final long agreementPartyId;

        /** 法律 Party */
        private final long partyId;

        /** 资料版本 */
        private final long partyProfileId;

        /** 角色 */
        private final AgreementPartyPartyRole partyRole;

        /** 签署方式 */
        private final AgreementPartyAcceptanceMethod acceptanceMethod;

        /** 实际接受的外部 Actor */
        private final Long acceptedByActorId;

        /** 接受时刻 */
        private final Instant acceptedTime;

        /** 合同接受事件编号 */
        private final String acceptanceReference;

        /** 签署证据文件引用 */
        private final Long evidenceDocumentId;

        /** 由角色行构造。 */
        Party(AgreementParty party) {
            this.agreementPartyId = party.getAgreementPartyId();
            this.partyId = party.getPartyId();
            this.partyProfileId = party.getPartyProfileId();
            this.partyRole = party.getPartyRole();
            this.acceptanceMethod = party.getAcceptanceMethod();
            this.acceptedByActorId = party.getAcceptedByActorId();
            this.acceptedTime = party.getAcceptedTime();
            this.acceptanceReference = party.getAcceptanceReference();
            this.evidenceDocumentId = party.getEvidenceDocumentId();
        }
    }

    /** 合同文件引用。 */
    @Getter
    public static class Document {
        /** 文件引用 ID */
        private final long agreementDocumentId;

        /** 用途 */
        private final AgreementDocumentRefDocumentType documentType;

        /** 文件 ID */
        private final long fileId;

        /** SHA-256 */
        private final String contentHash;

        /** 由文件引用行构造。 */
        Document(AgreementDocumentRef document) {
            this.agreementDocumentId = document.getAgreementDocumentId();
            this.documentType = document.getDocumentType();
            this.fileId = document.getFileId();
            this.contentHash = document.getContentHash();
        }
    }

    /** 开通项及其约束。 */
    @Getter
    public static class Capability {
        /** 开通项 ID */
        private final long agreementCapabilityId;

        /** 准确 OE */
        private final String entityNo;

        /** 可开通项代码 */
        private final String capabilityCode;

        /** 状态 */
        private final AgreementCapabilityStatus status;

        /** 生效起点 */
        private final Instant effectiveFrom;

        /** 生效终点 */
        private final Instant effectiveTo;

        /** 滚动保证金比例 */
        private final BigDecimal rollingReserveRate;

        /** 滚动保证金天数 */
        private final Integer rollingReserveDays;

        /** 是否要求 3DS 认证 */
        private final boolean require3ds;

        /** 接受条件行，按维度与值排序 */
        private final List<Limit> limits;

        /** 由开通项与其约束构造。 */
        Capability(AgreementCapability capability, List<AgreementCapabilityLimit> limits) {
            this.agreementCapabilityId = capability.getAgreementCapabilityId();
            this.entityNo = capability.getEntityNo();
            this.capabilityCode = capability.getCapabilityCode();
            this.status = capability.getStatus();
            this.effectiveFrom = capability.getEffectiveFrom();
            this.effectiveTo = capability.getEffectiveTo();
            this.rollingReserveRate = capability.getRollingReserveRate();
            this.rollingReserveDays = capability.getRollingReserveDays();
            this.require3ds = Boolean.TRUE.equals(capability.getRequire3ds());
            this.limits = limits.stream()
                .sorted(Comparator.comparing((AgreementCapabilityLimit limit) -> limit.getLimitCode().getCode())
                    .thenComparing(AgreementCapabilityLimit::getLimitValue))
                .map(limit -> new Limit(limit.getLimitCode(), limit.getLimitValue())).toList();
        }
    }

    /** 开通项接受条件的一个取值。 */
    @Getter
    public static class Limit {
        /** 条件维度 */
        private final PricingDimension limitCode;

        /** 规范化取值 */
        private final String limitValue;

        /** 构造条件取值。 */
        Limit(PricingDimension limitCode, String limitValue) {
            this.limitCode = limitCode;
            this.limitValue = limitValue;
        }
    }
}
