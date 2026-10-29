package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.workflow.WorkflowInstanceApi;
import com.paytech.platform.support.api.workflow.request.ChangeWorkflowStatusRequestBody;
import com.paytech.platform.support.api.workflow.request.StartWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.request.TerminateWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.base.WorkflowApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;

import tools.jackson.databind.JsonNode;

/**
 * 按编码发起与业务关联键防重（REQ-05 R1，AC-01—AC-03）：同键重复与并发发起只有一个 RUNNING 实例，结束后可再发起，
 * ID + 版本发起同样防重，业务关联键区分大小写；二选一与未发布 / 停用的拒绝。
 *
 * @see WorkflowInstanceApi#startInstance(String, String, StartWorkflowInstanceRequestBody)
 */
class WorkflowStartByCodeApiTest extends WorkflowApiTestBase {

    private static final String CODE = "GO_LIVE";

    private Operator executor;

    private long definitionId;

    /** 本类公共：一名执行人与一个已发布的单节点人工确认流程。 */
    @BeforeEach
    void setUpStartMocks() {
        executor = person();
        JsonNode graph = WorkflowGraphJson.graph().task("review", "审核")
            .action("confirm", confirmTemplate("CONFIRM_SETUP"), executor.accountId(), true).end("end")
            .chain("start", "review", "end").build();
        definitionId = published(CODE, graph, WorkflowGraphJson.emptyForm());
    }

    /** 首次 created=true；同键再发返回同一实例 created=false，标题等改动被忽略；ID + 版本模式同键也命中；大小写不同为新键。 */
    @Test
    void test_normalFlowAndDuplicate() {
        WorkflowMutationResponseBody first = byCode(CODE, "CLIENT:880000000101", "首次").assertOk()
            .as(WorkflowMutationResponseBody.class);
        auditChecker.assertEvent(requestId, "OPERATIONS_WORKFLOW_INSTANCE_START",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), "WORKFLOW_INSTANCE", first.getObjectId());
        WorkflowMutationResponseBody again = byCode(CODE, "CLIENT:880000000101", "改了标题").assertOk()
            .as(WorkflowMutationResponseBody.class);
        WorkflowMutationResponseBody byId = byId("CLIENT:880000000101").assertOk()
            .as(WorkflowMutationResponseBody.class);
        WorkflowMutationResponseBody otherCase = byCode(CODE, "client:880000000101", "大小写").assertOk()
            .as(WorkflowMutationResponseBody.class);

        Map<String, Object> row = workflowData.instance(Long.parseLong(first.getObjectId()));
        assertAll(() -> assertEquals(Boolean.TRUE, first.getCreated()),
            () -> assertEquals(WorkflowInstanceStatus.RUNNING.getCode(), first.getStatus()),
            () -> assertEquals(Boolean.FALSE, again.getCreated()),
            () -> assertEquals(first.getObjectId(), again.getObjectId()),
            () -> assertEquals(Boolean.FALSE, byId.getCreated()),
            () -> assertEquals(first.getObjectId(), byId.getObjectId()),
            () -> assertEquals(Boolean.TRUE, otherCase.getCreated()),
            () -> assertNotEquals(first.getObjectId(), otherCase.getObjectId()),
            () -> assertEquals("CLIENT:880000000101", row.get("business_key")),
            () -> assertEquals(1, ((Number) row.get("active_slot")).intValue()),
            () -> assertEquals(1, workflowData.instancesByKey(definitionId, "CLIENT:880000000101").size()),
            () -> assertEquals(2, workflowData.instanceCount()));
    }

    /** 两个线程同时按同键发起：只产生一个 RUNNING 实例，另一个返回同一实例 created=false。 */
    @Test
    void test_concurrentStart() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<WorkflowMutationResponseBody>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                results.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(go);
                    return byCode(CODE, "CLIENT:880000000102", "并发").assertOk().as(WorkflowMutationResponseBody.class);
                }, pool));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            WorkflowMutationResponseBody a = results.get(0).get(30, TimeUnit.SECONDS);
            WorkflowMutationResponseBody b = results.get(1).get(30, TimeUnit.SECONDS);

            assertAll(() -> assertEquals(a.getObjectId(), b.getObjectId()),
                () -> assertEquals(List.of(false, true), List.of(a.getCreated(), b.getCreated()).stream().sorted()
                    .toList()),
                () -> assertEquals(1, workflowData.instancesByKey(definitionId, "CLIENT:880000000102").size()));
        } finally {
            pool.shutdownNow();
        }
    }

    /** 终止后同键可再发起（新实例，原实例槽位清空）。 */
    @Test
    void test_restartAfterTermination() {
        String firstId = byCode(CODE, "CLIENT:880000000103", "首次").assertOk().as(WorkflowMutationResponseBody.class)
            .getObjectId();
        post(RUNTIME + "/instances/" + firstId + "/termination", admin, new TerminateWorkflowInstanceRequestBody("重来"))
            .assertOk();

        WorkflowMutationResponseBody second = byCode(CODE, "CLIENT:880000000103", "再次").assertOk()
            .as(WorkflowMutationResponseBody.class);

        Map<String, Object> terminated = workflowData.instance(Long.parseLong(firstId));
        assertAll(() -> assertEquals(Boolean.TRUE, second.getCreated()),
            () -> assertNotEquals(firstId, second.getObjectId()),
            () -> assertEquals(WorkflowInstanceStatus.TERMINATED.getCode(), terminated.get("status")),
            () -> assertNull(terminated.get("active_slot")),
            () -> assertEquals(2, workflowData.instancesByKey(definitionId, "CLIENT:880000000103").size()));
    }

    /** 完成后同键可再发起。 */
    @Test
    void test_restartAfterCompletion() {
        long firstId = Long.parseLong(byCode(CODE, "CLIENT:880000000104", "首次").assertOk()
            .as(WorkflowMutationResponseBody.class).getObjectId());
        confirm(firstId, "confirm", executor);

        WorkflowMutationResponseBody second = byCode(CODE, "CLIENT:880000000104", "再次").assertOk()
            .as(WorkflowMutationResponseBody.class);

        assertAll(() -> assertEquals(WorkflowInstanceStatus.COMPLETED.getCode(), instanceStatus(firstId)),
            () -> assertNull(workflowData.instance(firstId).get("active_slot")),
            () -> assertEquals(Boolean.TRUE, second.getCreated()));
    }

    /** 二选一违反 400 INVALID_REQUEST；编码不存在 404；停用或从未发布 400 BUSINESS_CHECK_FAILED（AC-03）；都不建实例。 */
    @Test
    void test_invalidParameter() {
        post(RUNTIME + "/instances", admin, new StartWorkflowInstanceRequestBody(Long.toString(definitionId), 1, "两者都给",
            null, WorkflowGraphJson.empty(), null, null, CODE)).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        post(RUNTIME + "/instances", admin, new StartWorkflowInstanceRequestBody(Long.toString(definitionId), null,
            "缺版本", null, WorkflowGraphJson.empty(), null, null, null)).assertProblem(400,
                CommonErrorCode.INVALID_REQUEST);
        post(RUNTIME + "/instances", admin, new StartWorkflowInstanceRequestBody(null, null, "都没有", null,
            WorkflowGraphJson.empty(), null, null, null)).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        byCode("lower_case", null, "格式").assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("definitionCode");
        byCode("NO_SUCH_FLOW", null, "不存在").assertProblem(404, CommonErrorCode.NOT_FOUND);
        createDefinition("NEVER_PUBLISHED");
        PlatformResponseChecker unpublished = byCode("NEVER_PUBLISHED", null, "未发布").assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        int version = ((Number) workflowData.definition(definitionId).get("version")).intValue();
        post(DEFINITIONS + "/" + definitionId + "/status", admin,
            new ChangeWorkflowStatusRequestBody(WorkflowDefinitionStatus.INACTIVE.getCode(), version)).assertOk();
        PlatformResponseChecker inactive = byCode(CODE, "CLIENT:1", "停用").assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertAll(() -> assertTrue(unpublished.json().path("detail").asString().contains("流程未发布或已停用")),
            () -> assertTrue(inactive.json().path("detail").asString().contains("流程未发布或已停用")),
            () -> assertEquals(0, workflowData.instanceCount()));
    }

    /** 按编码发起。 */
    private PlatformResponseChecker byCode(String code, String businessKey, String title) {
        return post(RUNTIME + "/instances", admin, new StartWorkflowInstanceRequestBody(null, null, title, businessKey,
            WorkflowGraphJson.empty(), null, null, code));
    }

    /** 按 ID + 当前版本发起。 */
    private PlatformResponseChecker byId(String businessKey) {
        int versionNo = ((Number) workflowData.definition(definitionId).get("current_version_no")).intValue();
        return post(RUNTIME + "/instances", admin, new StartWorkflowInstanceRequestBody(Long.toString(definitionId),
            versionNo, "按 ID", businessKey, WorkflowGraphJson.empty(), null, null, null));
    }

    /** 等待起跑信号。 */
    private static void await(CountDownLatch go) {
        try {
            assertTrue(go.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
