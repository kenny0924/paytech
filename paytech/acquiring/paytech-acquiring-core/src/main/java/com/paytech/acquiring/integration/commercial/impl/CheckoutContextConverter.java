package com.paytech.acquiring.integration.commercial.impl;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.CapabilityFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.CommercialFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.FileRef;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.InitializationRequest;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.RelationshipFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.RoleBindingFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.Seller;
import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;

/**
 * commercial 结账上下文解析的 Wire 与收单商业事实之间的转换
 * <li>Wire 中的 ID 均为规范正十进制字符串，在此解析为数值；格式不符或超出 BIGINT 抛 {@link CheckoutUpstreamProtocolException}（502）。</li>
 * <li>只做结构转换，完整性与组合规则由 CheckoutInitializationContext.validateCommercial 统一校验。</li>
 */
final class CheckoutContextConverter {
    /** 规范正十进制 BIGINT 文本。 */
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");

    /** 商业时点：UTC、固定六位小数。 */
    private static final DateTimeFormatter CONTEXT_TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    /** 禁止实例化转换工具。 */
    private CheckoutContextConverter() {}

    /** 构造解析请求：不传支付方式（省略时 commercial 返回全部可用支付方式开通项），商业时点取订单创建时刻。 */
    static ResolveCheckoutContextRequestBody toRequest(InitializationRequest request) {
        var body = new ResolveCheckoutContextRequestBody();
        body.setClientId(Long.toString(request.getClientId()));
        body.setOrderNo(request.getOrderNo());
        body.setConsumerCountryCode(request.getCountry());
        body.setCurrency(request.getCurrency());
        body.setLocale(request.getLocale());
        body.setContextAsOfTime(CONTEXT_TIME.format(request.getContextTime()));
        return body;
    }

    /** 把解析响应转换为待冻结的商业事实；四个必需分组缺失视为协议错误。 */
    static CommercialFacts toFacts(ResolveCheckoutContextResponseBody body) {
        var classification = body.getClassification();
        var eligibility = body.getEligibility();
        var clientTerms = body.getClientTerms();
        var legal = body.getLegal();
        if (classification == null || eligibility == null || clientTerms == null || legal == null) {
            throw new CheckoutUpstreamProtocolException("commercial 结账上下文缺少必需分组");
        }
        var builder = CommercialFacts.builder().entityNo(body.getEntityNo()).entitySourceType(body.getSourceType())
            .industryCode(classification.getIndustryCode()).mccCode(classification.getMccCode())
            .affiliationId(id(eligibility.getAffiliationId())).entityScopeId(id(eligibility.getEntityScopeId()))
            .entitySourceRelationId(id(eligibility.getEntitySourceRelationId()))
            .clientPricingVersionId(id(clientTerms.getVersionId())).clientAgreementId(id(legal.getClientAgreementId()))
            .sellerDisclosureId(id(legal.getSellerDisclosureId())).seller(seller(legal.getSeller()))
            .consumerTerms(file(legal, legal.getConsumerTermsDocumentId()))
            .refundPolicy(file(legal, legal.getRefundPolicyDocumentId()))
            .privacyNotice(file(legal, legal.getPrivacyNoticeDocumentId()))
            .relationship(relationship(body.getRelationshipSnapshot()))
            .capabilities(capabilities(clientTerms.getCapabilities())).businessMode(clientTerms.getBusinessMode());
        var partner = body.getPartnerTerms();
        if (partner != null) {
            builder.localPartnerId(optionalId(partner.getLocalPartnerId()))
                .partnerShareVersionId(optionalId(partner.getShareVersionId()))
                .platformChargeVersionId(optionalId(partner.getServiceChargeVersionId()))
                .platformServicePartyId(optionalId(partner.getPlatformSignatoryPartyId()))
                .partnerAgreementId(optionalId(partner.getAgreementId()))
                .partnerAgreementOperatingEntityId(optionalId(partner.getAgreementOperatingEntityId()));
        }
        return builder.build();
    }

    /** 卖方公开文本原样透传；缺失时为空，由完整性校验拒绝。 */
    static Seller seller(ResolveCheckoutContextResponseBody.Seller seller) {
        if (seller == null) {
            return null;
        }
        return new Seller(seller.getLegalName(), seller.getRegistrationNumber(), seller.getTaxRegistrationNumber(),
            seller.getAddressText(), seller.getSupportEmail(), seller.getSupportPhone());
    }

    /** 按文档 ID 在 legal.documents 中取不可变文件证据；未引用或未找到时为空，由完整性校验拒绝。 */
    static FileRef file(ResolveCheckoutContextResponseBody.Legal legal, String documentId) {
        if (documentId == null || legal.getDocuments() == null) {
            return null;
        }
        for (var document : legal.getDocuments()) {
            if (document != null && documentId.equals(document.getDocumentId())) {
                return new FileRef(id(document.getDocumentId()), id(document.getFileId()), document.getContentHash());
            }
        }
        return null;
    }

    /** 商业关系快照原样转换；未返回时为空，订单按“只记录、生效版本 0、无绑定行”冻结。 */
    static RelationshipFacts relationship(ResolveCheckoutContextResponseBody.RelationshipSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        List<RoleBindingFacts> bindings = snapshot.getBindings() == null ? null : snapshot.getBindings().stream()
            .map(binding -> binding == null ? null : new RoleBindingFacts(binding.getRole(), binding.getSubjectType(),
                binding.getSubjectId(), optionalId(binding.getPartyId())))
            .toList();
        return new RelationshipFacts(snapshot.getEffectiveModelVersion(), snapshot.getSnapshotModelVersion(),
            snapshot.getFundFlow(), bindings);
    }

    /** 开通项只保留二期执行需要的字段；未返回时为空集合。 */
    static List<CapabilityFacts> capabilities(List<ResolveCheckoutContextResponseBody.Capability> capabilities) {
        if (capabilities == null) {
            return List.of();
        }
        return capabilities.stream().map(capability -> {
            if (capability == null) {
                throw new CheckoutUpstreamProtocolException("commercial 开通项为空");
            }
            return new CapabilityFacts(id(capability.getAgreementCapabilityId()), capability.getCapabilityCode(),
                capability.getCapabilityKind(), capability.getPaymentMethodCode(), capability.isRequire3ds(),
                conditions(capability.getConditions()));
        }).toList();
    }

    /** 接受条件按维度保留原顺序。 */
    static Map<String, List<String>> conditions(List<ResolveCheckoutContextResponseBody.CapabilityCondition> conditions) {
        if (conditions == null) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, List<String>>();
        for (var condition : conditions) {
            if (condition == null || condition.getDimensionCode() == null || condition.getValues() == null
                || condition.getValues().contains(null)) {
                throw new CheckoutUpstreamProtocolException("commercial 开通项条件不完整");
            }
            result.put(condition.getDimensionCode(), List.copyOf(condition.getValues()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** 必填 ID；缺失或格式不符视为协议错误。 */
    static long id(String value) {
        if (value == null || !POSITIVE_ID.matcher(value).matches()) {
            throw new CheckoutUpstreamProtocolException("commercial 结账上下文 ID 格式无效");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new CheckoutUpstreamProtocolException("commercial 结账上下文 ID 超出范围", overflow);
        }
    }

    /** 可空 ID；给出时必须合法。 */
    static Long optionalId(String value) {
        return value == null ? null : id(value);
    }
}
