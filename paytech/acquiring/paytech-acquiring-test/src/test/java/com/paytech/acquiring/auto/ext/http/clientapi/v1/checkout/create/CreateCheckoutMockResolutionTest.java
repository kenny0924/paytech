package com.paytech.acquiring.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.template;
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
import com.paytech.acquiring.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker;

/**
 * 商业解析模式 MOCK（生产缺省）：固定 AE001 + PARTNER Mock，不调用 commercial，不冻结商业关系快照
 * <li>以属性覆盖启动独立应用上下文，类结束后关闭，避免与 REAL 上下文的 MQ 监听并存。</li>
 * <li>期望值来自批准的固定 Mock 合同（AE001、PARTNER、占位 ID），由本类独立给出。</li>
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
@TestPropertySource(properties = CommercialMockConfiguration.RESOLUTION_MODE + "=MOCK")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CreateCheckoutMockResolutionTest extends AbstractCreateCheckoutApiTest {
    /** 第二层 BeforeEach：MOCK 模式只需 Risk 放行；commercial 不应被调用，因此不配置解析 DTO。 */
    @BeforeEach
    void setUpClassMocks() {
        riskMocker.allow(CheckoutRiskMocker.DEFAULT_EVALUATION_ID);
    }

    /** 固定 Mock 的 PARTNER 事实完整冻结；无快照：无绑定行、资金流向与快照版本为空、生效版本 0。 */
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
        checkoutChecker.assertRelationship(clientId(), CLIENT_ORDER_ID, null, 0, null, List.of());
        preparationMocker.assertNoCalls();
        assertEquals(List.of(), cctTransactions.transactionStates(), "MOCK 模式不应有 commercial 出站请求");
        riskMocker.assertCalls(1);
    }
}
