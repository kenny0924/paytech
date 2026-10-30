package com.paytech.acquiring.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static com.paytech.acquiring.test.builder.external.CheckoutContextResponseBuilder.binding;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.paytech.acquiring.api.client.v1.checkout.ClientCheckoutApi;
import com.paytech.acquiring.api.client.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.acquiring.configuration.CommercialMockConfiguration;
import com.paytech.acquiring.domain.enums.EntitySourceType;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody.RoleBinding;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundFlow;
import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.commercial.api.enums.RelationshipSubjectType;
import com.paytech.acquiring.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker;

/**
 * 商业解析模式 MOCK（生产缺省）：固定 AE001 + PARTNER Mock，不调用 commercial；按 MOR 合同冻结 8 行商业关系快照，TAX_RESPONSIBLE 为
 * AE001 的法律 Party（tax 需求 technique S-03），建单按该主体向 tax 请求暂估
 * <li>以属性覆盖启动独立应用上下文，类结束后关闭，避免与 REAL 上下文的 MQ 监听并存。</li>
 * <li>期望值来自批准的固定 Mock 合同（AE001、PARTNER、占位 ID），由本类独立给出。</li>
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
@TestPropertySource(properties = CommercialMockConfiguration.RESOLUTION_MODE + "=MOCK")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CreateCheckoutMockResolutionTest extends AbstractCreateCheckoutApiTest {
    /** mor-test 演示 OE AE001 的法律 Party（固定 Mock 合同）。 */
    private static final String AE001_PARTY_ID = "880000000702";

    /** 第二层 BeforeEach：MOCK 模式只需 Risk 放行与 tax 响应；commercial 不应被调用，因此不配置解析 DTO。 */
    @BeforeEach
    void setUpClassMocks() {
        riskMocker.allow(CheckoutRiskMocker.DEFAULT_EVALUATION_ID);
        taxMocker.notManaged();
    }

    /** 固定 Mock 的 PARTNER 事实完整冻结；MOR 快照 8 行绑定、生效版本 0；tax 以 AE001 为纳税主体、MOR 业务模式请求暂估。 */
    @Test
    void test_mockResolution() {
        var response = invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90",
            "2.00", "42.90");

        checkoutChecker.assertCreated(clientId(), CLIENT_ORDER_ID, response.orderNo(), NOW, NOW.plusSeconds(1800));
        checkoutChecker.assertEntitySource(clientId(), CLIENT_ORDER_ID, EntitySourceType.PARTNER.getCode(), 107L);
        checkoutChecker.assertFrozenCommercial(clientId(), CLIENT_ORDER_ID,
            Map.ofEntries(Map.entry("affiliation_id", 101L), Map.entry("entity_scope_id", 102L),
                Map.entry("entity_source_relation_id", 103L), Map.entry("client_pricing_version_id", 104L),
                Map.entry("partner_share_version_id", 108L), Map.entry("platform_charge_version_id", 109L),
                Map.entry("platform_service_party_id", 110L), Map.entry("client_agreement_id", 105L),
                Map.entry("partner_agreement_id", 111L), Map.entry("partner_agreement_operating_entity_id", 112L),
                Map.entry("seller_disclosure_id", 106L), Map.entry("locale", "en"),
                Map.entry("public_legal_name", "Mock Operating Entity")));
        checkoutChecker.assertRelationship(clientId(), CLIENT_ORDER_ID, FundFlow.THROUGH_CUSTODIAN.getCode(), 0, 1,
            mockBindings());
        var taxRequest = taxMocker.requests().getFirst();
        assertEquals(AE001_PARTY_ID, taxRequest.getTaxSubjectPartyId());
        assertEquals(AE001_PARTY_ID, taxRequest.getSellerPartyId());
        assertEquals(BusinessMode.MOR.getCode(), taxRequest.getBusinessMode());
        preparationMocker.assertNoCalls();
        assertEquals(List.of(), cctTransactions.transactionStates(), "MOCK 模式不应有 commercial 出站请求");
        riskMocker.assertCalls(1);
        taxMocker.assertCalls(1);
    }

    /** 与 commercial RelationshipSnapshot.derive 的 MOR 推导一致；Client 与 Local Partner 无 Party，PLATFORM 为占位签约方 110。 */
    private List<RoleBinding> mockBindings() {
        var entity = "AE001";
        var partner = "107";
        return List.of(
            binding(RelationshipRole.CLIENT, RelationshipSubjectType.CLIENT, Long.toString(clientId()), null),
            binding(RelationshipRole.SELLER, RelationshipSubjectType.OPERATING_ENTITY, entity, AE001_PARTY_ID),
            binding(RelationshipRole.ACQUIRING_OE, RelationshipSubjectType.OPERATING_ENTITY, entity, AE001_PARTY_ID),
            binding(RelationshipRole.OE_PROVIDER, RelationshipSubjectType.LOCAL_PARTNER, partner, null),
            binding(RelationshipRole.FUNDS_CUSTODIAN, RelationshipSubjectType.OPERATING_ENTITY, entity, AE001_PARTY_ID),
            binding(RelationshipRole.TAX_RESPONSIBLE, RelationshipSubjectType.OPERATING_ENTITY, entity, AE001_PARTY_ID),
            binding(RelationshipRole.PLATFORM_FEE_PAYER, RelationshipSubjectType.LOCAL_PARTNER, partner, null),
            binding(RelationshipRole.PLATFORM, RelationshipSubjectType.PLATFORM, null, "110"));
    }
}
