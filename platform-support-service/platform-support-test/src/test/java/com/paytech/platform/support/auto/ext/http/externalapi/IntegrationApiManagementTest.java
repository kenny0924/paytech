package com.paytech.platform.support.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.api.externalapi.IntegrationApiManagementApi;
import com.paytech.platform.support.api.externalapi.request.*;
import com.paytech.platform.support.api.externalapi.response.IntegrationApiResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationApiSummary;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiRequests;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ObjectNode;

/**
 * 原子 API 管理：创建与详情、定义静态校验（路径变量、地址代码、固定值、脚本阶段）、校验接口不落库、 修改时复核引用流程、被流程引用时删除保护。
 *
 * @see IntegrationApiManagementApi#listIntegrationApis(String, String, String, Integer, Integer, String)
 * @see IntegrationApiManagementApi#getIntegrationApi(String, String, String, String)
 * @see IntegrationApiManagementApi#createIntegrationApi(String, String, String, CreateIntegrationApiRequestBody)
 * @see IntegrationApiManagementApi#updateIntegrationApi(String, String, String, String,
 *      UpdateIntegrationApiRequestBody)
 * @see IntegrationApiManagementApi#deleteIntegrationApi(String, String, String, String,
 *      DeleteExternalApiConfigRequestBody)
 * @see IntegrationApiManagementApi#validateIntegrationApi(String, String, String, ValidateIntegrationApiRequestBody)
 */
class IntegrationApiManagementTest extends ExternalApiTestBase {

    private static final String PATH = BASE + "/integrations/DEMO/apis";

    private Operator admin;
    private Integration demo;

    /** 本类公共：一名平台管理员、一个带样例出站地址的接入方 DEMO。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = administrator();
        demo = externalApi.integration("DEMO");
    }

    /** 引用同阶段脚本的定义保存成功；详情原样返回定义，列表统计脚本数，并写入成功审计。 */
    @Test
    void test_createAndGet() {
        externalApi.signScript(demo, ScriptExecutionStage.REQUEST);
        var definition = ExternalApiSamples.createOrderDefinitionWithScript(ExternalApiSamples.SIGN_SCRIPT);

        assertMutation(post(PATH, admin, ExternalApiRequests.createIntegrationApi("CREATE_ORDER", definition)),
            "INTEGRATION_API", "DEMO/CREATE_ORDER", 1);
        auditChecker.assertEvent(requestId, "OPERATIONS_INTEGRATION_API_CREATE", AuditModels.Result.SUCCEEDED.getCode(), admin.actor(),
            "INTEGRATION_API", "DEMO/CREATE_ORDER");

        var detail = get(PATH + "/CREATE_ORDER", admin).assertOk().as(IntegrationApiResponseBody.class);
        var page = get(PATH, admin).assertOk().as(new TypeReference<PagingResponseBody<IntegrationApiSummary>>() {});

        assertAll(() -> assertEquals(definition, detail.getDefinition()),
            () -> assertEquals(4, detail.getRequestFields().size()),
            () -> assertTrue(detail.getReferencedBy().isEmpty()), () -> assertEquals(1, page.total()),
            () -> assertEquals("POST", page.items().getFirst().getMethod()),
            () -> assertEquals(ExternalApiSamples.ENDPOINT, page.items().getFirst().getEndpointCode()),
            () -> assertEquals("/outlets/{outletRef}/orders", page.items().getFirst().getPathTemplate()),
            () -> assertEquals(1, page.items().getFirst().getRequestScriptCount()),
            () -> assertEquals(0, page.items().getFirst().getResponseScriptCount()));
    }

    /** 路径变量与 PATH 字段不对应、地址代码不存在、固定值字段未知、脚本阶段不符时一次返回全部位置，不落库。 */
    @Test
    void test_invalidDefinition() {
        externalApi.signScript(demo, ScriptExecutionStage.RESPONSE);
        ObjectNode definition = ExternalApiSamples.createOrderDefinitionWithScript(ExternalApiSamples.SIGN_SCRIPT);
        ObjectNode transport = (ObjectNode)definition.get("transport");
        transport.put("pathTemplate", "/outlets/{storeRef}/orders");
        transport.put("endpointCode", "MISSING_GATEWAY");
        ((ObjectNode)definition.get("fixedValues")).set("HEADER:X-Unknown",
            ExternalApiSamples.object("{\"kind\":\"EXPRESSION\",\"expression\":\"'x'\"}"));

        var issues = issues(
            post(PATH + "/validate", admin, ExternalApiRequests.validateIntegrationApi("CREATE_ORDER", definition)));
        post(PATH, admin, ExternalApiRequests.createIntegrationApi("CREATE_ORDER", definition))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("definition.transport.pathTemplate")
            .assertFieldError("definition.transport.endpointCode");

        assertEquals(List.of("ENDPOINT_NOT_FOUND@definition.transport.endpointCode",
            "FIXED_VALUE_FIELD_UNKNOWN@definition.fixedValues.HEADER:X-Unknown",
            "PATH_VARIABLE_MISMATCH@definition.transport.pathTemplate",
            "SCRIPT_STAGE_MISMATCH@definition.scripts[0].stage"), issues);
        get(PATH + "/CREATE_ORDER", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 校验接口对合法定义返回 valid，且不创建原子 API。 */
    @Test
    void test_validateDoesNotPersist() {
        var result = post(PATH + "/validate", admin,
            ExternalApiRequests.validateIntegrationApi("CREATE_ORDER", ExternalApiSamples.createOrderDefinition()))
            .assertOk().as(ValidationResponseBody.class);

        assertAll(() -> assertTrue(result.isValid()), () -> assertTrue(result.getIssues().isEmpty()));
        get(PATH + "/CREATE_ORDER", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 新增必填请求字段会让引用它的流程缺少赋值，修改被拒绝并列出标准 API；原定义保持。 */
    @Test
    void test_updateBreakingFlow() {
        var pay = externalApi.standardApi("PAY");
        externalApi.createOrderApi(demo);
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());
        var fields = new ArrayList<ApiFieldBody>(ExternalApiSamples.createOrderRequestFields());
        fields.add(ExternalApiSamples.field(ApiFieldLocation.BODY, "currency", ApiFieldDataType.STRING, true));

        var rejected = put(PATH + "/CREATE_ORDER", admin,
            ExternalApiRequests.updateIntegrationApi(fields, ExternalApiSamples.createOrderDefinition(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertTrue(rejected.json().path("detail").asString().contains("PAY"));

        var detail = get(PATH + "/CREATE_ORDER", admin).assertOk().as(IntegrationApiResponseBody.class);
        assertAll(() -> assertEquals(1, detail.getVersion()), () -> assertEquals(4, detail.getRequestFields().size()),
            () -> assertEquals("PAY", detail.getReferencedBy().getFirst().getStandardApiCode()),
            () -> assertEquals(List.of("createOrder"), detail.getReferencedBy().getFirst().getNodeIds()));
    }

    /** 兼容修改（新增可选字段）保存成功并递增版本。 */
    @Test
    void test_compatibleUpdate() {
        var pay = externalApi.standardApi("PAY");
        externalApi.createOrderApi(demo);
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());
        var fields = new ArrayList<ApiFieldBody>(ExternalApiSamples.createOrderRequestFields());
        fields.add(ExternalApiSamples.field(ApiFieldLocation.BODY, "memo", ApiFieldDataType.STRING, false));

        assertMutation(
            put(PATH + "/CREATE_ORDER", admin,
                ExternalApiRequests.updateIntegrationApi(fields, ExternalApiSamples.createOrderDefinition(), 1)),
            "INTEGRATION_API", "DEMO/CREATE_ORDER", 2);

        assertEquals(5, get(PATH + "/CREATE_ORDER", admin).assertOk().as(IntegrationApiResponseBody.class)
            .getRequestFields().size());
    }

    /** 被流程引用时不能删除；未被引用的原子 API 按版本删除。 */
    @Test
    void test_delete() {
        var pay = externalApi.standardApi("PAY");
        externalApi.createOrderApi(demo);
        externalApi.integrationApi(demo, "QUERY_ORDER", ExternalApiSamples.createOrderDefinition());
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());

        delete(PATH + "/CREATE_ORDER", admin, ExternalApiRequests.delete(1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertMutation(delete(PATH + "/QUERY_ORDER", admin, ExternalApiRequests.delete(1)), "INTEGRATION_API",
            "DEMO/QUERY_ORDER", 1);

        get(PATH + "/QUERY_ORDER", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(PATH + "/CREATE_ORDER", admin).assertOk();
    }

    /** 接入方不存在时返回 404。 */
    @Test
    void test_integrationNotFound() {
        get(BASE + "/integrations/NOPE/apis/CREATE_ORDER", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        post(BASE + "/integrations/NOPE/apis", admin,
            ExternalApiRequests.createIntegrationApi("CREATE_ORDER", ExternalApiSamples.createOrderDefinition()))
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
    }
}
