package com.paytech.acquiring.configuration;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.CommercialFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.FileRef;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.RelationshipFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.RoleBindingFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.Seller;
import com.paytech.acquiring.domain.enums.EntitySourceType;
import com.paytech.acquiring.domainservice.port.customer.CheckoutCommercialPort;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundFlow;
import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.commercial.api.enums.RelationshipSubjectType;

/**
 * 本轮明确授权的固定商业 Mock（acquiring.commercial.resolution-mode=MOCK，缺省即 MOCK）；仅用于 mor-test 种子就绪前的流程联通，不代表真实
 * commercial 解析
 * <li>固定 AE001，来源 PARTNER（平台 PayFac Partner 提供），Partner 合同字段为固定占位 ID。</li>
 * <li>按 MOR 合同给出与 commercial RelationshipSnapshot.derive 一致的 8 行角色绑定（tax 需求 technique S-03）：卖方、收单 OE、
 * 资金托管与 TAX_RESPONSIBLE 都是 AE001（Party {@value #AE001_PARTY_ID}），使结账走真实算税；生效模型版本记 0（只记录，下游沿用旧字段）。</li>
 * <li>REAL 时本配置不生效，端口由 {@link CommercialClientConfiguration} 提供。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = CommercialMockConfiguration.RESOLUTION_MODE, havingValue = "MOCK", matchIfMissing = true)
public class CommercialMockConfiguration {
    /** 商业解析模式配置键：MOCK（默认）使用本固定 Mock，REAL 调用 commercial；修改后需重启。 */
    public static final String RESOLUTION_MODE = "acquiring.commercial.resolution-mode";

    /** 固定 OE。 */
    static final String ENTITY_NO = "AE001";

    /** mor-test 演示 OE AE001 的 client-master 法律 Party，与 tax 演示 market / registration 的纳税主体一致。 */
    static final long AE001_PARTY_ID = 880000000702L;

    /** 固定来源 Local Partner。 */
    static final long LOCAL_PARTNER_ID = 107L;

    /** 固定平台签约 Party（与 platformServicePartyId 相同的占位）。 */
    static final long PLATFORM_PARTY_ID = 110L;

    /** 固定 AE001 + PARTNER 来源、MOR 合同的商业事实与商业关系快照。 */
    @Bean
    CheckoutCommercialPort checkoutCommercialPort() {
        return request -> CommercialFacts.builder().entityNo(ENTITY_NO)
            .entitySourceType(EntitySourceType.PARTNER.getCode()).localPartnerId(LOCAL_PARTNER_ID).industryCode("RETAIL")
            .mccCode("5999").affiliationId(101L).entityScopeId(102L).entitySourceRelationId(103L)
            .clientPricingVersionId(104L).partnerShareVersionId(108L).platformChargeVersionId(109L)
            .platformServicePartyId(PLATFORM_PARTY_ID).clientAgreementId(105L).partnerAgreementId(111L)
            .partnerAgreementOperatingEntityId(112L).sellerDisclosureId(106L)
            .seller(new Seller("Mock Operating Entity", "MOCK-REG", null, "Mock seller address",
                "support@example.invalid", null))
            .consumerTerms(new FileRef(201L, 301L, "a".repeat(64)))
            .refundPolicy(new FileRef(202L, 302L, "b".repeat(64)))
            .privacyNotice(new FileRef(203L, 303L, "c".repeat(64))).businessMode(BusinessMode.MOR.getCode())
            .relationship(new RelationshipFacts(0, 1, FundFlow.THROUGH_CUSTODIAN.getCode(),
                morBindings(request.getClientId())))
            .build();
    }

    /**
     * MOR 合同的角色绑定，按 RelationshipRole 声明顺序：卖方、收单 OE、资金托管、税务责任均为 AE001，OE 提供方与平台服务费付款方为
     * 来源 Local Partner，PLATFORM 为平台签约方；Client 与 Local Partner 没有可用 Party。
     *
     * @param clientId 建单 Client
     * @return 8 行绑定
     */
    static List<RoleBindingFacts> morBindings(long clientId) {
        var entity = new RoleBindingFacts(RelationshipRole.ACQUIRING_OE.getCode(),
            RelationshipSubjectType.OPERATING_ENTITY.getCode(), ENTITY_NO, AE001_PARTY_ID);
        var provider = new RoleBindingFacts(RelationshipRole.OE_PROVIDER.getCode(),
            RelationshipSubjectType.LOCAL_PARTNER.getCode(), Long.toString(LOCAL_PARTNER_ID), null);
        return List.of(
            new RoleBindingFacts(RelationshipRole.CLIENT.getCode(), RelationshipSubjectType.CLIENT.getCode(),
                Long.toString(clientId), null),
            as(entity, RelationshipRole.SELLER), entity, provider, as(entity, RelationshipRole.FUNDS_CUSTODIAN),
            as(entity, RelationshipRole.TAX_RESPONSIBLE), as(provider, RelationshipRole.PLATFORM_FEE_PAYER),
            new RoleBindingFacts(RelationshipRole.PLATFORM.getCode(), RelationshipSubjectType.PLATFORM.getCode(), null,
                PLATFORM_PARTY_ID));
    }

    /** 同一主体换一个角色。 */
    private static RoleBindingFacts as(RoleBindingFacts source, RelationshipRole role) {
        return new RoleBindingFacts(role.getCode(), source.subjectType(), source.subjectId(), source.partyId());
    }
}
