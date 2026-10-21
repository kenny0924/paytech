package com.paytech.channel.connector.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.api.externalapi.IntegrationScriptManagementApi;
import com.paytech.channel.connector.api.externalapi.request.*;
import com.paytech.channel.connector.api.externalapi.response.IntegrationScriptResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationScriptSummary;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.ConfigurationField;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiRequests;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;

import tools.jackson.core.type.TypeReference;

/**
 * 公共脚本管理：创建与详情、按阶段筛选、修改阶段或参数时复核引用的原子 API、被引用时删除保护、源码字节上限。
 *
 * @see IntegrationScriptManagementApi#listIntegrationScripts(String, Integer, Integer, String, String)
 * @see IntegrationScriptManagementApi#getIntegrationScript(String, String)
 * @see IntegrationScriptManagementApi#createIntegrationScript(String, String, CreateIntegrationScriptRequestBody)
 * @see IntegrationScriptManagementApi#updateIntegrationScript(String, String, String,
 *      UpdateIntegrationScriptRequestBody)
 * @see IntegrationScriptManagementApi#deleteIntegrationScript(String, String, String,
 *      DeleteExternalApiConfigRequestBody)
 * @see IntegrationScriptManagementApi#validateIntegrationScript(String, ValidateIntegrationScriptRequestBody)
 */
class IntegrationScriptManagementTest extends ExternalApiTestBase {

    private static final String PATH = BASE + "/integrations/DEMO/scripts";
    private static final String SIGN = PATH + "/" + ExternalApiSamples.SIGN_SCRIPT;

    private Integration demo;

    /** 本类公共：一个接入方 DEMO。 */
    @BeforeEach
    void setUpClassMocks() {
        demo = externalApi.integration("DEMO");
    }

    /** 创建后详情返回参数与源码，列表按阶段筛选，并同事务写入 CREATE 变更记录（源码只记摘要）。 */
    @Test
    void test_createAndList() {
        assertMutation(post(PATH, ExternalApiRequests.createScript("DEMO_SIGN", ScriptExecutionStage.REQUEST)),
            "INTEGRATION_SCRIPT", "DEMO/DEMO_SIGN", 1);
        var change = assertChange(ChangeTargetType.INTEGRATION_SCRIPT, externalApiData.scriptId("DEMO", "DEMO_SIGN"),
            ChangeType.CREATE, "DEMO/DEMO_SIGN", 0, 1, ExternalApiRequests.REASON);
        assertAll(() -> assertTrue(changedFields(change).contains(ConfigurationField.SOURCE_DIGEST.getCode())),
            () -> assertFalse(change.toString().contains(ExternalApiSamples.signSource()), "变更摘要不应包含脚本源码"));
        assertMutation(post(PATH, ExternalApiRequests.createScript("DEMO_VERIFY", ScriptExecutionStage.RESPONSE)),
            "INTEGRATION_SCRIPT", "DEMO/DEMO_VERIFY", 1);

        var detail = get(SIGN).assertOk().as(IntegrationScriptResponseBody.class);
        var requestStage = get(PATH + "?executionStage=" + ScriptExecutionStage.REQUEST.getCode()).assertOk()
            .as(new TypeReference<PagingResponseBody<IntegrationScriptSummary>>() {});

        assertAll(() -> assertEquals(ScriptExecutionStage.REQUEST.getCode(), detail.getExecutionStage()),
            () -> assertEquals("key", detail.getParameterDefinition().getFirst().getName()),
            () -> assertEquals("PROTECTED_HANDLE", detail.getParameterDefinition().getFirst().getDataType()),
            () -> assertEquals(ExternalApiSamples.signSource(), detail.getSourceCode()),
            () -> assertEquals(1, requestStage.total()), () -> assertEquals(List.of("DEMO_SIGN"),
                requestStage.items().stream().map(IntegrationScriptSummary::getScriptCode).toList()));
    }

    /** 被原子 API 按 REQUEST 阶段引用时，改为 RESPONSE 或删掉必填参数都被拒绝；版本不变。 */
    @Test
    void test_updateBreakingReference() {
        externalApi.signScript(demo, ScriptExecutionStage.REQUEST);
        externalApi.integrationApi(demo, ExternalApiSamples.CREATE_ORDER,
            ExternalApiSamples.createOrderDefinitionWithScript(ExternalApiSamples.SIGN_SCRIPT));
        var renamed = List.of(new ScriptParameterBody("secret", "PROTECTED_HANDLE", true));

        var stage =
            put(SIGN, ExternalApiRequests.updateScript(ScriptExecutionStage.RESPONSE, ExternalApiSamples.signParameters(), 1))
                .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        put(SIGN, ExternalApiRequests.updateScript(ScriptExecutionStage.REQUEST, renamed, 1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertTrue(stage.json().path("detail").asString().contains(ExternalApiSamples.CREATE_ORDER));
        var detail = get(SIGN).assertOk().as(IntegrationScriptResponseBody.class);
        assertAll(() -> assertEquals(1, detail.getVersion()), () -> assertEquals(ScriptExecutionStage.REQUEST.getCode(), detail.getExecutionStage()),
            () -> assertEquals(List.of(ExternalApiSamples.CREATE_ORDER), detail.getReferencedApiCodes()));
    }

    /** 未被引用时可以改阶段与参数，版本递增；旧版本再提交返回 409。 */
    @Test
    void test_updateUnreferenced() {
        var script = externalApi.signScript(demo, ScriptExecutionStage.REQUEST);

        assertMutation(put(SIGN, ExternalApiRequests.updateScript(ScriptExecutionStage.RESPONSE, List.of(), 1)),
            "INTEGRATION_SCRIPT", "DEMO/DEMO_SIGN", 2);
        var change = assertChange(ChangeTargetType.INTEGRATION_SCRIPT, script.getIntegrationScriptId(),
            ChangeType.UPDATE, "DEMO/DEMO_SIGN", 1, 2, ExternalApiRequests.REASON);
        assertTrue(changedFields(change).contains(ConfigurationField.EXECUTION_STAGE.getCode()));
        put(SIGN, ExternalApiRequests.updateScript(ScriptExecutionStage.REQUEST, List.of(), 1)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);

        var detail = get(SIGN).assertOk().as(IntegrationScriptResponseBody.class);
        assertAll(() -> assertEquals(ScriptExecutionStage.RESPONSE.getCode(), detail.getExecutionStage()),
            () -> assertTrue(detail.getParameterDefinition().isEmpty()));
    }

    /** 被原子 API 引用时不能删除；去掉引用的原子 API 后可以删除。 */
    @Test
    void test_delete() {
        externalApi.signScript(demo, ScriptExecutionStage.REQUEST);
        externalApi.integrationApi(demo, ExternalApiSamples.CREATE_ORDER,
            ExternalApiSamples.createOrderDefinitionWithScript(ExternalApiSamples.SIGN_SCRIPT));

        var rejected = delete(SIGN, ExternalApiRequests.delete(1)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertTrue(rejected.json().path("detail").asString().contains(ExternalApiSamples.CREATE_ORDER));
        assertMutation(delete(BASE + "/integrations/DEMO/apis/CREATE_ORDER", ExternalApiRequests.delete(1)),
            "INTEGRATION_API", "DEMO/CREATE_ORDER", 1);
        assertMutation(delete(SIGN, ExternalApiRequests.delete(1)), "INTEGRATION_SCRIPT", "DEMO/DEMO_SIGN", 1);

        get(SIGN).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 源码按 UTF-8 字节计上限：字符数未超但字节超过 32 KiB 时校验接口报告问题，保存返回 422。 */
    @Test
    void test_sourceTooLarge() {
        String source = "签".repeat(11_000);

        var issues =
            issues(validate(PATH + "/validate", ExternalApiRequests.validateScript("DEMO_SIGN", ScriptExecutionStage.REQUEST, source)));
        post(PATH,
            ExternalApiRequests.createScript("DEMO_SIGN", ScriptExecutionStage.REQUEST, ExternalApiSamples.signParameters(), source))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("sourceCode");

        assertEquals(List.of("SOURCE_TOO_LARGE@sourceCode"), issues);
        get(SIGN).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }
}
