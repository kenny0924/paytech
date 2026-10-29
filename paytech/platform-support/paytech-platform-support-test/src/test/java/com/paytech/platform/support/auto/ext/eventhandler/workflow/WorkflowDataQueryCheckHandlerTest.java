package com.paytech.platform.support.auto.ext.eventhandler.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.test.base.WorkflowDataQueryTestBase;
import com.paytech.platform.support.test.builder.CredentialFixture;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;
import com.paytech.platform.support.workflow.service.handler.WorkflowDataQueryCheckHandler;

import tools.jackson.databind.JsonNode;

/**
 * DATA_QUERY 首查补偿任务（REQ-05 R3）：任何写入口新建 DATA_QUERY 动作运行时同事务登记、提交后执行并删除；
 * 检查锁被占用视为已有检查、结束任务；实例锁冲突可重试，到期后经正式 Scanner 重新执行；身份不合法终止。
 *
 * @see WorkflowDataQueryCheckHandler#handle(com.paytech.foundation.compensation.api.CompensationEvent)
 */
class WorkflowDataQueryCheckHandlerTest extends WorkflowDataQueryTestBase {

    @Autowired
    private RedissonClient redisson;

    private Operator executor;

    private long check;

    /** 本类公共：跟进人、CLIENT 查询模板与 DATA_QUERY 动作模板。 */
    @BeforeEach
    void setUpHandlerMocks() {
        executor = person();
        clientQuery("GOLIVE_CLIENT");
        check = clientCheckTemplate("CLIENT_CHECK", "GOLIVE_CLIENT");
    }

    /** 人工完成前一节点后激活的 DATA_QUERY 也登记首查：提交后执行、满足即完成并推进，任务删除。 */
    @Test
    void test_activationAfterManualAction() {
        JsonNode graph = WorkflowGraphJson.graph().task("n1", "人工确认")
            .action("confirm", confirmTemplate("CONFIRM_SETUP"), executor.accountId(), true)
            .task("n2", "系统检查").action("check", check, executor.accountId(), true)
            .input("clientId", "VARIABLE", "start.clientId").end("end").chain("start", "n1", "n2", "end").build();
        published("AFTER_MANUAL", graph, WorkflowGraphJson.textForm("clientId"));
        long instanceId = started("AFTER_MANUAL", "1002");

        confirm(instanceId, "confirm", executor);
        awaitChecks();

        Map<String, Object> row = actionRow(instanceId, "check");
        assertAll(() -> assertEquals(ActionRunStatus.COMPLETED.getCode(), row.get("status")),
            () -> assertEquals(DataQueryCheckResult.SATISFIED.getCode(), row.get("check_result")),
            () -> assertEquals("COMPLETED", instanceStatus(instanceId)),
            () -> assertTrue(workflowData.events().isEmpty()));
    }

    /** 检查锁被占用：任务视为已有检查而结束，不查询；实例锁被占用：可重试，释放后到期重跑完成检查。 */
    @Test
    void test_locksAndRetry() {
        published("SINGLE", singleCheckGraph(check, executor.accountId()), WorkflowGraphJson.textForm("clientId"));
        long instanceId = started("SINGLE", "9999");
        awaitChecks();
        String actionRunId = actionRow(instanceId, "check").get("action_run_id").toString();
        // 测试时钟固定：先把最近检查时间移到过去，重新检查后应回到当前时钟
        workflowData.setLastCheckTime(Long.parseLong(actionRunId), CredentialFixture.NOW.minus(5, ChronoUnit.MINUTES));
        Object firstCheck = actionRow(instanceId, "check").get("last_check_time");

        holding("workflow-action-check:" + actionRunId, () -> scanOne(actionRunId));
        List<Map<String, Object>> afterInProgress = workflowData.events();
        Object stillFirst = actionRow(instanceId, "check").get("last_check_time");
        holding("workflow-instance:" + instanceId, () -> scanOne(actionRunId));
        List<Map<String, Object>> afterBusy = workflowData.events();
        workflowData.due();
        scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(WorkflowDataQueryCheckHandler.EVENT_TYPE)));
        workflowFixture.awaitIdle();

        assertAll(() -> assertTrue(afterInProgress.isEmpty(), afterInProgress::toString),
            () -> assertEquals(firstCheck, stillFirst),
            () -> assertEquals(1, afterBusy.size()),
            () -> assertEquals("ERROR", afterBusy.get(0).get("execute_status")),
            () -> assertEquals("WORKFLOW_INSTANCE_BUSY", afterBusy.get(0).get("last_error_code")),
            () -> assertTrue(workflowData.events().isEmpty()),
            () -> assertNotEquals(firstCheck, actionRow(instanceId, "check").get("last_check_time")));
    }

    /** 事件键不是动作运行 ID：终止失败并保留，不执行检查。 */
    @Test
    void test_invalidIdentity() {
        workflowData.insertEvent(OperatorBuilder.nextId(), "not-an-id", WorkflowDataQueryCheckHandler.EVENT_TYPE);

        scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(WorkflowDataQueryCheckHandler.EVENT_TYPE)));
        workflowFixture.awaitIdle();

        List<Map<String, Object>> events = workflowData.events();
        assertAll(() -> assertEquals(1, events.size()), () -> assertEquals("FAILED", events.get(0).get("execute_status")),
            () -> assertEquals("WORKFLOW_DATA_QUERY_CHECK_INVALID", events.get(0).get("last_error_code")));
    }

    /** 插入该动作的首查任务并经正式 Scanner 执行一次。 */
    private void scanOne(String actionRunId) {
        workflowData.insertEvent(OperatorBuilder.nextId(), actionRunId, WorkflowDataQueryCheckHandler.EVENT_TYPE);
        scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(WorkflowDataQueryCheckHandler.EVENT_TYPE)));
        workflowFixture.awaitIdle();
    }

    /** 以 Client 编号按编码发起。 */
    private long started(String code, String clientId) {
        return Long.parseLong(startByCode(code, admin, clientId, "CLIENT:" + clientId).assertOk()
            .as(WorkflowMutationResponseBody.class).getObjectId());
    }

    /** 持有资源锁（单资源 LockCommand 的 lock: 前缀与自旋锁）期间执行。 */
    private void holding(String key, Runnable action) {
        RLock held = redisson.getSpinLock("lock:" + key);
        assertTrue(held.tryLock());
        try {
            action.run();
        } finally {
            held.unlock();
        }
    }
}
