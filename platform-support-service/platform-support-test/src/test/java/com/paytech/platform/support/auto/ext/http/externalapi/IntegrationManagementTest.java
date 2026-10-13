package com.paytech.platform.support.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.api.externalapi.IntegrationManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.IntegrationEndpointBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationRequestBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationSummary;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiRequests;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;

import tools.jackson.core.type.TypeReference;

/**
 * 接入方管理：创建与详情、出站地址校验、被原子 API 引用的地址保护、下级配置删除保护与版本冲突。
 *
 * @see IntegrationManagementApi#integrations(String, String, Integer, Integer, String)
 * @see IntegrationManagementApi#integration(String, String, String)
 * @see IntegrationManagementApi#createIntegration(String, String, CreateIntegrationRequestBody)
 * @see IntegrationManagementApi#updateIntegration(String, String, String, UpdateIntegrationRequestBody)
 * @see IntegrationManagementApi#deleteIntegration(String, String, String, DeleteExternalApiConfigRequestBody)
 */
class IntegrationManagementTest extends ExternalApiTestBase {

    private static final String PATH = BASE + "/integrations";

    private Operator admin;

    /** 本类公共：一名平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = administrator();
    }

    /** 创建后详情返回出站地址与零计数，并写入成功审计。 */
    @Test
    void test_createAndGet() {
        assertMutation(post(PATH, admin, ExternalApiRequests.createIntegration("DEMO")), "INTEGRATION", "DEMO", 1);
        auditChecker.assertEvent(requestId, "OPERATIONS_INTEGRATION_CREATE", AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), "INTEGRATION",
            "DEMO");

        var detail = get(PATH + "/DEMO", admin).assertOk().as(IntegrationResponseBody.class);

        assertAll(() -> assertEquals("DEMO 渠道", detail.getIntegrationName()),
            () -> assertEquals(1, detail.getEndpoints().size()),
            () -> assertEquals(ExternalApiSamples.ENDPOINT, detail.getEndpoints().getFirst().getEndpointCode()),
            () -> assertEquals("https://gateway.example.test/api", detail.getEndpoints().getFirst().getBaseUrl()),
            () -> assertTrue(detail.getEndpoints().getFirst().getReferencedApiCodes().isEmpty()),
            () -> assertEquals(0, detail.getApiCount()), () -> assertEquals(0, detail.getScriptCount()),
            () -> assertEquals(0, detail.getImplementationCount()), () -> assertEquals(1, detail.getVersion()));
    }

    /** 列表统计出站地址、原子 API、脚本与已实现的标准 API，关键字按编码或名称筛选。 */
    @Test
    void test_listCounts() {
        var pay = externalApi.standardApi("PAY");
        var demo = externalApi.integration("DEMO");
        externalApi.integration("OTHER");
        externalApi.createOrderApi(demo);
        externalApi.signScript(demo, ScriptExecutionStage.REQUEST);
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());

        var page = get(PATH + "?keyword=DEM", admin).assertOk()
            .as(new TypeReference<PagingResponseBody<IntegrationSummary>>() {});

        assertEquals(1, page.total());
        var summary = page.items().getFirst();
        assertAll(() -> assertEquals("DEMO", summary.getIntegrationCode()),
            () -> assertEquals(1, summary.getEndpointCount()), () -> assertEquals(1, summary.getApiCount()),
            () -> assertEquals(1, summary.getScriptCount()), () -> assertEquals(List.of("PAY"), summary
                .getImplementedStandardApis().stream().map(reference -> reference.getStandardApiCode()).toList()));
    }

    /** 出站地址协议必须在允许列表中（默认 https，本机 mock 另允许 http）、不带查询串，代码不能重复；一次返回全部位置。 */
    @Test
    void test_invalidEndpoints() {
        var endpoints = List.of(new IntegrationEndpointBody("PLAIN", "ftp://gateway.example.test"),
            new IntegrationEndpointBody("QUERY", "https://gateway.example.test/api?key=1"),
            new IntegrationEndpointBody("PLAIN", "https://other.example.test"));

        post(PATH, admin, ExternalApiRequests.createIntegration("DEMO", endpoints))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("endpoints[0].baseUrl")
            .assertFieldError("endpoints[1].baseUrl").assertFieldError("endpoints[2].endpointCode");

        get(PATH + "/DEMO", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 被原子 API 引用的地址代码不能移除；保留该代码的修改成功，详情列出引用方。 */
    @Test
    void test_updateKeepsReferencedEndpoint() {
        var demo = externalApi.integration("DEMO");
        externalApi.createOrderApi(demo);
        var replaced = List.of(new IntegrationEndpointBody("NEW_GATEWAY", "https://new.example.test"));
        var extended = List.of(new IntegrationEndpointBody(ExternalApiSamples.ENDPOINT, "https://moved.example.test"),
            new IntegrationEndpointBody("NEW_GATEWAY", "https://new.example.test"));

        var rejected = put(PATH + "/DEMO", admin, ExternalApiRequests.updateIntegration("DEMO", replaced, 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertTrue(rejected.json().path("detail").asString().contains(ExternalApiSamples.CREATE_ORDER));

        assertMutation(put(PATH + "/DEMO", admin, ExternalApiRequests.updateIntegration("DEMO 2", extended, 1)),
            "INTEGRATION", "DEMO", 2);
        var detail = get(PATH + "/DEMO", admin).assertOk().as(IntegrationResponseBody.class);
        assertAll(() -> assertEquals("DEMO 2", detail.getIntegrationName()),
            () -> assertEquals("https://moved.example.test", detail.getEndpoints().getFirst().getBaseUrl()),
            () -> assertEquals(List.of(ExternalApiSamples.CREATE_ORDER),
                detail.getEndpoints().getFirst().getReferencedApiCodes()),
            () -> assertTrue(detail.getEndpoints().get(1).getReferencedApiCodes().isEmpty()));
    }

    /** 旧版本修改返回 409，数据不变。 */
    @Test
    void test_staleVersion() {
        externalApi.integration("DEMO");

        put(PATH + "/DEMO", admin, ExternalApiRequests.updateIntegration("改名", ExternalApiSamples.endpoints(), 3))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);

        assertEquals("DEMO 渠道",
            get(PATH + "/DEMO", admin).assertOk().as(IntegrationResponseBody.class).getIntegrationName());
    }

    /** 仍有原子 API 时不能删除；空接入方按版本删除。 */
    @Test
    void test_delete() {
        var demo = externalApi.integration("DEMO");
        externalApi.createOrderApi(demo);
        externalApi.integration("EMPTY");

        var rejected =
            delete(PATH + "/DEMO", admin, ExternalApiRequests.delete(1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertTrue(rejected.json().path("detail").asString().contains("原子 API 1 个"));
        assertMutation(delete(PATH + "/EMPTY", admin, ExternalApiRequests.delete(1)), "INTEGRATION", "EMPTY", 1);

        get(PATH + "/EMPTY", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(PATH + "/DEMO", admin).assertOk();
    }

    /** 只读人员不能创建。 */
    @Test
    void test_readerCannotWrite() {
        post(PATH, reader(), ExternalApiRequests.createIntegration("DEMO")).assertProblem(403,
            OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);

        get(PATH + "/DEMO", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }
}
