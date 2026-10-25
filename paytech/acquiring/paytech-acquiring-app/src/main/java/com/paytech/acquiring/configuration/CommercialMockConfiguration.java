package com.paytech.acquiring.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.CommercialFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.FileRef;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.Seller;
import com.paytech.acquiring.domain.enums.EntitySourceType;
import com.paytech.acquiring.domainservice.port.customer.CheckoutCommercialPort;

/**
 * 本轮明确授权的固定商业 Mock（acquiring.commercial.resolution-mode=MOCK，缺省即 MOCK）；仅用于 mor-test 种子就绪前的流程联通，不代表真实
 * commercial 解析
 * <li>固定 AE001，来源 PARTNER（平台 PayFac Partner 提供），Partner 合同字段为固定占位 ID。</li>
 * <li>不提供商业关系快照：订单不写角色绑定，fund_flow 与 snapshot_model_version 为空，生效版本记 0。</li>
 * <li>REAL 时本配置不生效，端口由 {@link CommercialClientConfiguration} 提供。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = CommercialMockConfiguration.RESOLUTION_MODE, havingValue = "MOCK", matchIfMissing = true)
public class CommercialMockConfiguration {
    /** 商业解析模式配置键：MOCK（默认）使用本固定 Mock，REAL 调用 commercial；修改后需重启。 */
    public static final String RESOLUTION_MODE = "acquiring.commercial.resolution-mode";

    /** 固定 AE001 + PARTNER 来源的商业事实。 */
    @Bean
    CheckoutCommercialPort checkoutCommercialPort() {
        return request -> CommercialFacts.builder().entityNo("AE001")
            .entitySourceType(EntitySourceType.PARTNER.getCode()).localPartnerId(107L).industryCode("RETAIL")
            .mccCode("5999").affiliationId(101L).entityScopeId(102L).entitySourceRelationId(103L)
            .clientPricingVersionId(104L).partnerShareVersionId(108L).platformChargeVersionId(109L)
            .platformServicePartyId(110L).clientAgreementId(105L).partnerAgreementId(111L)
            .partnerAgreementOperatingEntityId(112L).sellerDisclosureId(106L)
            .seller(new Seller("Mock Operating Entity", "MOCK-REG", null, "Mock seller address",
                "support@example.invalid", null))
            .consumerTerms(new FileRef(201L, 301L, "a".repeat(64)))
            .refundPolicy(new FileRef(202L, 302L, "b".repeat(64)))
            .privacyNotice(new FileRef(203L, 303L, "c".repeat(64))).build();
    }
}
