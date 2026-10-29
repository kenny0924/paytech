package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.workflow.WorkflowActionTemplateManagementApi;
import com.paytech.platform.support.api.workflow.WorkflowDataQueryTemplateApi;
import com.paytech.platform.support.api.workflow.WorkflowDefinitionManagementApi;
import com.paytech.platform.support.api.workflow.request.CreateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.request.PublishWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.ValidateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateValidationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDraftResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowProblemBody;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.test.base.WorkflowDataQueryTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * DATA_QUERY 动作模板与流程发布（REQ-05 R2、B7、AC-04）：合法配置可建可发布；data-query 模板不存在、停用、参数与列不一致、
 * 列被屏蔽时拒绝并说明原因；旧形状 422；发布时按当前 data-query 事实重查，必填参数须映射、映射变量须可用；
 * 动作目录可选择启用中的查询模板。
 *
 * @see WorkflowActionTemplateManagementApi#createActionTemplate(String, String, CreateWorkflowActionTemplateRequestBody)
 * @see WorkflowDefinitionManagementApi#publishDefinition(String, String, String, PublishWorkflowDefinitionRequestBody)
 * @see WorkflowDataQueryTemplateApi#listDataQueryTemplates(String, String, String, Integer, Integer)
 * @see WorkflowActionTemplateManagementApi#validateActionTemplate(String, String, ValidateWorkflowActionTemplateRequestBody)
 */
class WorkflowDataQueryTemplateApiTest extends WorkflowDataQueryTestBase {

    /** 声明了全部约定列的 CLIENT 模板列声明；out_client_no 不在声明中由用例按需追加。 */
    private static final String DECLARED = "[{\"name\":\"satisfied\",\"mask\":\"NONE\"},"
        + "{\"name\":\"missing\",\"mask\":\"NONE\"},{\"name\":\"OUT_CLIENT_TYPE\",\"mask\":\"NONE\"},"
        + "{\"name\":\"out_client_no\",\"mask\":\"NONE\"}]";

    private Operator executor;

    /** 本类公共：一名执行人与一个声明了出参列的 CLIENT 查询模板 GOLIVE_CLIENT。 */
    @BeforeEach
    void setUpTemplateMocks() {
        executor = person();
        queryTemplate("GOLIVE_CLIENT", CLIENT_BODY, CLIENT_PARAMETERS, DECLARED);
    }

    /** 合法 DATA_QUERY 模板可建；流程草稿无问题并可发布，版本快照冻结新形状配置；模板选择只列启用中的模板与参数声明。 */
    @Test
    void test_normalFlow() {
        queryTemplate("GOLIVE_DISABLED", CLIENT_BODY, CLIENT_PARAMETERS, null, 5, QueryTemplateStatus.INACTIVE);
        long check = clientCheckTemplate("CLIENT_CHECK", "GOLIVE_CLIENT");
        long definitionId = createDefinition("CLIENT_GO_LIVE");

        WorkflowDraftResponseBody draft = saveDraft(definitionId, singleCheckGraph(check, executor.accountId()),
            WorkflowGraphJson.textForm("clientId"));
        publish(definitionId).assertOk();
        JsonNode list = get("/workflow-admin/v1/data-query-templates?keyword=GOLIVE", admin).assertOk().json();

        JsonNode snapshot = workflowData.versions(definitionId).stream().reduce((first, last) -> last).map(
            row -> json(row.get("action_snapshot_json"))).orElseThrow();
        assertAll(() -> assertTrue(draft.getProblems().isEmpty(), () -> codes(draft.getProblems()).toString()),
            () -> assertTrue(snapshot.toString().contains("\"queryCode\":\"GOLIVE_CLIENT\""), snapshot::toString),
            () -> assertEquals(1, list.path("total").asInt()),
            () -> assertEquals("GOLIVE_CLIENT", list.path("items").get(0).path("templateCode").asString()),
            () -> assertEquals("clientId", list.path("items").get(0).path("parameters").get(0).path("name")
                .asString()),
            () -> assertEquals("INTEGER", list.path("items").get(0).path("parameters").get(0).path("type").asString()),
            () -> assertTrue(list.path("items").get(0).path("parameters").get(0).path("required").asBoolean()),
            () -> assertEquals(1, list.path("items").get(0).path("versionNo").asInt()),
            () -> assertEquals(5, list.path("items").get(0).path("timeoutSeconds").asInt()),
            () -> assertTrue(list.path("items").get(0).path("body").isMissingNode()));
    }

    /** 新建时按 data-query 事实拒绝：不存在、停用、参数未声明、必填参数未列出、输出列未声明、列被屏蔽；旧键 422。 */
    @Test
    void test_templateConfigRejected() {
        queryTemplate("GOLIVE_OFF", CLIENT_BODY, CLIENT_PARAMETERS, null, 5, QueryTemplateStatus.INACTIVE);
        queryTemplate("GOLIVE_MASKED", CLIENT_BODY, CLIENT_PARAMETERS, "[{\"name\":\"satisfied\",\"mask\":\"FULL\"},"
            + "{\"name\":\"missing\",\"mask\":\"NONE\"},{\"name\":\"out_client_type\",\"mask\":\"NONE\"}]");

        String notFound = rejected(config("GOLIVE_NOPE", List.of("clientId")));
        String inactive = rejected(config("GOLIVE_OFF", List.of("clientId")));
        String parameters = rejected(config("GOLIVE_CLIENT", List.of("agreementId")));
        String undeclared = rejected(dataQueryConfig("GOLIVE_CLIENT", List.of("clientId"),
            outputs("client_name", "TEXT")));
        String masked = rejected(config("GOLIVE_MASKED", List.of("clientId")));
        ObjectNode legacy = config("GOLIVE_CLIENT", List.of("clientId"));
        legacy.putObject("passRule");
        PlatformResponseChecker legacyShape = post(TEMPLATES, admin, new CreateWorkflowActionTemplateRequestBody(
            "LEGACY", "旧形状", WorkflowActionType.DATA_QUERY.getCode(), null, legacy))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("config.passRule");

        assertAll(() -> assertTrue(notFound.contains("查询模板不存在：GOLIVE_NOPE"), notFound),
            () -> assertTrue(inactive.contains("查询模板或其数据源已停用：GOLIVE_OFF"), inactive),
            () -> assertTrue(parameters.contains("未声明参数 agreementId"), parameters),
            () -> assertTrue(parameters.contains("必填参数 clientId"), parameters),
            () -> assertTrue(undeclared.contains("没有返回列 out_client_name"), undeclared),
            () -> assertTrue(masked.contains("列 satisfied 已屏蔽"), masked),
            () -> assertTrue(legacyShape.json().path("errors").isArray()),
            () -> assertEquals(0L, workflowData.rowCount()));
    }

    /** 配置校验（不保存）返回逐项问题：itemKey 为参数 key、输出名或空；合法配置为空列表；结构错误 422；不写库。 */
    @Test
    void test_validationProblems() {
        queryTemplate("GOLIVE_OFF", CLIENT_BODY, CLIENT_PARAMETERS, null, 5, QueryTemplateStatus.INACTIVE);

        List<WorkflowProblemBody> valid = validate(config("GOLIVE_CLIENT", List.of("clientId")));
        List<WorkflowProblemBody> columns = validate(dataQueryConfig("GOLIVE_CLIENT", List.of("agreementId"),
            outputs("client_name", "TEXT")));
        List<WorkflowProblemBody> inactive = validate(config("GOLIVE_OFF", List.of("clientId")));
        ObjectNode legacy = config("GOLIVE_CLIENT", List.of("clientId"));
        legacy.putObject("passRule");
        post(TEMPLATES + "/validation", admin, new ValidateWorkflowActionTemplateRequestBody(
            WorkflowActionType.DATA_QUERY.getCode(), legacy)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("config.passRule");

        assertAll(() -> assertTrue(valid.isEmpty(), () -> codes(valid).toString()),
            () -> assertEquals(List.of("DATA_QUERY_PARAMETER_UNDECLARED:agreementId",
                "DATA_QUERY_PARAMETER_MISSING:clientId", "DATA_QUERY_OUTPUT_UNDECLARED:client_name"),
                columns.stream().map(problem -> problem.getCode() + ":" + problem.getItemKey()).toList()),
            () -> assertTrue(columns.stream().allMatch(problem -> problem.getNodeKey() == null)),
            () -> assertEquals("查询模板 GOLIVE_CLIENT 没有返回列 out_client_name", columns.get(2).getMessage()),
            () -> assertEquals(List.of(WorkflowProblemCode.DATA_QUERY_TEMPLATE_INACTIVE.getCode()), codes(inactive)),
            () -> assertEquals(null, inactive.get(0).getItemKey()),
            () -> assertEquals(0L, workflowData.rowCount()));
    }

    /** 校验 DATA_QUERY 配置，返回问题列表。 */
    private List<WorkflowProblemBody> validate(JsonNode config) {
        return post(TEMPLATES + "/validation", admin, new ValidateWorkflowActionTemplateRequestBody(
            WorkflowActionType.DATA_QUERY.getCode(), config)).assertOk()
            .as(WorkflowActionTemplateValidationResponseBody.class).getProblems();
    }

    /** 发布时重查：必填参数未映射、映射不可用变量、data-query 模板在保存后被停用，均拒绝发布并给出问题码。 */
    @Test
    void test_publicationRechecksFacts() {
        long check = clientCheckTemplate("CLIENT_CHECK", "GOLIVE_CLIENT");
        long unmapped = createDefinition("UNMAPPED");
        WorkflowDraftResponseBody missing = saveDraft(unmapped, WorkflowGraphJson.graph().task("n1", "系统检查")
            .action("check", check, executor.accountId(), true).end("end").chain("start", "n1", "end").build(),
            WorkflowGraphJson.textForm("clientId"));
        long wrongVariable = createDefinition("WRONG_VARIABLE");
        WorkflowDraftResponseBody unavailable = saveDraft(wrongVariable, WorkflowGraphJson.graph().task("n1", "系统检查")
            .action("check", check, executor.accountId(), true).input("clientId", "VARIABLE", "start.nope")
            .end("end").chain("start", "n1", "end").build(), WorkflowGraphJson.textForm("clientId"));
        long disabledLater = createDefinition("DISABLED_LATER");
        saveDraft(disabledLater, singleCheckGraph(check, executor.accountId()), WorkflowGraphJson.textForm("clientId"));
        dataQueryRows.changeTemplateStatus(dataQueryRows.templateId("GOLIVE_CLIENT"),
            QueryTemplateStatus.INACTIVE.getCode());

        publish(unmapped).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        publish(wrongVariable).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        publish(disabledLater).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        List<String> afterDisable = codes(get(DEFINITIONS + "/" + disabledLater, admin).assertOk()
            .as(WorkflowDefinitionDetailResponseBody.class)
            .getProblems());

        assertAll(() -> assertTrue(codes(missing.getProblems()).contains(
                WorkflowProblemCode.DATA_QUERY_INPUT_MISSING.getCode()), () -> codes(missing.getProblems()).toString()),
            () -> assertTrue(codes(unavailable.getProblems()).contains(
                WorkflowProblemCode.VARIABLE_UNAVAILABLE.getCode()), () -> codes(unavailable.getProblems()).toString()),
            () -> assertTrue(afterDisable.contains(WorkflowProblemCode.DATA_QUERY_TEMPLATE_INACTIVE.getCode()),
                afterDisable::toString));
    }

    /** CLIENT 模板的 DATA_QUERY 配置，输出 client_type（ENUM）与 client_no（TEXT）。 */
    private static ObjectNode config(String queryCode, List<String> parameters) {
        return dataQueryConfig(queryCode, parameters, enumOutput(outputs("client_no", "TEXT"), "client_type",
            "ORGANIZATION", "INDIVIDUAL"));
    }

    /** 新建被拒绝，返回中文原因。 */
    private String rejected(JsonNode config) {
        return post(TEMPLATES, admin, new CreateWorkflowActionTemplateRequestBody("BAD_" + Long.toString(System.nanoTime() % 1_000_000_000L),
            "拒绝", WorkflowActionType.DATA_QUERY.getCode(), null, config))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED).json().path("detail").asString();
    }

    /** 问题码列表。 */
    private static List<String> codes(List<WorkflowProblemBody> problems) {
        return problems.stream().map(WorkflowProblemBody::getCode).toList();
    }

    /** JSON 列转为树。 */
    private static JsonNode json(Object column) {
        return JsonMapper.builder().build().readTree(String.valueOf(column));
    }
}
