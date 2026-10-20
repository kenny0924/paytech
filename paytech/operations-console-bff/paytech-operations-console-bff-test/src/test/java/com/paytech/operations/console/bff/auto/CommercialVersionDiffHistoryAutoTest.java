package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_CONTRACT_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_PRICING_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.BusinessChangeComparison;
import com.paytech.operations.console.bff.service.processor.CommercialBusinessChangeProcessor;
import com.paytech.operations.console.bff.service.processor.CommercialTermVersionDiffProcessor;
import com.paytech.operations.console.bff.test.base.CommercialBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.CommercialOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 第二切片 I2 的版本对比与变更历史：经真实 BFF 入口与真实 commercial 客户端执行，commercial 由 Loopback Simulator 返回配置的 DTO
 * <li>版本对比：按 OE + 币种 + 画像配对，新增 / 删除 / 修改（字段级，十进制按数值）/ 未变；不同方案 400；Owner 错误透传。</li>
 * <li>变更历史：本页内比较、向后回溯、首版与超出回溯上限四种比较结果；参数错误与 Owner 错误透传。</li>
 *
 * @see CommercialTermVersionDiffProcessor
 * @see CommercialBusinessChangeProcessor
 * @author Claude
 */
class CommercialVersionDiffHistoryAutoTest extends CommercialBrowserAutoTestBase {
    private static final String AGREEMENT_ID = "7319000000000000101";
    private static final String CLIENT_ID = "7300000000000000011";
    private static final String PLAN_ID = "7319000000000000501";
    private static final String BASE_ID = "7319000000000000601";
    private static final String TARGET_ID = "7319000000000000602";
    private static final String DIFF_ROUTE = "operations-commercial-term-version-diff";
    private static final String HISTORY = "/commercial-admin/v1/business-changes";
    private static final String HISTORY_ROUTE = "operations-commercial-business-change-list";
    private static final String OWNER_HISTORY = "/business-changes/v1";
    private static final String ROOT_QUERY = "aggregateRootType=AGREEMENT&aggregateRootId=" + AGREEMENT_ID;
    private static final String CAPTURE = "CAPTURE";

    /** 版本对比：一条修改（字段级）、一条未变（0.030 = 0.03）、一条新增、一条删除，版本口径的税务处理变化。 */
    @Test
    void test_normalFlow_versionDiff() throws Exception {
        replyVersions(PLAN_ID, PLAN_ID);
        commercial.reply("GET", "/commercial-term-versions/v1/" + BASE_ID + "/prices?entityNo=AE001&termType=CLIENT_PRICING", 200,
            JSON.writeValueAsString(prices(BASE_ID, basePrices())));
        commercial.reply("GET", "/commercial-term-versions/v1/" + TARGET_ID + "/prices?entityNo=AE001&termType=CLIENT_PRICING", 200,
            JSON.writeValueAsString(prices(TARGET_ID, targetPrices())));

        var response = send("GET", diffPath(BASE_ID, TARGET_ID), DIFF_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        var changed = body.path("changed").get(0);
        assertAll(() -> assertEquals(BASE_ID, body.path("base").path("versionId").asString()),
            () -> assertEquals(TARGET_ID, body.path("target").path("versionId").asString()),
            () -> assertEquals(List.of("feeTaxTreatment"), fields(body.path("versionDifferences"))),
            () -> assertEquals(List.of("APPLE_PAY_STD"), profiles(body.path("added"))),
            () -> assertEquals(List.of("CARD_VISA"), profiles(body.path("removed"))),
            () -> assertEquals("AED", body.path("removed").get(0).path("currency").asString()),
            () -> assertEquals(List.of("CARD_MC"), profiles(body.path("unchanged"))),
            () -> assertEquals(1, body.path("changed").size()),
            () -> assertEquals("CARD_VISA", changed.path("pricingProfileCode").asString()),
            () -> assertEquals(List.of("priorityNo", "settlement.settlementBusinessDays", "components[MDR].minAmount",
                "components[TIER]", "components[PER_TXN]"), fields(changed.path("differences"))),
            () -> assertEquals("0.50", changed.path("differences").get(2).path("baseValue").asString()),
            () -> assertEquals("0.60", changed.path("differences").get(2).path("targetValue").asString()),
            () -> assertTrue(changed.path("differences").get(3).path("baseValue").isNull()),
            () -> assertEquals("FIXED · CAPTURE", changed.path("differences").get(4).path("baseValue").asString()),
            () -> assertEquals(5, commercial.requests().size(), commercial.requests().toString()));
    }

    /** 两个版本属于不同方案：400 BUSINESS_CHECK_FAILED，原因包含两个方案 ID；只读取了两个版本。 */
    @Test
    void test_businessRuleViolation_versionsOfDifferentPlans() throws Exception {
        replyVersions(PLAN_ID, "7319000000000000509");

        var response = send("GET", diffPath(BASE_ID, TARGET_ID), DIFF_ROUTE, null);

        assertEquals(400, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), body.path("code").asString()),
            () -> assertTrue(body.path("detail").asString().contains("7319000000000000509"), body.path("detail").asString()),
            () -> assertEquals(2, commercial.requests().size(), commercial.requests().toString()));
    }

    /** 版本不存在：commercial 404 与原因透传；相同版本、非法 ID 与多余 Query 在本地拒绝。 */
    @Test
    void test_noDataFound_versionDiff() throws Exception {
        commercial.problem("GET", "/commercial-terms/v1/versions/" + BASE_ID, 404, CommonErrorCode.NOT_FOUND.getCode());
        commercial.reply("GET", "/commercial-terms/v1/versions/" + TARGET_ID, 200, JSON.writeValueAsString(version(TARGET_ID, PLAN_ID)));

        var response = send("GET", diffPath(BASE_ID, TARGET_ID), DIFF_ROUTE, null);

        assertEquals(404, response.statusCode(), response.body());
        assertEquals(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, JSON.readTree(response.body()).path("detail").asString());

        for (String path : List.of(diffPath(BASE_ID, BASE_ID), diffPath("0601", TARGET_ID), diffPath(BASE_ID, TARGET_ID) + "?x=1")) {
            resetBetweenCalls();
            assertRejectedLocally(400, send("GET", path, DIFF_ROUTE, null));
        }
    }

    /** 本页内比较：同一行的上一快照在本页；本页已到列表末尾时，找不到上一快照的为首版（全部字段）。 */
    @Test
    void test_normalFlow_historyComparedWithinPage() throws Exception {
        var items = JSON.createArrayNode()
            .add(change("1004", "AGREEMENT", AGREEMENT_ID, 3, Map.of("status", "ACTIVE", "agreementName", "A")))
            .add(change("1003", "AGREEMENT_CAPABILITY", "7319000000000000301", 3, Map.of("status", "ENABLED", "entityNo", "AE001")))
            .add(change("1002", "AGREEMENT", AGREEMENT_ID, 2, Map.of("status", "SIGNED", "agreementName", "A")))
            .add(change("1001", "AGREEMENT", AGREEMENT_ID, 1, Map.of("status", "DRAFT", "agreementName", "A")));
        commercial.reply("GET", OWNER_HISTORY + "?" + ROOT_QUERY + "&page=1&size=10", 200, JSON.writeValueAsString(page(items, 1, 10, 4)));

        var response = send("GET", HISTORY + "?" + ROOT_QUERY, HISTORY_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var entries = JSON.readTree(response.body()).path("items");
        assertAll(() -> assertEquals(BusinessChangeComparison.COMPARED.getCode(), entries.get(0).path("comparison").asString()),
            () -> assertEquals(2, entries.get(0).path("previousVersionNo").asLong()),
            () -> assertEquals(List.of("status"), strings(entries.get(0).path("changedFields"))),
            () -> assertEquals("SIGNED", entries.get(0).path("previousData").path("status").asString()),
            () -> assertEquals(BusinessChangeComparison.FIRST_VERSION.getCode(), entries.get(1).path("comparison").asString()),
            () -> assertEquals(List.of("entityNo", "status"), strings(entries.get(1).path("changedFields"))),
            () -> assertTrue(entries.get(1).path("previousData").isNull()),
            () -> assertEquals(List.of("status"), strings(entries.get(2).path("changedFields"))),
            () -> assertEquals(BusinessChangeComparison.FIRST_VERSION.getCode(), entries.get(3).path("comparison").asString()),
            () -> assertEquals("AGREEMENT_ACTIVATED", entries.get(0).path("meta").path("reasonCode").asString()),
            () -> assertEquals(1, commercial.requests().size()));
    }

    /** 本页最后一条的上一快照在下一页：按 200 条一页从第 11 条起回溯找到并比较。 */
    @Test
    void test_edgeCase_historyLooksBackBeyondPage() throws Exception {
        var all = JSON.createArrayNode();
        for (int version = 12; version >= 1; version--) {
            all.add(change(String.valueOf(2000 + version), "AGREEMENT", AGREEMENT_ID, version, Map.of("changeMark", "v" + version)));
        }
        var firstTen = JSON.createArrayNode();
        for (int index = 0; index < 10; index++) {
            firstTen.add(all.get(index));
        }
        commercial.reply("GET", OWNER_HISTORY + "?" + ROOT_QUERY + "&page=1&size=10", 200, JSON.writeValueAsString(page(firstTen, 1, 10, 12)));
        commercial.reply("GET", OWNER_HISTORY + "?" + ROOT_QUERY + "&page=1&size=200", 200, JSON.writeValueAsString(page(all, 1, 200, 12)));

        var response = send("GET", HISTORY + "?" + ROOT_QUERY, HISTORY_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var last = JSON.readTree(response.body()).path("items").get(9);
        assertAll(() -> assertEquals(3, last.path("versionNo").asLong()),
            () -> assertEquals(BusinessChangeComparison.COMPARED.getCode(), last.path("comparison").asString()),
            () -> assertEquals(2, last.path("previousVersionNo").asLong()),
            () -> assertEquals(List.of("changeMark"), strings(last.path("changedFields"))),
            () -> assertEquals(2, commercial.requests().size()));
    }

    /** 上一快照超出回溯上限（5 页）：不比较，changedFields 与 previousData 为 null。 */
    @Test
    void test_edgeCase_historyLookBackLimit() throws Exception {
        var items = JSON.createArrayNode();
        for (int index = 0; index < 10; index++) {
            items.add(change(String.valueOf(3000 + index), "AGREEMENT_CAPABILITY", String.valueOf(7319000000000000310L + index), 900,
                Map.of("status", "ENABLED")));
        }
        commercial.reply("GET", OWNER_HISTORY + "?" + ROOT_QUERY + "&page=1&size=10", 200, JSON.writeValueAsString(page(items, 1, 10, 5000)));
        var unrelated = JSON.createArrayNode().add(change("4000", "AGREEMENT", AGREEMENT_ID, 800, Map.of("status", "ACTIVE")));
        for (int pageNo = 1; pageNo <= 5; pageNo++) {
            commercial.reply("GET", OWNER_HISTORY + "?" + ROOT_QUERY + "&page=" + pageNo + "&size=200", 200,
                JSON.writeValueAsString(page(unrelated, pageNo, 200, 5000)));
        }

        var response = send("GET", HISTORY + "?" + ROOT_QUERY, HISTORY_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var entries = JSON.readTree(response.body()).path("items");
        assertAll(() -> assertTrue(entries.valueStream().allMatch(entry ->
                BusinessChangeComparison.NOT_COMPARED.getCode().equals(entry.path("comparison").asString())
                    && entry.path("changedFields").isNull() && entry.path("previousData").isNull())),
            () -> assertEquals(6, commercial.requests().size()));
    }

    /** 空历史为空页；参数错误本地拒绝；commercial 的业务拒绝透传原因，INVALID_REQUEST 只保留公共说明。 */
    @Test
    void test_invalidParameter_history() throws Exception {
        commercial.reply("GET", OWNER_HISTORY, 200, JSON.writeValueAsString(page(JSON.createArrayNode(), 1, 10, 0)));
        var empty = send("GET", HISTORY + "?" + ROOT_QUERY, HISTORY_ROUTE, null);
        assertEquals(200, empty.statusCode(), empty.body());
        assertEquals(0, JSON.readTree(empty.body()).path("items").size());

        for (String query : List.of("?aggregateRootType=AGREEMENT", "?aggregateRootType=CLIENT&aggregateRootId=1",
            "?" + ROOT_QUERY + "&size=7", "?" + ROOT_QUERY + "&aggregateRootId=2")) {
            resetBetweenCalls();
            assertRejectedLocally(400, send("GET", HISTORY + query, HISTORY_ROUTE, null));
        }

        resetBetweenCalls();
        commercial.problem("GET", OWNER_HISTORY, 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        var rejected = send("GET", HISTORY + "?" + ROOT_QUERY, HISTORY_ROUTE, null);
        assertEquals(400, rejected.statusCode(), rejected.body());
        assertEquals(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, JSON.readTree(rejected.body()).path("detail").asString());

        resetBetweenCalls();
        commercial.problem("GET", OWNER_HISTORY, 400, CommonErrorCode.INVALID_REQUEST.getCode());
        var invalid = send("GET", HISTORY + "?" + ROOT_QUERY, HISTORY_ROUTE, null);
        var invalidBody = JSON.readTree(invalid.body());
        assertAll(() -> assertEquals(400, invalid.statusCode(), invalid.body()),
            () -> assertEquals(CommonErrorCode.INVALID_REQUEST.getCode(), invalidBody.path("code").asString()),
            () -> assertFalse(invalid.body().contains(CommercialOwnerSimulator.OWNER_DETAIL_CANARY), "INVALID_REQUEST 不外显 Owner detail"));
    }

    /** 版本对比需要定价只读；变更历史需要合同只读。 */
    @Test
    void test_authorizationDenied() throws Exception {
        platform.grantPermissions(COMMERCIAL_CONTRACT_READ_PERMISSION_ID);
        assertDenied(send("GET", diffPath(BASE_ID, TARGET_ID), DIFF_ROUTE, null));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_READ_PERMISSION_ID);
        assertDenied(send("GET", HISTORY + "?" + ROOT_QUERY, HISTORY_ROUTE, null));
    }

    private void replyVersions(String basePlanId, String targetPlanId) {
        commercial.reply("GET", "/commercial-terms/v1/versions/" + BASE_ID, 200, JSON.writeValueAsString(version(BASE_ID, basePlanId)));
        var target = version(TARGET_ID, targetPlanId).put("feeTaxTreatment", FeeTaxTreatment.INCLUSIVE.getCode());
        commercial.reply("GET", "/commercial-terms/v1/versions/" + TARGET_ID, 200, JSON.writeValueAsString(target));
        commercial.reply("GET", "/agreements/v1/" + AGREEMENT_ID, 200, JSON.writeValueAsString(agreement()));
    }

    private static String diffPath(String baseId, String targetId) {
        return "/commercial-admin/v1/commercial-terms/versions/" + baseId + "/diff/" + targetId;
    }

    /** 基准：AE001 USD Visa（MDR 0.029 最低 0.50 + 每笔 0.30，T+2，优先级 10）、USD Mastercard、AED Visa。 */
    private static List<ObjectNode> basePrices() {
        var visa = price("CARD_VISA", "USD", 10, 2);
        component(visa, "MDR", "PERCENTAGE", CalculationBase.GROSS_AMOUNT).put("rate", "0.029").put("minAmount", "0.50");
        component(visa, "PER_TXN", "FIXED", CalculationBase.FIXED_PER_EVENT).put("fixedAmount", "0.30");
        var mastercard = price("CARD_MC", "USD", 20, 2);
        component(mastercard, "MDR", "PERCENTAGE", CalculationBase.GROSS_AMOUNT).put("rate", "0.03");
        var aed = price("CARD_VISA", "AED", 10, 2);
        component(aed, "MDR", "PERCENTAGE", CalculationBase.GROSS_AMOUNT).put("rate", "0.025");
        return List.of(visa, mastercard, aed);
    }

    /** 目标：Visa 改为优先级 20、T+3、最低 0.60、去掉每笔固定费、新增阶梯组件；Mastercard 数值不变；新增 Apple Pay。 */
    private static List<ObjectNode> targetPrices() {
        var visa = price("CARD_VISA", "USD", 20, 3);
        component(visa, "MDR", "PERCENTAGE", CalculationBase.GROSS_AMOUNT).put("rate", "0.0290").put("minAmount", "0.60");
        var tier = component(visa, "TIER", "TIERED", CalculationBase.GROSS_AMOUNT).put("bandBasis", "TRANSACTION_AMOUNT")
            .put("bandMode", "WHOLE_BASE");
        tier.withArray("bands").addObject().put("bandId", "7319000000000009101").put("bandNo", 1).put("lowerBound", "0")
            .putNull("upperBound").put("rate", "0.01").putNull("fixedAmount").put("currency", "USD");
        var mastercard = price("CARD_MC", "USD", 20, 2);
        component(mastercard, "MDR", "PERCENTAGE", CalculationBase.GROSS_AMOUNT).put("rate", "0.030");
        var applePay = price("APPLE_PAY_STD", "USD", 30, 2);
        component(applePay, "MDR", "PERCENTAGE", CalculationBase.GROSS_AMOUNT).put("rate", "0.025");
        return List.of(visa, mastercard, applePay);
    }

    private static ObjectNode prices(String versionId, List<ObjectNode> prices) {
        var body = JSON.createObjectNode().put("termVersionId", versionId).put("termType", "CLIENT_PRICING").put("entityNo", "AE001");
        ArrayNode list = body.putArray("prices");
        for (ObjectNode price : prices) {
            list.add(price.deepCopy().put("termVersionId", versionId));
        }
        return body;
    }

    private static ObjectNode price(String profileCode, String currency, int priority, int settlementDays) {
        var price = JSON.createObjectNode().put("termType", "CLIENT_PRICING").put("agreementId", AGREEMENT_ID).put("planId", PLAN_ID)
            .put("termVersionId", BASE_ID).put("entityNo", "AE001").put("clientId", CLIENT_ID).putNull("localPartnerId")
            .put("priceId", "73190000000000070" + priority).put("pricingProfileCode", profileCode).put("paymentMethodCode", "CARD")
            .put("priorityNo", priority).put("currency", currency).put("pricingModel", "BLENDED")
            .put("feeTaxTreatment", FeeTaxTreatment.EXCLUSIVE.getCode());
        price.putArray("conditions");
        price.putArray("components");
        price.putObject("settlement").put("settlementBusinessDays", settlementDays).put("businessCalendarCode", "AE_BUSINESS_DAYS")
            .put("settlementTimezone", "UTC").put("cutoffSecondOfDay", 86399);
        return price;
    }

    private static ObjectNode component(ObjectNode price, String code, String type, CalculationBase base) {
        var components = price.withArray("components");
        var component = components.addObject().put("componentId", "731900000000000800" + components.size())
            .put("componentNo", components.size()).put("componentCode", code).put("componentType", type).put("triggerCode", CAPTURE)
            .put("calculationBase", base.getCode()).put("refundBehavior", "RETURN_PRORATA").putNull("rate").putNull("fixedAmount")
            .put("currency", "USD").putNull("minAmount").putNull("maxAmount").putNull("bandBasis").putNull("bandMode");
        component.putArray("bands");
        return component;
    }

    private static ObjectNode version(String versionId, String planId) {
        return JSON.createObjectNode().put("versionId", versionId).put("planId", planId).put("termType", "CLIENT_PRICING")
            .put("agreementId", AGREEMENT_ID).put("versionNo", BASE_ID.equals(versionId) ? "1" : "2").put("status", "ACTIVE")
            .put("effectiveFrom", "2026-10-01T00:00:00Z").putNull("effectiveTo").put("roundingMode", "HALF_UP")
            .put("pricingModel", "BLENDED").put("feeTaxTreatment", FeeTaxTreatment.EXCLUSIVE.getCode()).put("changeVersion", "5");
    }

    private static ObjectNode agreement() {
        var node = JSON.createObjectNode().put("agreementId", AGREEMENT_ID).put("agreementNo", "AGR-AE001-0001")
            .put("agreementName", "示例合同").put("agreementType", "CLIENT").put("businessMode", "MOR")
            .put("fundMode", "SETTLE_THROUGH_PLATFORM").put("pricingAuthority", "PLATFORM").putNull("referralPartnerId")
            .put("clientId", CLIENT_ID).putNull("localPartnerId").put("entityNo", "AE001").put("status", "ACTIVE")
            .put("effectiveFrom", "2026-10-01T00:00:00Z").putNull("effectiveTo").put("signedTime", "2026-10-02T00:00:00Z")
            .put("activatedTime", "2026-10-03T00:00:00Z").put("changeVersion", 7);
        node.putArray("operatingEntityNos");
        node.putArray("parties");
        node.putArray("documents");
        node.putArray("productCodes").add("ACQUIRING");
        node.putArray("capabilities");
        return node;
    }

    private static ObjectNode change(String historyId, String recordType, String recordId, long versionNo, Map<String, Object> data) {
        var change = JSON.createObjectNode().put("historyId", historyId).put("recordType", recordType).put("recordId", recordId)
            .put("versionNo", versionNo).put("operationType", versionNo == 1 ? "CREATE" : "UPDATE").put("createBy", "10001")
            .put("createTime", "2026-10-20T06:00:00Z");
        change.putObject("meta").put("triggerType", "COMMAND").put("triggerId", "88" + historyId).put("reasonCode", "AGREEMENT_ACTIVATED");
        change.set("data", JSON.valueToTree(data));
        return change;
    }

    private static ObjectNode page(JsonNode items, int page, int size, long total) {
        var body = JSON.createObjectNode();
        body.set("items", items);
        return body.put("page", page).put("size", size).put("total", total);
    }

    private static List<String> fields(JsonNode differences) {
        return differences.valueStream().map(difference -> difference.path("field").asString()).toList();
    }

    private static List<String> profiles(JsonNode prices) {
        return prices.valueStream().map(price -> price.path("pricingProfileCode").asString()).toList();
    }

    private static List<String> strings(JsonNode array) {
        var values = new ArrayList<String>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }
}
