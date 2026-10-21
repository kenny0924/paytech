package com.paytech.channel.connector.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.api.externalapi.IntegrationApiManagementApi;
import com.paytech.channel.connector.api.externalapi.request.*;
import com.paytech.channel.connector.api.externalapi.response.IntegrationApiResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationApiSummary;
import com.paytech.channel.connector.api.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiRequests;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ObjectNode;

/**
 * 原子 API 管理：创建与详情、定义静态校验（路径变量、地址代码、固定值、脚本阶段）、校验接口不落库、 修改时复核引用流程、被流程引用时删除保护。
 *
 * @see IntegrationApiManagementApi#listIntegrationApis(String, Integer, Integer, String)
 * @see IntegrationApiManagementApi#getIntegrationApi(String, String)
 * @see IntegrationApiManagementApi#createIntegrationApi(String, String, CreateIntegrationApiRequestBody)
 * @see IntegrationApiManagementApi#updateIntegrationApi(String, String, String, UpdateIntegrationApiRequestBody)
 * @see IntegrationApiManagementApi#deleteIntegrationApi(String, String, String, DeleteExternalApiConfigRequestBody)
 * @see IntegrationApiManagementApi#validateIntegrationApi(String, ValidateIntegrationApiRequestBody)
 */
class IntegrationApiManagementTest extends ExternalApiTestBase {

    private static final String PATH = BASE + "/integrations/DEMO/apis";

    private Integration demo;

    /** 本类公共：一个带样例出站地址的接入方 DEMO。 */
    @BeforeEach
    void setUpClassMocks() {
        demo = externalApi.integration("DEMO");
    }

    /** 引用同阶段脚本的定义保存成功；详情原样返回定义，列表统计脚本数，并同事务写入 CREATE 变更记录（定义只记摘要）。 */
    @Test
    void test_createAndGet() {
        externalApi.signScript(demo, ScriptExecutionStage.REQUEST);
        var definition = ExternalApiSamples.createOrderDefinitionWithScript(ExternalApiSamples.SIGN_SCRIPT);

        assertMutation(post(PATH, ExternalApiRequests.createIntegrationApi("CREATE_ORDER", definition)),
            "INTEGRATION_API", "DEMO/CREATE_ORDER", 1);
        var change = assertChange(ChangeTargetType.INTEGRATION_API,
            externalApiData.integrationApiId("DEMO", "CREATE_ORDER"), ChangeType.CREATE, "DEMO/CREATE_ORDER", 0, 1,
            ExternalApiRequests.REASON);
        assertTrue(changedFields(change).contains(ConfigurationField.DEFINITION_DIGEST.getCode()));

        var detail = get(PATH + "/CREATE_ORDER").assertOk().as(IntegrationApiResponseBody.class);
        var page = get(PATH).assertOk().as(new TypeReference<PagingResponseBody<IntegrationApiSummary>>() {});

        assertAll(() -> assertEquals(definition, detail.getDefinition()),
            () -> assertEquals(4, detail.getRequestFields().size()),
            () -> assertTrue(detail.getReferencedBy().isEmpty()), () -> assertEquals(1, page.total()),
            () -> assertEquals("POST", page.items().getFirst().getMethod()),
            () -> assertEquals(ExternalApiSamples.ENDPOINT, page.items().getFirst().getEndpointCode()),
            () -> assertEquals("/outlets/{outletRef}/orders", page.items().getFirst().getPathTemplate()),
            () -> assertEquals(1, page.items().getFirst().getRequestScriptCount()),
            () -> assertEquals(0, page.items().getFirst().getResponseScriptCount()));
    }

    /** 路径变量与 PATH 字段不对应、地址代码不存在、固定值字段未知、脚本阶段不符、小数格式非法时一次返回全部位置，不落库。 */
    @Test
    void test_invalidDefinition() {
        externalApi.signScript(demo, ScriptExecutionStage.RESPONSE);
        ObjectNode definition = ExternalApiSamples.createOrderDefinitionWithScript(ExternalApiSamples.SIGN_SCRIPT);
        ObjectNode transport = (ObjectNode)definition.get("transport");
        transport.put("pathTemplate", "/outlets/{storeRef}/orders");
        transport.put("endpointCode", "MISSING_GATEWAY");
        ((ObjectNode)definition.get("fixedValues")).set("HEADER:X-Unknown",
            ExternalApiSamples.object("{\"kind\":\"EXPRESSION\",\"expression\":\"'x'\"}"));
        definition.put("decimalEncoding", "FLOAT");

        var issues = issues(
            validate(PATH + "/validate", ExternalApiRequests.validateIntegrationApi("CREATE_ORDER", definition)));
        post(PATH, ExternalApiRequests.createIntegrationApi("CREATE_ORDER", definition))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("definition.transport.pathTemplate")
            .assertFieldError("definition.transport.endpointCode").assertFieldError("definition.decimalEncoding");

        assertEquals(List.of("DECIMAL_ENCODING_INVALID@definition.decimalEncoding",
            "ENDPOINT_NOT_FOUND@definition.transport.endpointCode",
            "FIXED_VALUE_FIELD_UNKNOWN@definition.fixedValues.HEADER:X-Unknown",
            "PATH_VARIABLE_MISMATCH@definition.transport.pathTemplate",
            "SCRIPT_STAGE_MISMATCH@definition.scripts[0].stage"), issues);
        get(PATH + "/CREATE_ORDER").assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 校验接口对合法定义返回 valid，且不创建原子 API。 */
    @Test
    void test_validateDoesNotPersist() {
        var result = validate(PATH + "/validate",
            ExternalApiRequests.validateIntegrationApi("CREATE_ORDER", ExternalApiSamples.createOrderDefinition()))
            .assertOk().as(ValidationResponseBody.class);

        assertAll(() -> assertTrue(result.isValid()), () -> assertTrue(result.getIssues().isEmpty()));
        get(PATH + "/CREATE_ORDER").assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 新增必填请求字段会让引用它的流程缺少赋值，修改被拒绝并列出标准 API；原定义保持。 */
    @Test
    void test_updateBreakingFlow() {
        var pay = externalApi.standardApi("PAY");
        externalApi.createOrderApi(demo);
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());
        var fields = new ArrayList<ApiFieldBody>(ExternalApiSamples.createOrderRequestFields());
        fields.add(ExternalApiSamples.field(ApiFieldLocation.BODY, "currency", ApiFieldDataType.STRING, true));

        var rejected = put(PATH + "/CREATE_ORDER",
            ExternalApiRequests.updateIntegrationApi(fields, ExternalApiSamples.createOrderDefinition(), 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertTrue(rejected.json().path("detail").asString().contains("PAY"));

        var detail = get(PATH + "/CREATE_ORDER").assertOk().as(IntegrationApiResponseBody.class);
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
            put(PATH + "/CREATE_ORDER",
                ExternalApiRequests.updateIntegrationApi(fields, ExternalApiSamples.createOrderDefinition(), 1)),
            "INTEGRATION_API", "DEMO/CREATE_ORDER", 2);

        assertEquals(5, get(PATH + "/CREATE_ORDER").assertOk().as(IntegrationApiResponseBody.class)
            .getRequestFields().size());
    }

    /** 被流程引用时不能删除；未被引用的原子 API 按版本删除。 */
    @Test
    void test_delete() {
        var pay = externalApi.standardApi("PAY");
        externalApi.createOrderApi(demo);
        var query = externalApi.integrationApi(demo, "QUERY_ORDER", ExternalApiSamples.createOrderDefinition());
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());

        delete(PATH + "/CREATE_ORDER", ExternalApiRequests.delete(1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertMutation(delete(PATH + "/QUERY_ORDER", ExternalApiRequests.delete(1)), "INTEGRATION_API",
            "DEMO/QUERY_ORDER", 1);
        assertChange(ChangeTargetType.INTEGRATION_API, query.getIntegrationApiId(), ChangeType.DELETE,
            "DEMO/QUERY_ORDER", 1, 1, ExternalApiRequests.REASON);

        get(PATH + "/QUERY_ORDER").assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(PATH + "/CREATE_ORDER").assertOk();
    }

    /** 接入方不存在时返回 404。 */
    @Test
    void test_integrationNotFound() {
        get(BASE + "/integrations/NOPE/apis/CREATE_ORDER").assertProblem(404, CommonErrorCode.NOT_FOUND);
        post(BASE + "/integrations/NOPE/apis",
            ExternalApiRequests.createIntegrationApi("CREATE_ORDER", ExternalApiSamples.createOrderDefinition()))
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
    }
}
