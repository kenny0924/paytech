package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_CONTRACT_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_PRICING_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.CommercialPriceErrorCode;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.ext.rest.CommercialPricePreviewController;
import com.paytech.operations.console.bff.test.base.CommercialBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.CommercialOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 商务工作台、费率卡预览与价格试算：经真实 BFF 入口与真实 commercial 客户端执行，commercial 由 Loopback Simulator 返回配置的 DTO
 * <li>试算覆盖命中转发、示例费用（百分比 + 下限、固定、WHOLE_BASE、PROGRESSIVE、不可计算组件）、命中失败原因透传与本地拒绝。</li>
 * <li>费率卡的原样转发由 CommercialBrowserAutoTest 的边界清单 R01 覆盖，这里只覆盖回执身份不一致；工作台数据见 CommercialConsoleQueryAutoTest。</li>
 *
 * @see CommercialPricePreviewController
 * @author Claude
 */
class CommercialPricePreviewAutoTest extends CommercialBrowserAutoTestBase {
    private static final String SIMULATE_PATH = "/commercial-admin/v1/commercial-prices/simulate";
    private static final String SIMULATE_ROUTE = "operations-commercial-price-simulate";
    private static final String RESOLUTION_PATH = "/commercial-prices/v1/resolutions";
    private static final String DASHBOARD_PATH = "/commercial-admin/v1/dashboard";
    private static final String DASHBOARD_ROUTE = "operations-commercial-dashboard";
    private static final String RATE_CARD_ROUTE = "operations-commercial-rate-card";
    private static final String CLIENT_ID = "7300000000000000011";
    private static final String VERSION_ID = "7319000000000000601";
    private static final String AGREEMENT_ID = "7319000000000000101";
    private static final String CAPTURED = "PAYMENT_CAPTURED";
    private static final String REFUNDED = "PAYMENT_REFUNDED";
    private static final String DISPUTED = "DISPUTE_OPENED";

    /** 命中价格原样返回；示例金额 1000.00 USD 下各组件按口径计算，事件合计只加已计算组件。 */
    @Test
    void test_normalFlow() throws Exception {
        commercial.reply("POST", RESOLUTION_PATH, 200, JSON.writeValueAsString(price()));

        var response = send("POST", SIMULATE_PATH, SIMULATE_ROUTE, simulation("1000.00"));

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        var events = body.path("illustration").path("feeEvents");
        assertAll(() -> assertEquals(price(), body.path("price")),
            () -> assertEquals("1000.00", body.path("illustration").path("amount").asString()),
            () -> assertEquals(2, body.path("illustration").path("fractionDigits").asInt()),
            () -> assertEquals(List.of(DISPUTED, CAPTURED, REFUNDED), names(events)),
            () -> assertEquals("20.00", component(events, DISPUTED, 0).path("amount").asString()),
            () -> assertEquals("累进 2 档", component(events, DISPUTED, 0).path("note").asString()),
            () -> assertFalse(component(events, DISPUTED, 1).path("computed").asBoolean()),
            () -> assertEquals("20.00", event(events, DISPUTED).path("totalAmount").asString()),
            () -> assertEquals("29.00", component(events, CAPTURED, 0).path("amount").asString()),
            () -> assertEquals("29.00000000", component(events, CAPTURED, 0).path("rawAmount").asString()),
            () -> assertEquals("0.30", component(events, CAPTURED, 1).path("amount").asString()),
            () -> assertEquals("29.30", event(events, CAPTURED).path("totalAmount").asString()),
            () -> assertEquals("20.00", component(events, REFUNDED, 0).path("amount").asString()),
            () -> assertEquals("命中第 2 档", component(events, REFUNDED, 0).path("note").asString()));
        assertForwardedResolution(body);
    }

    /** 小额命中最低金额、WHOLE_BASE 落在首档；无示例金额时只返回命中价格。 */
    @Test
    void test_edgeCase() throws Exception {
        commercial.reply("POST", RESOLUTION_PATH, 200, JSON.writeValueAsString(price()));
        var small = JSON.readTree(send("POST", SIMULATE_PATH, SIMULATE_ROUTE, simulation("10")).body());
        var events = small.path("illustration").path("feeEvents");
        assertAll(() -> assertEquals("0.50", component(events, CAPTURED, 0).path("amount").asString()),
            () -> assertEquals("按最低金额 0.50", component(events, CAPTURED, 0).path("note").asString()),
            () -> assertEquals("0.80", event(events, CAPTURED).path("totalAmount").asString()),
            () -> assertEquals("0.30", component(events, REFUNDED, 0).path("amount").asString()),
            () -> assertEquals("命中第 1 档", component(events, REFUNDED, 0).path("note").asString()),
            () -> assertEquals("0.30", component(events, DISPUTED, 0).path("amount").asString()));

        resetBetweenCalls();
        commercial.reply("POST", RESOLUTION_PATH, 200, JSON.writeValueAsString(price()));
        var withoutAmount = send("POST", SIMULATE_PATH, SIMULATE_ROUTE, simulation(null));
        assertEquals(200, withoutAmount.statusCode(), withoutAmount.body());
        assertTrue(JSON.readTree(withoutAmount.body()).path("illustration").isNull());
    }

    /** 命中失败：422 无命中 / 事实缺失与 409 歧义保留 commercial 错误码，Owner 原因经 Foundation 开关到达浏览器。 */
    @Test
    void test_businessRuleViolation() throws Exception {
        for (var error : List.of(CommercialPriceErrorCode.PRICE_NOT_FOUND, CommercialPriceErrorCode.PRICING_FACTS_INCOMPLETE,
            CommercialPriceErrorCode.INVALID_PRICING_FACT, CommercialPriceErrorCode.PRICE_AMBIGUOUS)) {
            int status = error == CommercialPriceErrorCode.PRICE_AMBIGUOUS ? 409 : 422;
            commercial.problem("POST", RESOLUTION_PATH, status, error.getCode());

            var response = send("POST", SIMULATE_PATH, SIMULATE_ROUTE, simulation("100"));

            assertEquals(status, response.statusCode(), response.body());
            var body = JSON.readTree(response.body());
            assertAll(() -> assertEquals(error.getCode(), body.path("code").asString()),
                () -> assertEquals(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, body.path("detail").asString()),
                () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
                () -> assertFalse(response.body().contains(CommercialOwnerSimulator.OWNER_REQUEST_ID)));
            resetBetweenCalls();
        }
    }

    /** commercial 422 字段错误只投影到试算请求体内存在的路径（paymentFactId 是 BFF 补足的字段，不外显）。 */
    @Test
    void test_validationPathsProjected() throws Exception {
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"%s","code":"%s",
             "requestId":"%s","timestamp":"2026-10-20T06:00:00.000000Z","errors":[
              {"field":"paymentFacts.dimensions","message":"x"},{"field":"paymentFactId","message":"y"}]}
            """.formatted(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, CommonErrorCode.VALIDATION_ERROR.getCode(),
            CommercialOwnerSimulator.OWNER_REQUEST_ID);
        commercial.reply("POST", RESOLUTION_PATH, 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", CommercialOwnerSimulator.OWNER_REQUEST_ID));

        var response = send("POST", SIMULATE_PATH, SIMULATE_ROUTE, simulation("100"));

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(1, body.path("errors").size()),
            () -> assertEquals("paymentFacts.dimensions", body.path("errors").get(0).path("field").asString()));
    }

    /** 缺少必填事实、非法示例金额、未知字段与 Query 在访问 commercial 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var missing = simulation("100");
        missing.remove("termVersionId");
        var response = send("POST", SIMULATE_PATH, SIMULATE_ROUTE, missing);
        assertRejectedLocally(422, response);
        assertEquals("termVersionId", JSON.readTree(response.body()).path("errors").get(0).path("field").asString());

        resetBetweenCalls();
        assertRejectedLocally(422, send("POST", SIMULATE_PATH, SIMULATE_ROUTE, simulation("1e3")));

        resetBetweenCalls();
        assertRejectedLocally(400, send("POST", SIMULATE_PATH, SIMULATE_ROUTE, simulation("100").put("paymentFactId", "9")));

        resetBetweenCalls();
        assertRejectedLocally(400, send("POST", SIMULATE_PATH + "?debug=1", SIMULATE_ROUTE, simulation("100")));

        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", "/commercial-admin/v1/rate-cards?clientId=0", RATE_CARD_ROUTE, null));

        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", "/commercial-admin/v1/rate-cards?clientId=" + CLIENT_ID + "&asOf=2026-10-20",
            RATE_CARD_ROUTE, null));
    }

    /** 试算与费率卡需要定价只读；工作台需要合同只读。 */
    @Test
    void test_authorizationDenied() throws Exception {
        platform.grantPermissions(COMMERCIAL_CONTRACT_READ_PERMISSION_ID);
        assertDenied(send("POST", SIMULATE_PATH, SIMULATE_ROUTE, simulation("100")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_CONTRACT_READ_PERMISSION_ID);
        assertDenied(send("GET", "/commercial-admin/v1/rate-cards?clientId=" + CLIENT_ID, RATE_CARD_ROUTE, null));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_READ_PERMISSION_ID);
        assertDenied(send("GET", DASHBOARD_PATH, DASHBOARD_ROUTE, null));
    }

    /** 费率卡回执的 Client 与请求不一致按上游无效响应 502 处理。 */
    @Test
    void test_rateCardIdentityMismatch() throws Exception {
        var card = (ObjectNode) row("R01").path("response").deepCopy();
        card.put("clientId", "7300000000000000099");
        commercial.reply("GET", "/rate-cards/v1", 200, JSON.writeValueAsString(card));

        var response = send("GET", "/commercial-admin/v1/rate-cards?clientId=" + CLIENT_ID, RATE_CARD_ROUTE, null);

        assertEquals(502, response.statusCode(), response.body());
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode(), JSON.readTree(response.body()).path("code").asString());
    }

    /** commercial 只收到一次命中请求：页面事实原样、补足试算追溯 ID、不带示例金额与操作人。 */
    private void assertForwardedResolution(JsonNode body) {
        assertEquals(1, commercial.requests().size(), commercial.requests().toString());
        var call = commercial.requests().getFirst();
        var forwarded = JSON.readTree(call.body());
        var expected = simulation("1000.00");
        expected.remove("amount");
        expected.put("paymentFactId", "1");
        assertAll(() -> assertEquals(RESOLUTION_PATH, call.path()), () -> assertNull(call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals("operations-console-bff", call.header("X-Caller-Service")),
            () -> assertEquals(expected, forwarded), () -> assertTrue(body.path("price").isObject()));
    }

    /** 试算请求：Client 条款、AE001、USD、Visa 信用卡事实；amount 为 null 时不带示例金额。 */
    private static ObjectNode simulation(String amount) {
        var request = JSON.createObjectNode().put("termVersionId", VERSION_ID).put("entityNo", "AE001")
            .put("agreementId", AGREEMENT_ID).put("termType", "CLIENT_PRICING").put("currency", "USD")
            .put("contextAsOfTime", "2026-10-20T00:00:00Z").put("clientId", CLIENT_ID).putNull("localPartnerId");
        var facts = request.putObject("paymentFacts").put("paymentMethodCode", "CARD");
        facts.putObject("dimensions").put(PricingDimension.CARD_BRAND.getCode(), "VISA")
            .putNull(PricingDimension.THREE_DS_RESULT.getCode());
        if (amount != null) {
            request.put("amount", amount);
        }
        return request;
    }

    /** 命中价格：捕获事件百分比 2.9%（最低 0.50）+ 固定 0.30；退款事件 WHOLE_BASE 阶梯；争议事件 PROGRESSIVE 阶梯与一个依赖实收费用的组件。 */
    private static ObjectNode price() {
        var price = JSON.createObjectNode().put("termType", "CLIENT_PRICING").put("agreementId", AGREEMENT_ID)
            .put("planId", "7319000000000000501").put("termVersionId", VERSION_ID).put("entityNo", "AE001")
            .put("clientId", CLIENT_ID).putNull("localPartnerId").put("priceId", "7319000000000000701")
            .put("pricingProfileCode", "CARD_VISA_STANDARD").put("paymentMethodCode", "CARD").put("priorityNo", 10)
            .put("currency", "USD").put("pricingModel", "BLENDED").put("feeTaxTreatment", "EXCLUSIVE")
            .putNull("sourceTemplatePriceId");
        price.putArray("conditions").addObject().put("dimensionCode", PricingDimension.CARD_BRAND.getCode())
            .put("conditionValue", "VISA");
        var components = price.putArray("components");
        component(components, 1, "MDR", "PERCENTAGE", CAPTURED, CalculationBase.GROSS_AMOUNT).put("rate", "0.029")
            .put("minAmount", "0.50");
        component(components, 2, "PER_TXN", "FIXED", CAPTURED, CalculationBase.FIXED_PER_EVENT).put("fixedAmount", "0.30");
        var whole = component(components, 3, "REFUND_TIER", "TIERED", REFUNDED, CalculationBase.GROSS_AMOUNT)
            .put("bandBasis", BandBasis.TRANSACTION_AMOUNT.getCode()).put("bandMode", "WHOLE_BASE");
        band(whole.withArray("bands"), 1, "0", "500", null, "0.30");
        band(whole.withArray("bands"), 2, "500", null, "0.02", null);
        var progressive = component(components, 4, "DISPUTE_TIER", "TIERED", DISPUTED, CalculationBase.GROSS_AMOUNT)
            .put("bandBasis", BandBasis.TRANSACTION_AMOUNT.getCode()).put("bandMode", "PROGRESSIVE");
        band(progressive.withArray("bands"), 1, "0", "500", "0.03", null);
        band(progressive.withArray("bands"), 2, "500", null, "0.01", null);
        component(components, 5, "DISPUTE_SHARE", "PERCENTAGE", DISPUTED, CalculationBase.ACTUAL_CLIENT_FEE)
            .put("rate", "0.1");
        price.putObject("settlement").put("settlementBusinessDays", 2).put("businessCalendarCode", "AE_BUSINESS_DAYS")
            .put("settlementTimezone", "Asia/Dubai").put("cutoffSecondOfDay", 0);
        return price;
    }

    private static ObjectNode component(ArrayNode components, int componentNo, String code, String type, String trigger,
        CalculationBase base) {
        return components.addObject().put("componentId", "73190000000000008" + componentNo + "1").put("componentNo", componentNo)
            .put("componentCode", code).put("componentType", type).put("triggerCode", trigger)
            .put("calculationBase", base.getCode()).put("refundBehavior", "RETURN_PRORATA").putNull("rate")
            .putNull("fixedAmount").put("currency", "USD").putNull("minAmount").putNull("maxAmount").putNull("bandBasis")
            .putNull("bandMode").putNull("benchmark").set("bands", JSON.createArrayNode());
    }

    private static void band(ArrayNode bands, int bandNo, String lower, String upper, String rate, String fixedAmount) {
        bands.addObject().put("bandId", "73190000000000009" + bandNo + "1").put("bandNo", bandNo).put("lowerBound", lower)
            .put("upperBound", upper).put("rate", rate).put("fixedAmount", fixedAmount).put("currency", "USD");
    }

    private static List<String> names(JsonNode events) {
        return events.valueStream().map(event -> event.path("feeEventCode").asString()).toList();
    }

    private static JsonNode event(JsonNode events, String feeEventCode) {
        return events.valueStream().filter(event -> feeEventCode.equals(event.path("feeEventCode").asString())).findFirst()
            .orElseThrow();
    }

    private static JsonNode component(JsonNode events, String feeEventCode, int index) {
        return event(events, feeEventCode).path("components").get(index);
    }
}
