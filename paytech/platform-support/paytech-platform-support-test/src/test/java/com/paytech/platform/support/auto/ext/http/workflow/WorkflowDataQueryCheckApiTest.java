package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.workflow.WorkflowInstanceApi;
import com.paytech.platform.support.api.workflow.request.StartWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.request.SubmitWorkflowActionRequestBody;
import com.paytech.platform.support.api.workflow.request.TerminateWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDataQueryCheckBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody.ActionRunBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.test.base.WorkflowDataQueryTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.dataquery.DataQueryFixture;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckFailure;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;

import tools.jackson.databind.JsonNode;

/**
 * DATA_QUERY 运行（REQ-05 R3—R6、AC-05—AC-08、AC-10、B3）：发起后提交即首查；满足时系统完成、只写声明输出并按变量走
 * 排他网关，级联的下一个 DATA_QUERY 继续首查；未满足与各类失败保持待处理并记录检查列，不产生通知、不挡人工操作；
 * DATA_QUERY 不能人工完成；我的待办与详情返回动作类型和检查块。
 *
 * @see WorkflowInstanceApi#startInstance(String, String, StartWorkflowInstanceRequestBody)
 * @see WorkflowInstanceApi#getInstance(String, String, String)
 * @see WorkflowInstanceApi#listMyTasks(String, String, String, String, Integer, Integer)
 * @see WorkflowInstanceApi#submitAction(String, String, String, SubmitWorkflowActionRequestBody)
 */
class WorkflowDataQueryCheckApiTest extends WorkflowDataQueryTestBase {

    private Operator executor;

    private long check;

    /** 本类公共：一名跟进人、CLIENT 查询模板 GOLIVE_CLIENT 与引用它的 DATA_QUERY 动作模板。 */
    @BeforeEach
    void setUpCheckMocks() {
        executor = person();
        clientQuery("GOLIVE_CLIENT");
        check = clientCheckTemplate("CLIENT_CHECK", "GOLIVE_CLIENT");
    }

    /** 满足：两个 DATA_QUERY 节点级联自动完成，输出只含声明的列，提交人为空，实例完成；检查块为 SATISFIED。 */
    @Test
    void test_satisfiedCompletesAndCascades() {
        long typeOnly = template("CLIENT_TYPE_ONLY", WorkflowActionType.DATA_QUERY.getCode(), dataQueryConfig(
            "GOLIVE_CLIENT", List.of("clientId"), enumOutput(outputs(), "client_type", "ORGANIZATION", "INDIVIDUAL")));
        JsonNode graph = WorkflowGraphJson.graph().task("n1", "起草检查").action("n1check", check, executor.accountId(), true)
            .input("clientId", "VARIABLE", "start.clientId").task("n2", "生效检查")
            .action("n2check", typeOnly, executor.accountId(), true).input("clientId", "CONSTANT", "1003")
            .end("end").chain("start", "n1", "n2", "end").build();
        published("CASCADE", graph, WorkflowGraphJson.textForm("clientId"));

        long instanceId = started("CASCADE", "1001");
        awaitChecks();

        Map<String, Object> first = actionRow(instanceId, "n1check");
        Map<String, Object> second = actionRow(instanceId, "n2check");
        ActionRunBody body = actionBody(instanceId, "n1check");
        assertAll(() -> assertEquals(WorkflowInstanceStatus.COMPLETED.getCode(), instanceStatus(instanceId)),
            () -> assertEquals(List.of("n1#1=PASSED", "n2#1=PASSED", "end#1=PASSED"), nodeTrail(instanceId)),
            () -> assertEquals(ActionRunStatus.COMPLETED.getCode(), first.get("status")),
            () -> assertNull(first.get("submitted_by")), () -> assertNotNull(first.get("submitted_time")),
            () -> assertEquals(json("{\"client_no\":\"FX-CL-1001\",\"client_type\":\"ORGANIZATION\"}"),
                json(first.get("output_json"))),
            () -> assertEquals(json("{\"client_type\":\"ORGANIZATION\"}"), json(second.get("output_json"))),
            () -> assertEquals(DataQueryCheckResult.SATISFIED.getCode(), first.get("check_result")),
            () -> assertNotNull(first.get("last_check_success_time")),
            () -> assertEquals(DataQueryCheckResult.SATISFIED.getCode(), body.getDataQueryCheck().getResult()),
            () -> assertEquals("GOLIVE_CLIENT", body.getDataQueryCheck().getQueryCode()),
            () -> assertNull(body.getSubmittedBy()),
            () -> assertEquals(0, workflowData.callbacks(instanceId).size()));
    }

    /** 未满足：动作保持待处理，记录原因码与检查时间；我的待办带动作类型，详情检查块给出 missing；不能人工完成（B3）。 */
    @Test
    void test_unsatisfiedKeepsPending() {
        published("SINGLE", singleCheckGraph(check, executor.accountId()), WorkflowGraphJson.textForm("clientId"));

        long instanceId = started("SINGLE", "9999");
        awaitChecks();

        Map<String, Object> row = actionRow(instanceId, "check");
        WorkflowDataQueryCheckBody block = actionBody(instanceId, "check").getDataQueryCheck();
        JsonNode tasks = get(RUNTIME + "/my-tasks", executor).assertOk().json();
        var manual = post(RUNTIME + "/action-runs/" + row.get("action_run_id") + "/submission", executor,
            new SubmitWorkflowActionRequestBody(WorkflowGraphJson.empty())).assertProblem(400,
                CommonErrorCode.BUSINESS_CHECK_FAILED);
        assertAll(() -> assertEquals(ActionRunStatus.PENDING.getCode(), row.get("status")),
            () -> assertEquals(DataQueryCheckResult.UNSATISFIED.getCode(), row.get("check_result")),
            () -> assertEquals("CLIENT_NOT_FOUND", row.get("check_detail")),
            () -> assertEquals(0, ((Number) row.get("check_failure_count")).intValue()),
            () -> assertNotNull(row.get("last_check_time")), () -> assertNotNull(row.get("last_check_success_time")),
            () -> assertNull(row.get("output_json")),
            () -> assertEquals(DataQueryCheckResult.UNSATISFIED.getCode(), block.getResult()),
            () -> assertEquals(List.of("CLIENT_NOT_FOUND"), block.getMissing()),
            () -> assertNull(block.getFailureCode()), () -> assertFalse(block.isChecking()),
            () -> assertEquals("workflow.dataQuery.criteria.GOLIVE_CLIENT", block.getDescriptionKey()),
            () -> assertEquals(1, tasks.path("total").asInt()),
            () -> assertEquals(WorkflowActionType.DATA_QUERY.getCode(), tasks.path("items").get(0).path("actionType")
                .asString()),
            () -> assertTrue(manual.json().path("detail").asString().contains("系统检查动作由查询结果自动完成")),
            () -> assertEquals(ActionRunStatus.PENDING.getCode(), actionRow(instanceId, "check").get("status")));
    }

    /** 失败：超时、多行、缺 satisfied、输出类型不符、模板停用、参数不合法，各记失败码与计数 1；不通知、仍可终止（AC-07）。 */
    @Test
    void test_failuresKeepPending() {
        String clientType = "'ORGANIZATION' AS out_client_type, 'FX' AS out_client_no";
        queryTemplate("Q_TIMEOUT", "SELECT 1 AS satisfied, " + clientType + ", SUM(a.n + b.n + c.n) AS total FROM "
            + DataQueryFixture.NUMBERS + " a CROSS JOIN " + DataQueryFixture.NUMBERS + " b CROSS JOIN "
            + DataQueryFixture.NUMBERS + " c", "[]", null, 1, QueryTemplateStatus.ACTIVE);
        queryTemplate("Q_ROWS", "SELECT 1 AS satisfied, " + clientType + " FROM t_client_fixture", "[]", null);
        queryTemplate("Q_NO_SATISFIED", "SELECT 1 AS ok, " + clientType, "[]", null);
        queryTemplate("Q_TYPE", "SELECT 1 AS satisfied, 'OTHER' AS out_client_type, 'FX' AS out_client_no", "[]",
            null);
        long disabled = queryTemplate("Q_DISABLED", "SELECT 1 AS satisfied, " + clientType, "[]", null);
        JsonNode graph = WorkflowGraphJson.graph().task("n1", "多项检查")
            .action("timeout", noParameter("T_TIMEOUT", "Q_TIMEOUT"), executor.accountId(), true)
            .action("rows", noParameter("T_ROWS", "Q_ROWS"), executor.accountId(), true)
            .action("shape", noParameter("T_SHAPE", "Q_NO_SATISFIED"), executor.accountId(), true)
            .action("type", noParameter("T_TYPE", "Q_TYPE"), executor.accountId(), true)
            .action("disabled", noParameter("T_DISABLED", "Q_DISABLED"), executor.accountId(), true)
            .action("parameter", check, executor.accountId(), true).input("clientId", "CONSTANT", "not-a-number")
            .end("end").chain("start", "n1", "end").build();
        published("FAILURES", graph, WorkflowGraphJson.textForm("clientId"));
        dataQueryRows.changeTemplateStatus(disabled, QueryTemplateStatus.INACTIVE.getCode());

        long instanceId = started("FAILURES", "1001");
        awaitChecks();

        Map<String, String> failures = Map.of("timeout", DataQueryCheckFailure.QUERY_TIMEOUT.getCode(),
            "rows", DataQueryCheckFailure.RESULT_SHAPE_INVALID.getCode(),
            "shape", DataQueryCheckFailure.RESULT_SHAPE_INVALID.getCode(),
            "type", DataQueryCheckFailure.RESULT_SHAPE_INVALID.getCode(),
            "disabled", DataQueryCheckFailure.TEMPLATE_UNAVAILABLE.getCode(),
            "parameter", DataQueryCheckFailure.PARAMETER_INVALID.getCode());
        failures.forEach((action, failure) -> {
            Map<String, Object> row = actionRow(instanceId, action);
            assertAll(action, () -> assertEquals(ActionRunStatus.PENDING.getCode(), row.get("status")),
                () -> assertEquals(DataQueryCheckResult.FAILED.getCode(), row.get("check_result")),
                () -> assertEquals(failure, row.get("check_detail")),
                () -> assertEquals(1, ((Number) row.get("check_failure_count")).intValue()),
                () -> assertNull(row.get("last_check_success_time")), () -> assertNull(row.get("output_json")));
        });
        assertEquals(0, workflowData.callbacks(instanceId).size());
        post(RUNTIME + "/instances/" + instanceId + "/termination", admin, new TerminateWorkflowInstanceRequestBody(
            "人工处理")).assertOk();
        assertEquals(ActionRunStatus.CANCELLED.getCode(), actionRow(instanceId, "timeout").get("status"));
    }

    /** 输出驱动排他网关：ENUM 等于、BOOLEAN（0 / 1 规整为布尔）等于、TEXT 包含（AC-08）。 */
    @Test
    void test_outputsDriveGateways() {
        queryTemplate("GOLIVE_KIND", "SELECT IF(COUNT(*) > 0, 1, 0) AS satisfied, MAX(client_type) AS out_client_type,"
            + " MAX(IF(client_type = 'ORGANIZATION', 1, 0)) AS out_is_org, MAX(client_no) AS out_client_no"
            + " FROM t_client_fixture WHERE client_id = #{clientId}", CLIENT_PARAMETERS, null);
        long kind = template("KIND_CHECK", WorkflowActionType.DATA_QUERY.getCode(), dataQueryConfig("GOLIVE_KIND",
            List.of("clientId"), enumOutput(outputs("is_org", "BOOLEAN", "client_no", "TEXT"), "client_type",
                "ORGANIZATION", "INDIVIDUAL")));
        JsonNode graph = WorkflowGraphJson.graph().task("n1", "类型检查").action("kind", kind, executor.accountId(), true)
            .input("clientId", "VARIABLE", "start.clientId")
            .exclusive("g1", "g1_g2", "kind.client_type", "EQ", "ORGANIZATION", "g1_individual")
            .exclusive("g2", "g2_g3", "kind.is_org", "EQ", "true", "g2_notBool")
            .exclusive("g3", "g3_first", "kind.client_no", "CONTAINS", "1001", "g3_other")
            .end("individual").end("notBool").end("first").end("other")
            .chain("start", "n1", "g1").edge("g1_g2", "g1", "g2").edge("g1_individual", "g1", "individual")
            .edge("g2_g3", "g2", "g3").edge("g2_notBool", "g2", "notBool").edge("g3_first", "g3", "first")
            .edge("g3_other", "g3", "other").build();
        published("BRANCHES", graph, WorkflowGraphJson.textForm("clientId"));

        long first = started("BRANCHES", "1001");
        long other = started("BRANCHES", "1003");
        long individual = started("BRANCHES", "1002");
        awaitChecks();

        assertAll(() -> assertEquals(json("{\"is_org\":true,\"client_no\":\"FX-CL-1001\",\"client_type\":"
                + "\"ORGANIZATION\"}"), json(actionRow(first, "kind").get("output_json"))),
            () -> assertTrue(nodeTrail(first).contains("first#1=PASSED"), () -> nodeTrail(first).toString()),
            () -> assertTrue(nodeTrail(other).contains("other#1=PASSED"), () -> nodeTrail(other).toString()),
            () -> assertTrue(nodeTrail(individual).contains("individual#1=PASSED"),
                () -> nodeTrail(individual).toString()));
    }

    /** 以 Client 编号发起并返回实例 ID（业务关联键 CLIENT:{clientId}）。 */
    private long started(String code, String clientId) {
        WorkflowMutationResponseBody started = startByCode(code, admin, clientId, "CLIENT:" + clientId).assertOk()
            .as(WorkflowMutationResponseBody.class);
        assertEquals(Boolean.TRUE, started.getCreated());
        return Long.parseLong(started.getObjectId());
    }

    /** 无参数的 DATA_QUERY 动作模板，输出 client_type（ENUM）与 client_no（TEXT）。 */
    private long noParameter(String templateCode, String queryCode) {
        return template(templateCode, WorkflowActionType.DATA_QUERY.getCode(), dataQueryConfig(queryCode, List.of(),
            enumOutput(outputs("client_no", "TEXT"), "client_type", "ORGANIZATION", "INDIVIDUAL")));
    }

    /** 详情中某动作最新一轮。 */
    private ActionRunBody actionBody(long instanceId, String actionKey) {
        WorkflowInstanceDetailResponseBody detail = detail(instanceId, admin);
        return detail.getActionRuns().stream().filter(run -> actionKey.equals(run.getActionKey()))
            .reduce((a, b) -> b).orElseThrow();
    }

    /** JSON 文本转为树。 */
    private static JsonNode json(Object text) {
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(String.valueOf(text));
    }
}
