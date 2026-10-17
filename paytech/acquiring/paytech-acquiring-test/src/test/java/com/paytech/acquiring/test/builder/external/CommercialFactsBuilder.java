package com.paytech.commerce.test.builder.external;

import com.paytech.commerce.domain.customer.CustomerCheckoutModels.*;

/** 准备测试独立定义 CCT DTO，不借用生产固定 Mock 生成期望。 */
public final class CommercialFactsBuilder {
    private CommercialFactsBuilder() {}

    public static CommercialFacts.CommercialFactsBuilder template() {
        return CommercialFacts.builder()
                .entityNo("AE001")
                .entitySourceType("PLATFORM")
                .industryCode("RETAIL")
                .mccCode("5999")
                .affiliationId(101L)
                .entityScopeId(102L)
                .entitySourceRelationId(103L)
                .clientPricingVersionId(104L)
                .clientAgreementId(105L)
                .sellerDisclosureId(106L)
                .seller(
                        new Seller(
                                "Test Seller",
                                "TEST-REG",
                                null,
                                "Test address",
                                "support@example.invalid",
                                null))
                .consumerTerms(new FileRef(201L, 301L, "a".repeat(64)))
                .refundPolicy(new FileRef(202L, 302L, "b".repeat(64)))
                .privacyNotice(new FileRef(203L, 303L, "c".repeat(64)));
    }
}
