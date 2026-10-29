package com.paytech.platform.support.auto.ext.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.ext.job.WorkflowDataQueryRecheckJobHandler;
import com.paytech.platform.support.test.base.WorkflowDataQueryTestBase;
import com.paytech.platform.support.test.builder.CredentialFixture;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.dataquery.DataQueryFixture;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckFailure;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.service.processor.response.DataQueryRecheckSummary;

/**
 * DATA_QUERY 定时重查（REQ-05 R3、AC-06、AC-07）：未满足的动作在 Owner 事实变化后由 Job 完成并推进；失败计数逐轮累加、
 * 成功后清零；最近刚检查过的动作不进入本轮；按 instance_id 取模分片，两个分片不重复。
 *
 * @see WorkflowDataQueryRecheckJobHandler#runOnce(int, int)
 */
class WorkflowDataQueryRecheckJobHandlerTest extends WorkflowDataQueryTestBase {

    @Autowired
    private WorkflowDataQueryRecheckJobHandler job;

    private Operator executor;

    private long clientQueryId;

    /** 本类公共：跟进人、CLIENT 查询模板、DATA_QUERY 动作模板与单节点流程 SINGLE。 */
    @BeforeEach
    void setUpRecheckMocks() {
        executor = person();
        clientQueryId = clientQuery("GOLIVE_CLIENT");
        published("SINGLE", singleCheckGraph(clientCheckTemplate("CLIENT_CHECK", "GOLIVE_CLIENT"),
            executor.accountId()), WorkflowGraphJson.textForm("clientId"));
    }

    /** 未满足后 Owner 事实变为满足：一轮重查完成动作并推进实例；已完成的动作不再进入下一轮。 */
    @Test
    void test_recheckCompletes() {
        long instanceId = started("SINGLE", "9999");
        awaitChecks();
        expire(instanceId);
        dataQueryRows.replaceBody(clientQueryId, "SELECT 1 AS satisfied, 'INDIVIDUAL' AS out_client_type,"
            + " 'FX-CL-9' AS out_client_no FROM t_client_fixture WHERE client_id = 1002 OR #{clientId} IS NULL");

        DataQueryRecheckSummary round = job.runOnce(0, 1);
        workflowFixture.awaitIdle();
        DataQueryRecheckSummary next = job.runOnce(0, 1);

        Map<String, Object> row = actionRow(instanceId, "check");
        assertAll(() -> assertEquals(List.of(1, 1, 0, 0, 0, 0), counts(round)),
            () -> assertEquals(List.of(0, 0, 0, 0, 0, 0), counts(next)),
            () -> assertEquals(ActionRunStatus.COMPLETED.getCode(), row.get("status")),
            () -> assertEquals(DataQueryCheckResult.SATISFIED.getCode(), row.get("check_result")),
            () -> assertEquals("COMPLETED", instanceStatus(instanceId)));
    }

    /** 超时失败逐轮累加计数（1 → 2），查询恢复后为未满足、计数清零。 */
    @Test
    void test_failureCountAccumulates() {
        long slowQuery = queryTemplate("Q_SLOW", "SELECT 1 AS satisfied, SUM(a.n + b.n + c.n) AS total FROM "
            + DataQueryFixture.NUMBERS + " a CROSS JOIN " + DataQueryFixture.NUMBERS + " b CROSS JOIN "
            + DataQueryFixture.NUMBERS + " c", "[]", null, 1, QueryTemplateStatus.ACTIVE);
        long slow = template("T_SLOW", WorkflowActionType.DATA_QUERY.getCode(), dataQueryConfig("Q_SLOW", List.of(),
            outputs()));
        published("SLOW", WorkflowGraphJson.graph().task("n1", "慢检查").action("check", slow, executor.accountId(), true)
            .end("end").chain("start", "n1", "end").build(), WorkflowGraphJson.textForm("clientId"));
        long instanceId = started("SLOW", "1001");
        awaitChecks();
        int afterFirst = failures(instanceId);

        expire(instanceId);
        DataQueryRecheckSummary failedRound = job.runOnce(0, 1);
        int afterSecond = failures(instanceId);
        Object detailAfterSecond = actionRow(instanceId, "check").get("check_detail");
        dataQueryRows.replaceBody(slowQuery, "SELECT 0 AS satisfied, 'TERM_NOT_ACTIVE' AS missing");
        expire(instanceId);
        job.runOnce(0, 1);

        Map<String, Object> row = actionRow(instanceId, "check");
        assertAll(() -> assertEquals(1, afterFirst), () -> assertEquals(2, afterSecond),
            () -> assertEquals(List.of(1, 0, 0, 0, 0, 0), counts(failedRound)),
            () -> assertEquals(1, failedRound.getQueryFailed()),
            () -> assertEquals(DataQueryCheckFailure.QUERY_TIMEOUT.getCode(), detailAfterSecond),
            () -> assertEquals(DataQueryCheckResult.UNSATISFIED.getCode(), row.get("check_result")),
            () -> assertEquals("TERM_NOT_ACTIVE", row.get("check_detail")),
            () -> assertEquals(0, ((Number) row.get("check_failure_count")).intValue()));
    }

    /** 刚检查过（最近检查不早于截止时间）的动作 c 不进入本轮；两个分片各自只取本分片实例，合计覆盖 a、b 且不重复。 */
    @Test
    void test_minAgeAndShards() {
        long a = started("SINGLE", "9001");
        long b = started("SINGLE", "9002");
        long c = started("SINGLE", "9003");
        awaitChecks();
        expire(a);
        expire(b);

        DataQueryRecheckSummary shard0 = job.runOnce(0, 2);
        DataQueryRecheckSummary shard1 = job.runOnce(1, 2);

        long expectedShard0 = List.of(a, b).stream().filter(id -> id % 2 == 0).count();
        assertAll(() -> assertEquals(expectedShard0, shard0.getChecked()),
            () -> assertEquals(2 - expectedShard0, shard1.getChecked()),
            () -> assertEquals(2, shard0.getUnsatisfied() + shard1.getUnsatisfied()),
            () -> assertEquals(0, shard0.getFailed() + shard1.getFailed()));
    }

    /** 以 Client 编号按编码发起。 */
    private long started(String code, String clientId) {
        return Long.parseLong(startByCode(code, admin, clientId, "CLIENT:" + clientId).assertOk()
            .as(WorkflowMutationResponseBody.class).getObjectId());
    }

    /**
     * 使动作到期：测试时钟固定，截止时间为当前时间减最小间隔（Mock 为 0），把最近检查时间移到 5 分钟前。
     *
     * @param instanceId 实例 ID
     */
    private void expire(long instanceId) {
        workflowData.setLastCheckTime(actionRunId(instanceId), CredentialFixture.NOW.minus(5, ChronoUnit.MINUTES));
    }

    /** 实例唯一动作 check 的运行 ID。 */
    private long actionRunId(long instanceId) {
        return ((Number) actionRow(instanceId, "check").get("action_run_id")).longValue();
    }

    /** 连续失败次数。 */
    private int failures(long instanceId) {
        return ((Number) actionRow(instanceId, "check").get("check_failure_count")).intValue();
    }

    /** checked、completed、unsatisfied、skipped、busy、failed。 */
    private static List<Integer> counts(DataQueryRecheckSummary summary) {
        return List.of(summary.getChecked(), summary.getCompleted(), summary.getUnsatisfied(), summary.getSkipped(),
            summary.getBusy(), summary.getFailed());
    }
}
