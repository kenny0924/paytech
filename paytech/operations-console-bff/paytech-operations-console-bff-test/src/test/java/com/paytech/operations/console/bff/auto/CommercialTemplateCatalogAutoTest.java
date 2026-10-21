package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_CATALOG_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_CONTRACT_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_PRICING_APPROVE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_PRICING_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_TEMPLATE_APPROVE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.COMMERCIAL_TEMPLATE_MANAGE_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.ext.rest.CommercialCatalogController;
import com.paytech.operations.console.bff.ext.rest.CommercialTemplateController;
import com.paytech.operations.console.bff.service.processor.CommercialTermScopedCommandProcessor;
import com.paytech.operations.console.bff.test.base.CommercialBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.CommercialOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 产品目录维护、标准价目模板与价格协议签署经真实 BFF 与 HttpExchange 转发 commercial；commercial 由 Loopback Simulator 承担
 * <li>转发契约与授权拒绝由 CommercialBrowserAutoTest 按边界清单逐行覆盖；本类覆盖权限分离、模板 / 合同路由范围隔离、Owner 错误透传与本地边界。</li>
 *
 * @see CommercialCatalogController
 * @see CommercialTemplateController
 * @see CommercialTermScopedCommandProcessor
 * @author Claude
 */
class CommercialTemplateCatalogAutoTest extends CommercialBrowserAutoTestBase {

    /** 模板 MANAGE 可以提交但不能复核；模板 APPROVE 可以复核但不能提交；合同定价权限不能操作模板，模板权限不能操作合同版本。 */
    @Test
    void test_templatePermissionsAreSeparated() throws Exception {
        platform.grantPermissions(COMMERCIAL_TEMPLATE_MANAGE_PERMISSION_ID);
        assertDenied(send(row("M06")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_TEMPLATE_MANAGE_PERMISSION_ID);
        var submit = row("M05");
        replyAsOwner(submit);
        assertForwarded(submit, send(submit));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_TEMPLATE_APPROVE_PERMISSION_ID);
        assertDenied(send(row("M05")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_TEMPLATE_APPROVE_PERMISSION_ID);
        var approve = row("M06");
        replyAsOwner(approve);
        assertForwarded(approve, send(approve));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_MANAGE_PERMISSION_ID, COMMERCIAL_PRICING_APPROVE_PERMISSION_ID);
        assertDenied(send(row("M04")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_MANAGE_PERMISSION_ID, COMMERCIAL_PRICING_APPROVE_PERMISSION_ID);
        assertDenied(send(row("M02")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_TEMPLATE_MANAGE_PERMISSION_ID, COMMERCIAL_TEMPLATE_APPROVE_PERMISSION_ID);
        assertDenied(send(row("T04")));
    }

    /** 目录维护只认 COMMERCIAL_CATALOG_MANAGE：合同读写权限不能维护目录，目录维护权限不能读取合同。 */
    @Test
    void test_catalogManageRequiresOwnPermission() throws Exception {
        platform.grantPermissions(COMMERCIAL_CONTRACT_READ_PERMISSION_ID);
        assertDenied(send(row("K05")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_PRICING_MANAGE_PERMISSION_ID);
        assertDenied(send(row("K09")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_CATALOG_MANAGE_PERMISSION_ID);
        assertDenied(send(row("K02")));

        resetBetweenCalls();
        platform.grantPermissions(COMMERCIAL_CATALOG_MANAGE_PERMISSION_ID);
        var create = row("K05");
        replyAsOwner(create);
        assertForwarded(create, send(create));
    }

    /** 合同价目路由遇到模板版本、模板路由遇到合同版本均 400，commercial 只收到 Owner 类型读取，没有写入。 */
    @Test
    void test_businessRuleViolation_versionScopeIsolated() throws Exception {
        var contractPrices = row("T03");
        commercial.reply("GET", "/commercial-terms/v1/versions/" + versionId(contractPrices), 200,
            JSON.writeValueAsString(ownerType(contractPrices.path("preReads").get(0).path("response"),
                CommercialTermOwnerType.PLATFORM_TEMPLATE)));
        var rejected = send(contractPrices.path("method").asString(), contractPrices.path("path").asString(),
            contractPrices.path("routeCode").asString(), contractPrices.path("request"));
        assertScopeRejected(contractPrices, rejected);
        assertTrue(JSON.readTree(rejected.body()).path("detail").asString().contains("标准价目页"));

        resetBetweenCalls();
        var templateApprove = row("M06");
        commercial.reply("GET", "/commercial-terms/v1/versions/" + versionId(templateApprove), 200,
            JSON.writeValueAsString(ownerType(templateApprove.path("preReads").get(0).path("response"),
                CommercialTermOwnerType.CLIENT)));
        var contractVersion = send(templateApprove.path("method").asString(), templateApprove.path("path").asString(),
            templateApprove.path("routeCode").asString(), templateApprove.path("request"));
        assertScopeRejected(templateApprove, contractVersion);
        assertTrue(JSON.readTree(contractVersion.body()).path("detail").asString().contains("不是标准价目模板"));
    }

    /** 建版本按模板方案列表判定方案归属：合同路由不能给模板方案建版本，模板路由不能给合同方案建版本。 */
    @Test
    void test_businessRuleViolation_planScopeIsolated() throws Exception {
        var contractCreate = row("T02");
        var templatesWithContractPlan = (ObjectNode) contractCreate.path("preReads").get(0).path("response").deepCopy();
        ((ObjectNode) templatesWithContractPlan.path("templates").get(0)).put("planId", planId(contractCreate));
        commercial.reply("GET", "/commercial-terms/v1/templates", 200, JSON.writeValueAsString(templatesWithContractPlan));
        assertScopeRejected(contractCreate, send(contractCreate.path("method").asString(),
            contractCreate.path("path").asString(), contractCreate.path("routeCode").asString(),
            contractCreate.path("request")));

        resetBetweenCalls();
        var templateCreate = row("M03");
        var noTemplates = JSON.createObjectNode().set("templates", JSON.createArrayNode());
        commercial.reply("GET", "/commercial-terms/v1/templates", 200, JSON.writeValueAsString(noTemplates));
        assertScopeRejected(templateCreate, send(templateCreate.path("method").asString(),
            templateCreate.path("path").asString(), templateCreate.path("routeCode").asString(),
            templateCreate.path("request")));
    }

    /** 版本 Owner 类型读取失败按 Owner 原因返回且不写入；读取回执缺少 Owner 类型视为上游无效响应。 */
    @Test
    void test_noDataFound_scopeReadFailure() throws Exception {
        var submit = row("M05");
        commercial.problem("GET", "/commercial-terms/v1/versions/" + versionId(submit), 404,
            CommonErrorCode.NOT_FOUND.getCode());
        var missing = send(submit.path("method").asString(), submit.path("path").asString(),
            submit.path("routeCode").asString(), submit.path("request"));
        assertEquals(404, missing.statusCode(), missing.body());
        assertEquals(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, JSON.readTree(missing.body()).path("detail").asString());
        assertEquals(1, commercial.requests().size(), commercial.requests().toString());

        resetBetweenCalls();
        var withoutOwner = (ObjectNode) submit.path("preReads").get(0).path("response").deepCopy();
        withoutOwner.putNull("ownerType");
        commercial.reply("GET", "/commercial-terms/v1/versions/" + versionId(submit), 200,
            JSON.writeValueAsString(withoutOwner));
        var invalid = send(submit.path("method").asString(), submit.path("path").asString(),
            submit.path("routeCode").asString(), submit.path("request"));
        assertEquals(502, invalid.statusCode(), invalid.body());
        assertEquals(1, commercial.requests().size(), commercial.requests().toString());
    }

    /** 模板复核（Checker 等于 Maker）、签署（未 APPROVED）与目录维护（代码重复）的 Owner 拒绝原因透传到浏览器，Owner Request ID 不外露。 */
    @Test
    void test_ownerRejectionDetailPassedThrough() throws Exception {
        assertOwnerDetail(row("M06"), 400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        resetBetweenCalls();
        assertOwnerDetail(row("T15"), 400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        resetBetweenCalls();
        assertOwnerDetail(row("K05"), 409, CommonErrorCode.CONCURRENT_REQUEST);

        resetBetweenCalls();
        assertOwnerDetail(row("T14"), 409, CommonErrorCode.CONCURRENT_REQUEST);
    }

    /** 开通项条件、目录与签署请求的未知字段、旧 limits 字段、缺少必填字段、查询带参数在访问 commercial 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var capability = row("C08");
        var legacy = (ObjectNode) capability.path("request").deepCopy();
        legacy.set("limits", JSON.createArrayNode());
        assertRejectedLocally(400, send(capability.path("method").asString(), capability.path("path").asString(),
            capability.path("routeCode").asString(), legacy));

        resetBetweenCalls();
        var emptyValues = (ObjectNode) capability.path("request").deepCopy();
        ((ObjectNode) emptyValues.path("conditions").get(0)).set("values", JSON.createArrayNode());
        var response = send(capability.path("method").asString(), capability.path("path").asString(),
            capability.path("routeCode").asString(), emptyValues);
        assertRejectedLocally(422, response);
        assertEquals("conditions[0].values", JSON.readTree(response.body()).path("errors").get(0).path("field").asString());

        resetBetweenCalls();
        var create = row("K05");
        var unknown = (ObjectNode) create.path("request").deepCopy();
        unknown.put("status", "ACTIVE");
        assertRejectedLocally(400, send(create.path("method").asString(), create.path("path").asString(),
            create.path("routeCode").asString(), unknown));

        resetBetweenCalls();
        var acceptance = row("T15");
        var noHash = (ObjectNode) acceptance.path("request").deepCopy();
        noHash.remove("contentHash");
        var missingHash = send(acceptance.path("method").asString(), acceptance.path("path").asString(),
            acceptance.path("routeCode").asString(), noHash);
        assertRejectedLocally(422, missingHash);
        assertEquals("contentHash", JSON.readTree(missingHash.body()).path("errors").get(0).path("field").asString());

        resetBetweenCalls();
        var dimensions = row("K04");
        assertRejectedLocally(400, send("GET", dimensions.path("path").asString() + "?productCode=ACQUIRING",
            dimensions.path("routeCode").asString(), null));

        resetBetweenCalls();
        var templates = row("M01");
        assertRejectedLocally(400, send("GET", templates.path("path").asString() + "?entityNo=ae001",
            templates.path("routeCode").asString(), null));
    }

    /** 模板方案创建回执不是 PLATFORM_TEMPLATE、目录新建回执代码与请求不一致，均按上游无效响应 502。 */
    @Test
    void test_edgeCase_receiptMismatch() throws Exception {
        var template = row("M02");
        var notTemplate = (ObjectNode) template.path("response").deepCopy();
        notTemplate.put("ownerType", CommercialTermOwnerType.CLIENT.getCode());
        commercial.reply("POST", template.path("ownerPath").asString(), 201, JSON.writeValueAsString(notTemplate));
        var templateResponse = send(template);
        assertEquals(502, templateResponse.statusCode(), templateResponse.body());

        resetBetweenCalls();
        var capability = row("K05");
        var otherCode = (ObjectNode) capability.path("response").deepCopy();
        otherCode.put("capabilityCode", "MADA");
        commercial.reply("POST", capability.path("ownerPath").asString(), 201, JSON.writeValueAsString(otherCode));
        var capabilityResponse = send(capability);
        assertEquals(502, capabilityResponse.statusCode(), capabilityResponse.body());
        assertFalse(capabilityResponse.body().contains("MADA"));
    }

    /** Owner 以给定状态与公共 Code 拒绝该行写入：浏览器得到同一 Code 与 Owner detail，Owner Request ID 不外露。 */
    private void assertOwnerDetail(JsonNode row, int status, CommonErrorCode code) throws Exception {
        commercial.problem(row.path("method").asString(), row.path("ownerPath").asString(), status, code.getCode());
        var response = send(row);
        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code.getCode(), body.path("code").asString()),
            () -> assertEquals(CommercialOwnerSimulator.OWNER_DETAIL_CANARY, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(CommercialOwnerSimulator.OWNER_REQUEST_ID)));
    }

    /** 版本样例的版本 ID（BFF 路径倒数第二段）。 */
    private static String versionId(JsonNode row) {
        String[] segments = row.path("path").asString().split("/");
        return segments[segments.length - 2];
    }

    /** 方案样例的方案 ID（BFF 路径倒数第二段）。 */
    private static String planId(JsonNode row) {
        return versionId(row);
    }

    /** 复制版本响应并改写 Owner 类型。 */
    private static ObjectNode ownerType(JsonNode version, CommercialTermOwnerType ownerType) {
        var copy = (ObjectNode) version.deepCopy();
        copy.put("ownerType", ownerType.getCode());
        return copy;
    }
}
