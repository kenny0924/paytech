package com.paytech.platform.support.auto.ext.http.workflow;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.workflow.WorkflowInstanceApi;
import com.paytech.platform.support.api.workflow.request.TerminateWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDataQueryCheckResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.test.base.WorkflowDataQueryTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dataquery.DataQueryFixture;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckFailure;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;

import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 立即检查（REQ-05 R3、AC-09）：参与人立即执行并返回结果与审计；可见性按参与人或 WORKFLOW_INSTANCE_READ；
 * 检查锁被占用返回 inProgress、实例锁被占用 409、已完成不再查询、非 DATA_QUERY 400；并发两次只查询一次；
 * 慢查询期间终止实例时结果丢弃、动作取消。
 *
 * @see WorkflowInstanceApi#checkDataQuery(String, String, String, tools.jackson.databind.JsonNode)
 */
class WorkflowDataQueryCheckNowApiTest extends WorkflowDataQueryTestBase {

    /** 满足的固定正文（不随参数变化）。 */
    private static final String ALWAYS_SATISFIED = "SELECT 1 AS satisfied, 'ORGANIZATION' AS out_client_type,"
        + " 'FX-CL-0' AS out_client_no FROM t_client_fixture WHERE client_id = 1001 OR #{clientId} IS NULL";

    @Autowired
    private RedissonClient redisson;

    private Operator executor;

    private long check;

    private long clientQueryId;

    /** 本类公共：一名跟进人、CLIENT 查询模板与 DATA_QUERY 动作模板，以及单节点流程 SINGLE。 */
    @BeforeEach
    void setUpCheckNowMocks() {
        executor = person();
        clientQueryId = clientQuery("GOLIVE_CLIENT");
        check = clientCheckTemplate("CLIENT_CHECK", "GOLIVE_CLIENT");
        published("SINGLE", singleCheckGraph(check, executor.accountId()), WorkflowGraphJson.textForm("clientId"));
    }

    /** 参与人立即检查：事实变为满足后立即完成并推进，返回检查块，写一条 CHECKED 审计。 */
    @Test
    void test_normalFlow() {
        long instanceId = unsatisfiedInstance();
        String actionRunId = actionRunId(instanceId);
        dataQueryRows.replaceBody(clientQueryId, ALWAYS_SATISFIED);

        WorkflowDataQueryCheckResponseBody result = checkNow(actionRunId, executor).assertOk()
            .as(WorkflowDataQueryCheckResponseBody.class);
        auditChecker.assertEvent(requestId, "OPERATIONS_WORKFLOW_ACTION_CHECK", AuditModels.Result.SUCCEEDED.getCode(),
            executor.actor(), "WORKFLOW_ACTION_RUN", actionRunId);

        assertAll(() -> assertEquals(actionRunId, result.getActionRunId()),
            () -> assertEquals(ActionRunStatus.COMPLETED.getCode(), result.getActionStatus()),
            () -> assertEquals(WorkflowInstanceStatus.COMPLETED.getCode(), result.getInstanceStatus()),
            () -> assertFalse(result.isInProgress()),
            () -> assertEquals(DataQueryCheckResult.SATISFIED.getCode(), result.getCheck().getResult()),
            () -> assertEquals(List.of(), result.getCheck().getMissing()),
            () -> assertEquals(WorkflowInstanceStatus.COMPLETED.getCode(), instanceStatus(instanceId)));
    }

    /** 非参与人且无 WORKFLOW_INSTANCE_READ 为 404，有则可检查；动作不存在 404；不是 DATA_QUERY 400；请求体可空。 */
    @Test
    void test_visibilityAndType() {
        long instanceId = unsatisfiedInstance();
        String actionRunId = actionRunId(instanceId);
        Operator outsider = operationsMocker.operator(OperatorBuilder.administrator().roles(
            operationsMocker.customRole("WORKFLOW_HANDLER", WorkflowPermissions.TASK_HANDLE)));
        Operator reader = operationsMocker.operator(OperatorBuilder.administrator().roles(
            operationsMocker.customRole("WORKFLOW_READER", WorkflowPermissions.TASK_HANDLE,
                WorkflowPermissions.INSTANCE_READ)));
        long manual = published("MANUAL", WorkflowGraphJson.graph().task("review", "审核")
            .action("confirm", confirmTemplate("CONFIRM_SETUP"), executor.accountId(), true).end("end")
            .chain("start", "review", "end").build(), WorkflowGraphJson.emptyForm());
        long manualInstance = started(manual, admin, WorkflowGraphJson.empty());

        checkNow(actionRunId, outsider).assertProblem(404, CommonErrorCode.NOT_FOUND);
        WorkflowDataQueryCheckResponseBody read = post(RUNTIME + "/action-runs/" + actionRunId + "/data-query-check",
            reader, null).assertOk().as(WorkflowDataQueryCheckResponseBody.class);
        checkNow("123456789", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        PlatformResponseChecker notDataQuery = checkNow(openAction(manualInstance, "confirm"), executor)
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertAll(() -> assertEquals(DataQueryCheckResult.UNSATISFIED.getCode(), read.getCheck().getResult()),
            () -> assertEquals(List.of("CLIENT_NOT_FOUND"), read.getCheck().getMissing()),
            () -> assertTrue(notDataQuery.json().path("detail").asString().contains("该动作不是系统检查")));
    }

    /** 检查锁被占用：不执行查询，返回已记录状态与 inProgress；实例锁被占用：409 且结果丢弃；已完成的动作不再查询。 */
    @Test
    void test_locksAndFinishedAction() {
        long instanceId = unsatisfiedInstance();
        String actionRunId = actionRunId(instanceId);
        Object checkedAt = actionRow(instanceId, "check").get("last_check_time");

        WorkflowDataQueryCheckResponseBody inProgress = holding("workflow-action-check:" + actionRunId,
            () -> checkNow(actionRunId, executor).assertOk().as(WorkflowDataQueryCheckResponseBody.class));
        holding("workflow-instance:" + instanceId,
            () -> checkNow(actionRunId, executor).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST));
        Map<String, Object> afterBusy = actionRow(instanceId, "check");
        dataQueryRows.replaceBody(clientQueryId, ALWAYS_SATISFIED);
        checkNow(actionRunId, executor).assertOk();
        Object completedAt = actionRow(instanceId, "check").get("last_check_time");
        WorkflowDataQueryCheckResponseBody finished = checkNow(actionRunId, executor).assertOk()
            .as(WorkflowDataQueryCheckResponseBody.class);

        assertAll(() -> assertTrue(inProgress.isInProgress()),
            () -> assertEquals(DataQueryCheckResult.UNSATISFIED.getCode(), inProgress.getCheck().getResult()),
            () -> assertEquals(checkedAt, afterBusy.get("last_check_time")),
            () -> assertEquals(0, ((Number) afterBusy.get("check_failure_count")).intValue()),
            () -> assertFalse(finished.isInProgress()),
            () -> assertEquals(ActionRunStatus.COMPLETED.getCode(), finished.getActionStatus()),
            () -> assertEquals(completedAt, actionRow(instanceId, "check").get("last_check_time")));
    }

    /** 两个并发立即检查只执行一次查询（慢查询 1 秒超时）：一个返回结果，另一个 inProgress，失败计数只加一。 */
    @Test
    void test_concurrentChecksQueryOnce() throws Exception {
        long instanceId = slowInstance("SLOW_ONCE");
        String actionRunId = actionRunId(instanceId);

        CompletableFuture<WorkflowDataQueryCheckResponseBody> first = CompletableFuture.supplyAsync(
            () -> checkNow(actionRunId, executor).assertOk().as(WorkflowDataQueryCheckResponseBody.class));
        awaitCheckLock(actionRunId);
        WorkflowDataQueryCheckResponseBody second = checkNow(actionRunId, executor).assertOk()
            .as(WorkflowDataQueryCheckResponseBody.class);
        WorkflowDataQueryCheckResponseBody executed = first.get(30, TimeUnit.SECONDS);

        assertAll(() -> assertTrue(second.isInProgress()), () -> assertFalse(executed.isInProgress()),
            () -> assertEquals(DataQueryCheckFailure.QUERY_TIMEOUT.getCode(), executed.getCheck().getFailureCode()),
            () -> assertEquals(2, ((Number) actionRow(instanceId, "check").get("check_failure_count")).intValue()));
    }

    /** 慢查询进行中终止实例：终止不被检查阻塞，检查结果丢弃，动作为 CANCELLED，失败计数不变。 */
    @Test
    void test_terminateDuringQuery() throws Exception {
        long instanceId = slowInstance("SLOW_TERMINATE");
        String actionRunId = actionRunId(instanceId);

        CompletableFuture<WorkflowDataQueryCheckResponseBody> running = CompletableFuture.supplyAsync(
            () -> checkNow(actionRunId, executor).assertOk().as(WorkflowDataQueryCheckResponseBody.class));
        awaitCheckLock(actionRunId);
        post(RUNTIME + "/instances/" + instanceId + "/termination", admin, new TerminateWorkflowInstanceRequestBody(
            "取消上线")).assertOk();
        WorkflowDataQueryCheckResponseBody result = running.get(30, TimeUnit.SECONDS);

        Map<String, Object> row = actionRow(instanceId, "check");
        assertAll(() -> assertEquals(ActionRunStatus.CANCELLED.getCode(), result.getActionStatus()),
            () -> assertEquals(WorkflowInstanceStatus.TERMINATED.getCode(), result.getInstanceStatus()),
            () -> assertEquals(ActionRunStatus.CANCELLED.getCode(), row.get("status")),
            () -> assertEquals(1, ((Number) row.get("check_failure_count")).intValue()));
    }

    /** 以不存在的 Client 发起并等待首查（未满足）。 */
    private long unsatisfiedInstance() {
        long instanceId = Long.parseLong(startByCode("SINGLE", admin, "9999", "CLIENT:9999").assertOk()
            .as(WorkflowMutationResponseBody.class).getObjectId());
        awaitChecks();
        assertEquals(DataQueryCheckResult.UNSATISFIED.getCode(), actionRow(instanceId, "check").get("check_result"));
        return instanceId;
    }

    /** 引用 1 秒即超时的慢查询的单节点流程，发起并等待首查（失败计数 1）。 */
    private long slowInstance(String code) {
        queryTemplate("Q_" + code, "SELECT 1 AS satisfied, 'ORGANIZATION' AS out_client_type, 'FX' AS out_client_no,"
            + " SUM(a.n + b.n + c.n) AS total FROM " + DataQueryFixture.NUMBERS + " a CROSS JOIN "
            + DataQueryFixture.NUMBERS + " b CROSS JOIN " + DataQueryFixture.NUMBERS + " c", "[]", null, 1,
            QueryTemplateStatus.ACTIVE);
        long slow = template("T_" + code, WorkflowActionType.DATA_QUERY.getCode(), dataQueryConfig("Q_" + code,
            List.of(), enumOutput(outputs("client_no", "TEXT"), "client_type", "ORGANIZATION", "INDIVIDUAL")));
        published(code, WorkflowGraphJson.graph().task("n1", "慢检查").action("check", slow, executor.accountId(), true)
            .end("end").chain("start", "n1", "end").build(), WorkflowGraphJson.textForm("clientId"));
        long instanceId = Long.parseLong(startByCode(code, admin, "1001", "CLIENT:1001").assertOk()
            .as(WorkflowMutationResponseBody.class).getObjectId());
        awaitChecks();
        assertEquals(1, ((Number) actionRow(instanceId, "check").get("check_failure_count")).intValue());
        return instanceId;
    }

    /** 实例唯一动作 check 的运行 ID。 */
    private String actionRunId(long instanceId) {
        return actionRow(instanceId, "check").get("action_run_id").toString();
    }

    /** 立即检查，请求体为 {}。 */
    private PlatformResponseChecker checkNow(String actionRunId, Operator operator) {
        return post(RUNTIME + "/action-runs/" + actionRunId + "/data-query-check", operator,
            JsonNodeFactory.instance.objectNode());
    }

    /** 等待检查锁被持有（查询已开始）。 */
    private void awaitCheckLock(String actionRunId) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20))
            .until(() -> lock("workflow-action-check:" + actionRunId).isLocked());
    }

    /** 资源锁：单资源 LockCommand 自动加 lock: 前缀并使用自旋锁（Foundation AbstractLockProcessor 合同）。 */
    private RLock lock(String key) {
        return redisson.getSpinLock("lock:" + key);
    }

    /** 在持有给定资源锁期间执行。 */
    private <T> T holding(String key, java.util.function.Supplier<T> action) {
        RLock held = lock(key);
        assertTrue(held.tryLock());
        try {
            return action.get();
        } finally {
            held.unlock();
        }
    }
}
