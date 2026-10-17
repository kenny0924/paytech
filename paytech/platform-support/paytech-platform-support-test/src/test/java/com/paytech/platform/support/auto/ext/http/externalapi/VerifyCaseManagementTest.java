package com.paytech.platform.support.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.VerifyCaseStatus;
import com.paytech.platform.support.api.externalapi.IntegrationVerifyManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.response.VerifyCaseResponseBody;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiFixtures;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;

import tools.jackson.databind.JsonNode;

/**
 * 验证用例管理：创建、详情与列表（未运行状态）、修改递增版本、账号不存在，以及步骤静态校验矩阵：
 * 结构与步骤 ID、未实现的标准 API、模板变量（未知、引用后续步骤、对象嵌在文字中）、输入结构、期望二选一、
 * 出口与输出路径、匹配方式与期望值。
 *
 * @see IntegrationVerifyManagementApi#listVerifyCases(String, String, String, Integer, Integer, String)
 * @see IntegrationVerifyManagementApi#getVerifyCase(String, String, String, String)
 * @see IntegrationVerifyManagementApi#createVerifyCase(String, String, String, CreateVerifyCaseRequestBody)
 * @see IntegrationVerifyManagementApi#updateVerifyCase(String, String, String, String, UpdateVerifyCaseRequestBody)
 * @see IntegrationVerifyManagementApi#deleteVerifyCase(String, String, String, String,
 *      com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody)
 * @see IntegrationVerifyManagementApi#validateVerifyCase(String, String, String, ValidateVerifyCaseRequestBody)
 */
class VerifyCaseManagementTest extends ExternalApiTestBase {

    private static final String PATH = BASE + "/integrations/DEMO/verify-cases";
    private static final String STEP = """
        {"id":"pay","standardApiCode":"PAY","input":{"paymentContext":{"attemptId":"A1","amount":"1.00","vaultToken":"T${run.code}"}},
         "expect":{"executeStatus":"SUCCESS","returnId":"done","outputs":[{"path":"paymentStatus","match":"ONE_OF","value":["PAID"]}]}}""";

    private Operator admin;

    /** 本类公共：管理员、接入方 DEMO 实现 PAY（单节点流程），验证账号 MAIN。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = administrator();
        Integration demo = externalApi.integration("DEMO");
        externalApi.integrationApi(demo, "CALL", ExternalApiSamples.createOrderDefinition());
        externalApi.implementation(demo, externalApi.standardApi("PAY"), ExternalApiFixtures.flow("call-create-order"));
        post(BASE + "/integrations/DEMO/verify-accounts", admin, new CreateVerifyAccountRequestBody("MAIN", "主账号",
            "DEMO", "A1", "A1", Map.of("outletId", "o-1"), Map.of("apiKey", "k"), "原因")).assertOk();
    }

    /** 创建后详情与列表为未运行；修改递增版本；账号不存在 400。 */
    @Test
    void test_createGetListUpdate() {
        assertMutation(post(PATH, admin, create("CASE_A", "[" + STEP + "]")), "INTEGRATION_VERIFY_CASE",
            "DEMO/CASE_A", 1);
        var detail = get(PATH + "/CASE_A", admin).assertOk().as(VerifyCaseResponseBody.class);
        assertAll(() -> assertEquals("MAIN", detail.getAccountCode()),
            () -> assertEquals(VerifyCaseStatus.NOT_RUN.getCode(), detail.getVerifyStatus()),
            () -> assertNull(detail.getLatestRun()), () -> assertEquals(1, detail.getSteps().size()));
        JsonNode list = get(PATH + "?keyword=CASE", admin).assertOk().json();
        assertAll(() -> assertEquals(1, list.path("total").asInt()),
            () -> assertEquals(List.of("PAY"), List.of(list.path("items").get(0).path("standardApiCodes").get(0).asString())));

        assertMutation(put(PATH + "/CASE_A", admin, new UpdateVerifyCaseRequestBody("改名", "说明", "MAIN",
            steps("[" + STEP + "]"), 1, "修改")), "INTEGRATION_VERIFY_CASE", "DEMO/CASE_A", 2);
        post(PATH, admin, new CreateVerifyCaseRequestBody("CASE_B", "用例", null, "NOPE", steps("[" + STEP + "]"),
            "原因")).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(PATH, admin, create("CASE_A", "[" + STEP + "]")).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
    }

    /** 静态校验矩阵：保存返回 422，校验接口返回全部问题。 */
    @Test
    void test_validationMatrix() {
        String invalid = """
            [{"id":"Bad","standardApiCode":"NOPE","input":{"x":"${steps.later.input.a}"},"expect":{"executeStatus":"MAYBE","problem":{"status":422}}},
             {"id":"pay","standardApiCode":"PAY","input":{"paymentContext":{"attemptId":"A1","amount":"1.00","vaultToken":"T-${account.identity}"}},
              "expect":{"executeStatus":"SUCCESS"}},
             {"id":"later","standardApiCode":"PAY","input":{"paymentContext":{"attemptId":"A1","vaultToken":"T"}},
              "expect":{"executeStatus":"SUCCESS","returnId":"nowhere","outputs":[
                {"path":"unknownField","match":"EXISTS"},{"path":"paymentStatus","match":"LIKE"},
                {"path":"paymentStatus","match":"REGEX","value":"("},{"path":"paymentStatus","match":"EXISTS","value":"x"}]}}]""";
        var issues = issues(post(PATH + "/validate", admin, new ValidateVerifyCaseRequestBody("MAIN", steps(invalid))));
        assertEquals(List.of("EXPECT_INVALID@steps[0].expect",
            "INPUT_SCHEMA_MISMATCH@steps[2].input.paymentContext.amount",
            "MATCH_INVALID@steps[2].expect.outputs[1].match",
            "OUTPUT_PATH_UNKNOWN@steps[2].expect.outputs[0].path",
            "RETURN_UNKNOWN@steps[2].expect.returnId",
            "STANDARD_API_NOT_IMPLEMENTED@steps[0].standardApiCode",
            "STEP_ID_INVALID@steps[0].id",
            "TEMPLATE_VARIABLE_INVALID@steps[0].input",
            "TEMPLATE_VARIABLE_INVALID@steps[1].input",
            "VALUE_INVALID@steps[2].expect.outputs[2].value",
            "VALUE_INVALID@steps[2].expect.outputs[3].value"), issues);
        post(PATH, admin, create("BAD", invalid)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("steps[0].id");
        assertEquals(List.of("STEPS_INVALID@steps"),
            issues(post(PATH + "/validate", admin, new ValidateVerifyCaseRequestBody("MAIN", steps("[]")))));
        assertEquals(List.of("ACCOUNT_NOT_FOUND@accountCode"),
            issues(post(PATH + "/validate", admin, new ValidateVerifyCaseRequestBody("NOPE", steps("[" + STEP + "]")))));
        assertTrue(issues(post(PATH + "/validate", admin, new ValidateVerifyCaseRequestBody("MAIN",
            steps("[" + STEP + "]")))).isEmpty());
    }

    private static CreateVerifyCaseRequestBody create(String code, String steps) {
        return new CreateVerifyCaseRequestBody(code, "用例", null, "MAIN", steps(steps), "原因");
    }

    private static JsonNode steps(String steps) {
        return ExternalApiSamples.object("{\"s\":" + steps + "}").get("s");
    }
}
