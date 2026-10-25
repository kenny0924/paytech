package com.paytech.acquiring.domain.checkout;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRelationshipBinding;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.*;
import com.paytech.acquiring.domain.enums.EntitySourceType;
import com.paytech.commercial.api.enums.FundFlow;
import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.commercial.api.enums.RelationshipSubjectType;
import com.paytech.foundation.common.utils.EnumUtils;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建 Checkout 时取得的外部结果；只校验和构造本库对象，不访问外部服务或数据库。 */
@Getter
@AllArgsConstructor
public class CheckoutInitializationContext {
    /** 订单创建输入。 */
    private final InitializationRequest request;

    /** CCT 商业事实。 */
    private final CommercialFacts commercial;

    /** 订单资格评估引用。 */
    private final long riskId;

    /** 税额和最终金额。 */
    private final TaxFacts tax;

    /** 已生成的消费者合同文件。 */
    private final RenderedContract rendered;

    /** Owner 结果完整且金额恒等式成立后，才形成 TERM_PRICE 本库写入对象。 */
    public FrozenCheckoutRecord buildFrozen(long orderContextId, long orderLegalId, long consumerContractId) {
        var f = commercial;
        var now = request.getContextTime();
        if (riskId <= 0 || tax == null || tax.snapshotId() <= 0 || !hash(tax.snapshotHash()) || rendered == null
            || rendered.fileId() <= 0 || !hash(rendered.contentHash()) || tax.taxAmount() == null
            || tax.roundingAmount() == null || tax.totalAmount() == null || tax.taxAmount().signum() < 0
            || tax.totalAmount().signum() <= 0
            || request.getNetAmount().add(tax.taxAmount()).add(tax.roundingAmount()).compareTo(tax.totalAmount()) != 0
            || !money(tax.taxAmount()) || !money(tax.roundingAmount()) || !money(tax.totalAmount())) {
            throw new CheckoutUpstreamProtocolException("Checkout 初始化权威结果或金额不完整");
        }
        validateTaxLines(f, tax);
        var seller = f.seller();
        var relationship = f.relationship();
        return FrozenCheckoutRecord.builder().orderContextId(orderContextId).orderId(request.getOrderId())
            .commercialContextTime(request.getContextTime()).affiliationId(f.affiliationId())
            .entityScopeId(f.entityScopeId()).entitySourceRelationId(f.entitySourceRelationId())
            .clientPricingVersionId(f.clientPricingVersionId()).partnerShareVersionId(f.partnerShareVersionId())
            .platformChargeVersionId(f.platformChargeVersionId()).platformServicePartyId(f.platformServicePartyId())
            .riskCheckoutAssessmentId(riskId).financeTaxSnapshotId(tax.snapshotId())
            .financeTaxSnapshotHash(tax.snapshotHash()).consumerCountryCode(request.getCountry()).locale(request.getLocale())
            .fundFlow(relationship == null ? null : relationship.fundFlow())
            .effectiveRelationshipModelVersion(relationship == null ? 0 : relationship.effectiveModelVersion())
            .snapshotModelVersion(relationship == null ? null : relationship.snapshotModelVersion())
            .orderLegalId(orderLegalId).clientAgreementId(f.clientAgreementId())
            .partnerAgreementId(f.partnerAgreementId())
            .partnerAgreementOperatingEntityId(f.partnerAgreementOperatingEntityId())
            .consumerTermsDocumentId(f.consumerTerms().documentId()).consumerTermsFileId(f.consumerTerms().fileId())
            .consumerTermsContentHash(f.consumerTerms().contentHash())
            .refundPolicyDocumentId(f.refundPolicy().documentId()).refundPolicyFileId(f.refundPolicy().fileId())
            .refundPolicyContentHash(f.refundPolicy().contentHash())
            .privacyNoticeDocumentId(f.privacyNotice().documentId()).privacyNoticeFileId(f.privacyNotice().fileId())
            .privacyNoticeContentHash(f.privacyNotice().contentHash()).sellerDisclosureId(f.sellerDisclosureId())
            .publicLegalName(seller.legalName()).publicRegistrationNumber(seller.registrationNumber())
            .publicTaxRegistrationNumber(seller.taxRegistrationNumber()).publicAddressText(seller.addressText())
            .publicSupportEmail(seller.supportEmail()).publicSupportPhone(seller.supportPhone())
            .consumerContractId(consumerContractId).renderedFileId(rendered.fileId())
            .renderedContentHash(rendered.contentHash()).presentedAt(now).build();
    }

    /**
     * 按 commercial 快照逐角色构造绑定行；没有快照（固定 Mock）时返回空集合，不写绑定表。
     *
     * @param ids 本地主键来源，每行取一次
     * @return 与快照角色顺序一致的待插入行
     */
    public List<OrderRelationshipBinding> buildRelationshipBindings(LongSupplier ids) {
        var relationship = commercial.relationship();
        if (relationship == null) {
            return List.of();
        }
        return relationship.bindings().stream().map(binding -> OrderRelationshipBinding.builder()
            .bindingId(ids.getAsLong()).orderId(request.getOrderId()).roleCode(binding.role())
            .subjectType(binding.subjectType()).subjectId(binding.subjectId()).partyId(binding.partyId())
            .snapshotModelVersion(relationship.snapshotModelVersion()).createTime(request.getContextTime()).build())
            .toList();
    }

    /** 全 OE 授权仍需准确 Scope、合同、来源及三份文件的完整结果；来源只接受 CLIENT / PARTNER。 */
    public static void validateCommercial(CommercialFacts f) {
        if (f == null || f.entityNo() == null || !f.entityNo().matches("[A-Z]{2}[0-9]{3}")
            || EnumUtils.getEnumByCode(EntitySourceType.class, f.entitySourceType()) == null
            || f.affiliationId() <= 0 || f.entityScopeId() <= 0 || f.entitySourceRelationId() <= 0
            || f.clientPricingVersionId() <= 0 || f.clientAgreementId() <= 0 || f.sellerDisclosureId() <= 0
            || !text(f.industryCode(), 64) || f.mccCode() == null || !f.mccCode().matches("[0-9]{4}")
            || f.seller() == null || !text(f.seller().legalName(), 255) || !text(f.seller().registrationNumber(), 128)
            || !text(f.seller().addressText(), 512) || !text(f.seller().supportEmail(), 254) || !file(f.consumerTerms())
            || !file(f.refundPolicy()) || !file(f.privacyNotice())) {
            throw new CheckoutUpstreamProtocolException("CCT 商业事实不完整");
        }
        boolean partner = EntitySourceType.PARTNER.getCode().equals(f.entitySourceType());
        Long[] partnerIds = {f.localPartnerId(), f.partnerShareVersionId(), f.platformChargeVersionId(),
            f.platformServicePartyId(), f.partnerAgreementId(), f.partnerAgreementOperatingEntityId()};
        for (Long id : partnerIds) {
            if (partner ? id == null || id <= 0 : id != null) {
                throw new CheckoutUpstreamProtocolException("CCT 来源合同组合不一致");
            }
        }
        validateRelationship(f.entityNo(), f.relationship());
    }

    /**
     * 有快照时校验其可冻结：资金流向与版本取值合法，角色与主体类型为已发布 Code 且角色不重复，CLIENT / SELLER / ACQUIRING_OE
     * 必在，ACQUIRING_OE 指向本次 OE；不推导或补齐缺失角色。
     */
    static void validateRelationship(String entityNo, RelationshipFacts relationship) {
        if (relationship == null) {
            return;
        }
        if (EnumUtils.getEnumByCode(FundFlow.class, relationship.fundFlow()) == null
            || (relationship.effectiveModelVersion() != 0 && relationship.effectiveModelVersion() != 1)
            || relationship.snapshotModelVersion() <= 0 || relationship.bindings() == null
            || relationship.bindings().isEmpty()) {
            throw new CheckoutUpstreamProtocolException("commercial 商业关系快照不完整");
        }
        Set<String> roles = new HashSet<>();
        for (var binding : relationship.bindings()) {
            if (!validBinding(binding) || !roles.add(binding.role())) {
                throw new CheckoutUpstreamProtocolException("commercial 商业关系角色绑定无效或重复");
            }
        }
        if (!roles.containsAll(Set.of(RelationshipRole.CLIENT.getCode(), RelationshipRole.SELLER.getCode(),
            RelationshipRole.ACQUIRING_OE.getCode())) || !acquiringEntityMatches(entityNo, relationship.bindings())) {
            throw new CheckoutUpstreamProtocolException("commercial 商业关系缺少必需角色或收单 OE 不一致");
        }
    }

    /** 单行绑定：Code 已发布；PLATFORM 主体无标识，其余主体标识非空且不超过 64 字符；Party 为正数或空。 */
    private static boolean validBinding(RoleBindingFacts binding) {
        if (binding == null || EnumUtils.getEnumByCode(RelationshipRole.class, binding.role()) == null) {
            return false;
        }
        var subjectType = EnumUtils.getEnumByCode(RelationshipSubjectType.class, binding.subjectType());
        if (subjectType == null || (binding.partyId() != null && binding.partyId() <= 0)) {
            return false;
        }
        if (subjectType == RelationshipSubjectType.PLATFORM) {
            return binding.subjectId() == null;
        }
        return text(binding.subjectId(), 64);
    }

    /** ACQUIRING_OE 必须是 OPERATING_ENTITY 主体且编号等于本次冻结的 OE。 */
    private static boolean acquiringEntityMatches(String entityNo, List<RoleBindingFacts> bindings) {
        return bindings.stream().filter(binding -> RelationshipRole.ACQUIRING_OE.getCode().equals(binding.role()))
            .allMatch(binding -> RelationshipSubjectType.OPERATING_ENTITY.getCode().equals(binding.subjectType())
                && entityNo.equals(binding.subjectId()));
    }

    /** Finance 回执必须属于原订单、OE、国家和币种，逐行净额及税额完整对齐。 */
    private void validateTaxLines(CommercialFacts facts, TaxFacts tax) {
        var s = request;
        if (tax.orderId() != s.getOrderId() || !facts.entityNo().equals(tax.entityNo())
            || !s.getCountry().equals(tax.country()) || !s.getCurrency().equals(tax.currency())
            || tax.netAmount() == null || s.getNetAmount().compareTo(tax.netAmount()) != 0 || tax.lines() == null
            || tax.lines().size() != s.getItems().size() || tax.lines().isEmpty()) {
            throw new CheckoutUpstreamProtocolException("Finance 税回执归属或行数不一致");
        }
        var lines = new java.util.HashMap<String, TaxLine>();
        BigDecimal sum = BigDecimal.ZERO;
        for (var line : tax.lines()) {
            if (line == null || lines.put(line.clientItemId(), line) != null || line.netAmount() == null
                || line.taxAmount() == null || line.totalAmount() == null || line.taxAmount().signum() < 0
                || !money(line.taxAmount())
                || line.netAmount().add(line.taxAmount()).compareTo(line.totalAmount()) != 0) {
                throw new CheckoutUpstreamProtocolException("Finance 税明细不完整");
            }
            sum = sum.add(line.taxAmount());
        }
        for (var item : s.getItems()) {
            var line = lines.get(item.clientItemId());
            if (line == null || item.lineNetAmount().compareTo(line.netAmount()) != 0) {
                throw new CheckoutUpstreamProtocolException("Finance 税明细与原商品行不一致");
            }
        }
        if (sum.compareTo(tax.taxAmount()) != 0) {
            throw new CheckoutUpstreamProtocolException("Finance 行税额与汇总不一致");
        }
    }

    /** MVP USD 金额精度沿当前 Client 创建合同，包含有符号舍入额。 */
    private static boolean money(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 2 && value.abs().precision() - value.abs().scale() <= 16;
    }

    /** 文本完整性只校验 Owner 协议，不推导缺失的法律事实。 */
    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    /** 文件身份与不可变内容摘要必须成组存在。 */
    private static boolean file(FileRef file) {
        return file != null && file.documentId() > 0 && file.fileId() > 0 && hash(file.contentHash());
    }

    /** 摘要使用小写十六进制 SHA-256。 */
    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
