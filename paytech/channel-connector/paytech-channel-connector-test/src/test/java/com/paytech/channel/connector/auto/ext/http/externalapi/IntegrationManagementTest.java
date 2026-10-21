package com.paytech.channel.connector.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.api.externalapi.IntegrationManagementApi;
import com.paytech.channel.connector.api.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.IntegrationEndpointBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateIntegrationRequestBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationSummary;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiRequests;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;

import tools.jackson.core.type.TypeReference;

/**
 * 接入方管理：创建与详情、出站地址校验、被原子 API 引用的地址保护、下级配置删除保护、版本冲突、操作者请求头与变更记录。
 *
 * @see IntegrationManagementApi#integrations(Integer, Integer, String)
 * @see IntegrationManagementApi#integration(String)
 * @see IntegrationManagementApi#createIntegration(String, CreateIntegrationRequestBody)
 * @see IntegrationManagementApi#updateIntegration(String, String, UpdateIntegrationRequestBody)
 * @see IntegrationManagementApi#deleteIntegration(String, String, DeleteExternalApiConfigRequestBody)
 */
class IntegrationManagementTest extends ExternalApiTestBase {

    private static final String PATH = BASE + "/integrations";


    /** 创建后详情返回出站地址与零计数，并同事务写入 CREATE 变更记录（出站地址只记摘要）。 */
    @Test
    void test_createAndGet() {
        assertMutation(post(PATH, ExternalApiRequests.createIntegration("DEMO")), "INTEGRATION", "DEMO", 1);
        var change = assertChange(ChangeTargetType.INTEGRATION, externalApiData.integrationId("DEMO"),
            ChangeType.CREATE, "DEMO", 0, 1, ExternalApiRequests.REASON);
        assertAll(() -> assertTrue(changedFields(change).contains(ConfigurationField.ENDPOINTS_DIGEST.getCode())),
            () -> assertFalse(change.toString().contains("gateway.example.test"), "变更摘要不应包含出站地址原文"));

        var detail = get(PATH + "/DEMO").assertOk().as(IntegrationResponseBody.class);

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

        var page = get(PATH + "?keyword=DEM").assertOk()
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

        post(PATH, ExternalApiRequests.createIntegration("DEMO", endpoints))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("endpoints[0].baseUrl")
            .assertFieldError("endpoints[1].baseUrl").assertFieldError("endpoints[2].endpointCode");

        get(PATH + "/DEMO").assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 被原子 API 引用的地址代码不能移除；保留该代码的修改成功，详情列出引用方。 */
    @Test
    void test_updateKeepsReferencedEndpoint() {
        var demo = externalApi.integration("DEMO");
        externalApi.createOrderApi(demo);
        var replaced = List.of(new IntegrationEndpointBody("NEW_GATEWAY", "https://new.example.test"));
        var extended = List.of(new IntegrationEndpointBody(ExternalApiSamples.ENDPOINT, "https://moved.example.test"),
            new IntegrationEndpointBody("NEW_GATEWAY", "https://new.example.test"));

        var rejected = put(PATH + "/DEMO", ExternalApiRequests.updateIntegration("DEMO", replaced, 1))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertTrue(rejected.json().path("detail").asString().contains(ExternalApiSamples.CREATE_ORDER));

        assertMutation(put(PATH + "/DEMO", ExternalApiRequests.updateIntegration("DEMO 2", extended, 1)),
            "INTEGRATION", "DEMO", 2);
        assertChange(ChangeTargetType.INTEGRATION, demo.getIntegrationId(), ChangeType.UPDATE, "DEMO", 1, 2,
            ExternalApiRequests.REASON);
        var detail = get(PATH + "/DEMO").assertOk().as(IntegrationResponseBody.class);
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

        put(PATH + "/DEMO", ExternalApiRequests.updateIntegration("改名", ExternalApiSamples.endpoints(), 3))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);

        assertEquals("DEMO 渠道",
            get(PATH + "/DEMO").assertOk().as(IntegrationResponseBody.class).getIntegrationName());
    }

    /** 仍有原子 API 时不能删除；空接入方按版本删除。 */
    @Test
    void test_delete() {
        var demo = externalApi.integration("DEMO");
        externalApi.createOrderApi(demo);
        externalApi.integration("EMPTY");

        var rejected =
            delete(PATH + "/DEMO", ExternalApiRequests.delete(1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertTrue(rejected.json().path("detail").asString().contains("原子 API 1 个"));
        assertMutation(delete(PATH + "/EMPTY", ExternalApiRequests.delete(1)), "INTEGRATION", "EMPTY", 1);

        get(PATH + "/EMPTY").assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(PATH + "/DEMO").assertOk();
    }

    /** 写命令缺少或带非法操作者请求头时 400，不落库、不写变更记录。 */
    @Test
    void test_actorHeaderRequired() {
        call(HttpMethod.POST, PATH, null, ExternalApiRequests.createIntegration("DEMO")).assertStatus(400);
        call(HttpMethod.POST, PATH, "actor-1", ExternalApiRequests.createIntegration("DEMO")).assertProblem(400,
            CommonErrorCode.INVALID_REQUEST);

        get(PATH + "/DEMO").assertProblem(404, CommonErrorCode.NOT_FOUND);
        assertEquals(0, externalApiData.countChanges(ChangeTargetType.INTEGRATION.getCode()));
    }
}
