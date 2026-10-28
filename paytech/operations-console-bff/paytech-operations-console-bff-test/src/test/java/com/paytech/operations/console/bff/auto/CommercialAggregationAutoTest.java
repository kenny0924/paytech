package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.service.processor.CommercialAgreementOverviewProcessor;
import com.paytech.operations.console.bff.service.processor.CommercialTermWorkspaceProcessor;
import com.paytech.operations.console.bff.test.base.CommercialBrowserAutoTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 商务页面聚合：合同详情（合同 + 当前 / 已排期条款版本）与条款版本编辑页（版本 + 合同范围 + 全部 OE 价格）
 * <li>经真实 BFF 入口与真实 commercial 客户端执行，commercial 由 Loopback Simulator 返回配置的 DTO。</li>
 *
 * @see CommercialAgreementOverviewProcessor
 * @see CommercialTermWorkspaceProcessor
 * @author Claude
 */
class CommercialAggregationAutoTest extends CommercialBrowserAutoTestBase {
    private static final String AGREEMENT_ID = "7319000000000000101";
    private static final String PARTNER_AGREEMENT_ID = "7319000000000000102";
    private static final String CLIENT_ID = "7300000000000000011";
    private static final String VERSION_ID = "7319000000000000601";
    private static final String DETAIL_ROUTE = "operations-commercial-agreement-detail";
    private static final String WORKSPACE_ROUTE = "operations-commercial-term-version-detail";
    private static final String TEMPLATE_PLAN_ID = "7319000000000000551";

    /** ACTIVE 的 CLIENT 合同：合同详情原样返回，费率卡只保留本合同的版本概要（丢弃价格块与其他合同）。 */
    @Test
    void test_normalFlow_clientAgreementWithTermVersions() throws Exception {
        commercial.reply("GET", "/agreements/v1/" + AGREEMENT_ID, 200, JSON.writeValueAsString(agreement(AGREEMENT_ID, "CLIENT", "ACTIVE")));
        commercial.reply("GET", "/rate-cards/v1?clientId=" + CLIENT_ID, 200, JSON.writeValueAsString(rateCard()));

        var response = send("GET", "/commercial-admin/v1/agreements/" + AGREEMENT_ID, DETAIL_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(agreement(AGREEMENT_ID, "CLIENT", "ACTIVE"), body.path("agreement")),
            () -> assertEquals("AVAILABLE", body.path("termVersionsStatus").asString()),
            () -> assertEquals(2, body.path("termVersions").size()),
            () -> assertEquals("CURRENT", body.path("termVersions").get(0).path("timing").asString()),
            () -> assertEquals("7319000000000000602", body.path("termVersions").get(1).path("versionId").asString()),
            () -> assertTrue(body.path("termVersions").get(0).path("capabilities").isMissingNode(), "版本概要不含价格块"),
            () -> assertEquals(List.of("/agreements/v1/" + AGREEMENT_ID, "/rate-cards/v1"),
                commercial.requests().stream().map(request -> request.path()).toList()));
    }

    /** PARTNER 合同不在费率卡范围内：不读取费率卡，版本部分标为 NOT_APPLICABLE。 */
    @Test
    void test_edgeCase_partnerAgreementSkipsRateCard() throws Exception {
        commercial.reply("GET", "/agreements/v1/" + PARTNER_AGREEMENT_ID, 200,
            JSON.writeValueAsString(agreement(PARTNER_AGREEMENT_ID, "PARTNER", "ACTIVE")));

        var response = send("GET", "/commercial-admin/v1/agreements/" + PARTNER_AGREEMENT_ID, DETAIL_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals("NOT_APPLICABLE", body.path("termVersionsStatus").asString()),
            () -> assertEquals(0, body.path("termVersions").size()),
            () -> assertEquals(1, commercial.requests().size()));
    }

    /** 费率卡失败只把版本部分标为 UNAVAILABLE，合同仍返回；合同本身读取失败则整页失败。 */
    @Test
    void test_noDataFound_rateCardFailureDegradesOnlyVersions() throws Exception {
        commercial.reply("GET", "/agreements/v1/" + AGREEMENT_ID, 200, JSON.writeValueAsString(agreement(AGREEMENT_ID, "CLIENT", "ACTIVE")));
        commercial.problem("GET", "/rate-cards/v1", 503, "OWNER_UNAVAILABLE");

        var degraded = send("GET", "/commercial-admin/v1/agreements/" + AGREEMENT_ID, DETAIL_ROUTE, null);

        assertEquals(200, degraded.statusCode(), degraded.body());
        assertEquals("UNAVAILABLE", JSON.readTree(degraded.body()).path("termVersionsStatus").asString());

        resetBetweenCalls();
        commercial.problem("GET", "/agreements/v1/" + AGREEMENT_ID, 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        var missing = send("GET", "/commercial-admin/v1/agreements/" + AGREEMENT_ID, DETAIL_ROUTE, null);
        assertEquals(400, missing.statusCode(), missing.body());
        assertEquals(1, commercial.requests().size(), "合同读取失败不再读取费率卡");
    }

    /** 版本编辑页：按 PARTNER 合同覆盖的每个 OE 读取价格全集，合并后按 OE、币种、优先级排序。 */
    @Test
    void test_normalFlow_termWorkspaceMergesPricesOfEveryEntity() throws Exception {
        commercial.reply("GET", "/commercial-terms/v1/versions/" + VERSION_ID, 200, JSON.writeValueAsString(version()));
        commercial.reply("GET", "/agreements/v1/" + PARTNER_AGREEMENT_ID, 200,
            JSON.writeValueAsString(agreement(PARTNER_AGREEMENT_ID, "PARTNER", "ACTIVE")));
        String prices = "/commercial-term-versions/v1/" + VERSION_ID + "/prices?entityNo=%s&termType=PARTNER_SHARE";
        commercial.reply("GET", prices.formatted("SA001"), 200, JSON.writeValueAsString(prices("SA001", "SAR", "USD")));
        commercial.reply("GET", prices.formatted("AE001"), 200, JSON.writeValueAsString(prices("AE001", "USD", "AED")));

        var response = send("GET", "/commercial-admin/v1/commercial-terms/versions/" + VERSION_ID, WORKSPACE_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        var order = new java.util.ArrayList<String>();
        body.path("prices").forEach(price -> order.add(price.path("entityNo").asString() + "/" + price.path("currency").asString()));
        assertAll(() -> assertEquals(version(), body.path("version")),
            () -> assertEquals(PARTNER_AGREEMENT_ID, body.path("agreement").path("agreementId").asString()),
            () -> assertEquals(List.of("AE001", "SA001"), JSON.convertValue(body.path("agreement").path("entityNos"), List.class)),
            () -> assertEquals(1, body.path("agreement").path("capabilities").size()),
            () -> assertTrue(body.path("agreement").path("clientId").isNull()),
            () -> assertEquals(List.of("AE001/AED", "AE001/USD", "SA001/SAR", "SA001/USD"), order),
            () -> assertEquals(4, commercial.requests().size()));
    }

    /** 模板版本编辑页：不读取合同，按模板方案列表取得模板 OE，只读取该 OE 的价格；agreement 为 null，template 为模板方案。 */
    @Test
    void test_normalFlow_templateWorkspaceReadsTemplateEntityOnly() throws Exception {
        commercial.reply("GET", "/commercial-terms/v1/versions/" + VERSION_ID, 200, JSON.writeValueAsString(templateVersion()));
        commercial.reply("GET", "/commercial-terms/v1/templates?termType=CLIENT_PRICING", 200, JSON.writeValueAsString(templates()));
        commercial.reply("GET", "/commercial-term-versions/v1/" + VERSION_ID + "/prices?entityNo=AE001&termType=CLIENT_PRICING", 200,
            JSON.writeValueAsString(prices("AE001", "AED", "USD").put("termType", "CLIENT_PRICING")));

        var response = send("GET", "/commercial-admin/v1/commercial-terms/versions/" + VERSION_ID, WORKSPACE_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(templateVersion(), body.path("version")),
            () -> assertTrue(body.path("agreement").isNull()),
            () -> assertEquals(TEMPLATE_PLAN_ID, body.path("template").path("planId").asString()),
            () -> assertEquals("AE001", body.path("template").path("entityNo").asString()),
            () -> assertEquals(2, body.path("prices").size()),
            () -> assertEquals(List.of("/commercial-terms/v1/versions/" + VERSION_ID, "/commercial-terms/v1/templates",
                "/commercial-term-versions/v1/" + VERSION_ID + "/prices"),
                commercial.requests().stream().map(request -> request.path()).toList()));
    }

    /** 模板版本的方案不在模板列表中：Owner 回执自相矛盾，按上游无效响应 502，不读取价格。 */
    @Test
    void test_edgeCase_templateWorkspaceWithoutTemplatePlan() throws Exception {
        commercial.reply("GET", "/commercial-terms/v1/versions/" + VERSION_ID, 200,
            JSON.writeValueAsString(templateVersion().put("planId", "7319000000000000599")));
        commercial.reply("GET", "/commercial-terms/v1/templates?termType=CLIENT_PRICING", 200, JSON.writeValueAsString(templates()));

        var response = send("GET", "/commercial-admin/v1/commercial-terms/versions/" + VERSION_ID, WORKSPACE_ROUTE, null);

        assertEquals(502, response.statusCode(), response.body());
        assertEquals(2, commercial.requests().size());
    }

    /** 任一 OE 的价格读取失败整页失败，不返回缺 OE 的矩阵。 */
    @Test
    void test_businessRuleViolation_termWorkspaceFailsWhenAnyEntityFails() throws Exception {
        commercial.reply("GET", "/commercial-terms/v1/versions/" + VERSION_ID, 200, JSON.writeValueAsString(version()));
        commercial.reply("GET", "/agreements/v1/" + PARTNER_AGREEMENT_ID, 200,
            JSON.writeValueAsString(agreement(PARTNER_AGREEMENT_ID, "PARTNER", "ACTIVE")));
        String prices = "/commercial-term-versions/v1/" + VERSION_ID + "/prices?entityNo=%s&termType=PARTNER_SHARE";
        commercial.reply("GET", prices.formatted("AE001"), 200, JSON.writeValueAsString(prices("AE001", "USD")));
        commercial.problem("GET", "/commercial-term-versions/v1/" + VERSION_ID + "/prices", 404, CommonErrorCode.NOT_FOUND.getCode());

        var response = send("GET", "/commercial-admin/v1/commercial-terms/versions/" + VERSION_ID, WORKSPACE_ROUTE, null);

        assertEquals(404, response.statusCode(), response.body());
    }

    private static ObjectNode agreement(String agreementId, String type, String status) {
        boolean client = "CLIENT".equals(type);
        var node = JSON.createObjectNode().put("agreementId", agreementId).put("agreementNo", "AGR-" + agreementId)
            .put("agreementName", "示例合同").put("agreementType", type)
            .put("businessMode", client ? "MOR" : null).put("fundMode", client ? "SETTLE_THROUGH_PLATFORM" : null)
            .put("pricingAuthority", client ? "PLATFORM" : null).putNull("referralPartnerId")
            .put("clientId", client ? CLIENT_ID : null).put("localPartnerId", client ? null : "7300000000000000021")
            .put("entityNo", client ? "AE001" : null).put("status", status)
            .put("effectiveFrom", "2026-10-01T00:00:00Z").putNull("effectiveTo")
            .put("signedTime", "2026-10-02T00:00:00Z").put("activatedTime", "2026-10-03T00:00:00Z").put("changeVersion", 7);
        ArrayNode entities = node.putArray("operatingEntityNos");
        if (!client) {
            entities.add("SA001").add("AE001");
        }
        node.putArray("parties").addObject().put("agreementPartyId", "7319000000000000221").put("partyId", "7319000000000000201")
            .put("partyProfileId", "7319000000000000202").put("partyRole", client ? "CLIENT" : "LOCAL_PARTNER")
            .put("acceptanceMethod", "E_SIGNATURE").putNull("acceptedByActorId").put("acceptedAt", "2026-10-02T00:00:00Z")
            .put("acceptanceReference", "ESIGN-1").put("evidenceDocumentId", "7319000000000000231");
        node.putArray("documents").addObject().put("agreementDocumentId", "7319000000000000231").put("documentType", "SIGNING_DOCUMENT")
            .put("fileId", "7319000000000000701").put("contentHash", "a".repeat(64));
        node.putArray("productCodes").add("ACQUIRING");
        var capability = node.putArray("capabilities").addObject().put("agreementCapabilityId", "7319000000000000301")
            .put("entityNo", "AE001").put("capabilityCode", "CARD_VISA").put("status", "ACTIVE")
            .put("executionStatus", "ENABLED")
            .put("effectiveFrom", "2026-10-01T00:00:00Z").putNull("effectiveTo").putNull("rollingReserveRate").putNull("rollingReserveDays");
        capability.putArray("conditions").addObject().put("dimensionCode", "CURRENCY").putArray("values").add("USD");
        capability.put("require3ds", false);
        return node;
    }

    private static JsonNode rateCard() {
        var card = JSON.createObjectNode().put("clientId", CLIENT_ID).put("asOf", "2026-10-19T00:00:00Z").putNull("currency");
        var agreements = card.putArray("agreements");
        agreements.add(rateCardAgreement("7319000000000000100", "7319000000000000590"));
        var own = rateCardAgreement(AGREEMENT_ID, "7319000000000000601");
        ((ArrayNode) own.path("termVersions")).add(termVersion("7319000000000000602", "SCHEDULED", "SCHEDULED"));
        agreements.add(own);
        return card;
    }

    private static ObjectNode rateCardAgreement(String agreementId, String versionId) {
        var agreement = JSON.createObjectNode().put("agreementId", agreementId).put("agreementNo", "AGR-" + agreementId)
            .put("entityNo", "AE001").put("businessMode", "MOR").put("fundMode", "SETTLE_THROUGH_PLATFORM")
            .put("pricingAuthority", "PLATFORM").put("effectiveFrom", "2026-10-01T00:00:00Z").putNull("effectiveTo");
        agreement.putArray("products").add("ACQUIRING");
        agreement.putArray("capabilities");
        agreement.putArray("termVersions").add(termVersion(versionId, "ACTIVE", "CURRENT"));
        return agreement;
    }

    private static ObjectNode termVersion(String versionId, String status, String timing) {
        var version = JSON.createObjectNode().put("planId", "7319000000000000501").put("termType", "CLIENT_PRICING")
            .put("versionId", versionId).put("versionNo", "1").put("status", status).put("timing", timing)
            .put("effectiveFrom", "2026-10-01T00:00:00Z").putNull("effectiveTo").put("pricingModel", "BLENDED")
            .put("feeTaxTreatment", "EXCLUSIVE");
        version.putArray("capabilities").addObject().put("agreementCapabilityId", "7319000000000000301")
            .put("capabilityCode", "CARD_VISA").putArray("currencies");
        return version;
    }

    private static ObjectNode version() {
        var version = JSON.createObjectNode().put("versionId", VERSION_ID).put("planId", "7319000000000000502")
            .put("termType", "PARTNER_SHARE").put("agreementId", PARTNER_AGREEMENT_ID).put("versionNo", "1")
            .put("status", "DRAFT").put("effectiveFrom", "2026-10-19T08:00:00Z").putNull("effectiveTo")
            .put("roundingMode", "HALF_UP").put("pricingModel", "BLENDED").put("feeTaxTreatment", "EXCLUSIVE")
            .put("changeVersion", "3").put("ownerType", CommercialTermOwnerType.LOCAL_PARTNER.getCode())
            .putNull("sourceTemplateVersionId").put("signatureExempt", false).put("belowStandardCount", 0)
            .putNull("templateUpdate").putNull("acceptance");
        version.putArray("relevanceWarnings");
        return version;
    }

    /** 标准价目模板版本：没有合同，Owner 类型为 PLATFORM_TEMPLATE。 */
    private static ObjectNode templateVersion() {
        var version = version().put("planId", TEMPLATE_PLAN_ID).put("termType", "CLIENT_PRICING")
            .put("ownerType", CommercialTermOwnerType.PLATFORM_TEMPLATE.getCode());
        version.putNull("agreementId");
        return version;
    }

    /** 模板方案列表：另一套模板与本模板（AE001）。 */
    private static ObjectNode templates() {
        var body = JSON.createObjectNode();
        var templates = body.putArray("templates");
        for (String[] plan : new String[][] {{"7319000000000000550", "SA001", "标准价 · SA001"},
            {TEMPLATE_PLAN_ID, "AE001", "标准价 · AE001"}}) {
            templates.addObject().put("planId", plan[0]).put("planCode", "TPL-" + plan[1]).put("templateName", plan[2])
                .put("termType", "CLIENT_PRICING").put("entityNo", plan[1]).put("status", "ACTIVE").put("changeVersion", "3")
                .putNull("activeVersionId").putNull("activeVersionNo").put("latestVersionId", VERSION_ID)
                .put("latestVersionNo", "1").put("latestVersionStatus", "DRAFT").put("createTime", "2026-10-20T00:00:00Z")
                .put("updateTime", "2026-10-20T00:00:00Z");
        }
        return body;
    }

    private static ObjectNode prices(String entityNo, String... currencies) {
        var body = JSON.createObjectNode().put("termVersionId", VERSION_ID).put("termType", "PARTNER_SHARE")
            .put("entityNo", entityNo);
        var prices = body.putArray("prices");
        int priority = 1;
        for (String currency : currencies) {
            var price = prices.addObject().put("termType", "PARTNER_SHARE").put("agreementId", PARTNER_AGREEMENT_ID)
                .put("planId", "7319000000000000502").put("termVersionId", VERSION_ID).put("entityNo", entityNo)
                .putNull("clientId").put("localPartnerId", "7300000000000000021")
                .put("priceId", "731900000000008" + ("AE001".equals(entityNo) ? "1" : "2") + priority).put("pricingProfileCode", "CARD_ANY")
                .put("paymentMethodCode", "CARD").put("priorityNo", priority++).put("currency", currency)
                .put("pricingModel", "BLENDED").put("feeTaxTreatment", "EXCLUSIVE").putNull("settlement");
            price.putArray("conditions");
            var component = price.putArray("components").addObject().put("componentId", "7319000000000009001")
                .put("componentNo", 1).put("componentCode", "SHARE").put("componentType", "PERCENTAGE")
                .put("triggerCode", "PAYMENT_CAPTURED").put("calculationBase", "ACTUAL_CLIENT_FEE").put("refundBehavior", "KEEP")
                .put("rate", "0.2").putNull("fixedAmount").putNull("currency").putNull("minAmount").putNull("maxAmount")
                .putNull("bandBasis").putNull("bandMode");
            component.putArray("bands");
        }
        return body;
    }
}
