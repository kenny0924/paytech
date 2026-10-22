package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.workflow.WorkflowInstanceApi;
import com.paytech.platform.support.api.workflow.request.StartWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.request.TerminateWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.request.TransferWorkflowTaskRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActivityListResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody.NodeRunBody;
import com.paytech.platform.support.api.workflow.response.WorkflowSubmissionResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.base.WorkflowApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.enums.RejectionKind;

import tools.jackson.databind.JsonNode;

/**
 * 工作流运行：发起、提交与同步验证、ANY / ALL 审批、必选与可选动作、排他与并行网关、退回修改与四种驳回位置、
 * 同人限制与指派限制、转交、终止、我的待办与实例可见性。
 *
 * @see WorkflowInstanceApi
 */
class WorkflowInstanceApiTest extends WorkflowApiTestBase {

    private Operator executor;
    private Operator approverA;
    private Operator approverB;
    private long confirm;

    /** 本类公共：一名执行人、两名审批人与一个不要求说明的人工确认模板。 */
    @BeforeEach
    void setUpInstanceMocks() {
        executor = person();
        approverA = person();
        approverB = person();
        confirm = confirmTemplate("CONFIRM_SETUP");
    }

    /** 执行人提交后开放 ANY 审批；一人同意即放行，另一行撤回，实例完成；我的待办随之变化，写发起与审批审计。 */
    @Test
    void test_normalFlow() {
        long definitionId = published("ANY_FLOW", review("ANY"), WorkflowGraphJson.emptyForm());
        var started = start(definitionId, admin, WorkflowGraphJson.empty(), null, null).assertOk().json();
        long instanceId = Long.parseLong(started.path("objectId").asString());
        auditChecker.assertEvent(requestId, "OPERATIONS_WORKFLOW_INSTANCE_START",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), "WORKFLOW_INSTANCE", Long.toString(instanceId));
        var executorTasks = get(RUNTIME + "/my-tasks", executor).assertOk().json();

        confirm(instanceId, "confirm", executor);
        var approverTasks = get(RUNTIME + "/my-tasks?taskType=APPROVAL", approverA).assertOk().json();
        approve(instanceId, approverA);
        auditChecker.assertEvent(requestId, "OPERATIONS_WORKFLOW_APPROVAL_DECIDE",
            AuditModels.Result.SUCCEEDED.getCode(), approverA.actor(), "WORKFLOW_APPROVAL",
            approvalId(instanceId, approverA));

        var approvals = workflowData.approvals(instanceId);
        var activities = get(RUNTIME + "/instances/" + instanceId + "/activities", admin).assertOk()
            .as(WorkflowActivityListResponseBody.class);
        assertAll(() -> assertEquals("RUNNING", started.path("status").asString()),
            () -> assertEquals("ACTION", executorTasks.path("items").get(0).path("taskType").asString()),
            () -> assertEquals(1, approverTasks.path("total").asInt()),
            () -> assertEquals("COMPLETED", instanceStatus(instanceId)),
            () -> assertNotNull(workflowData.instance(instanceId).get("end_time")),
            () -> assertEquals(List.of("review#1=PASSED", "end#1=PASSED"), nodeTrail(instanceId)),
            () -> assertEquals(Map.of(approverA.actor(), "APPROVED", approverB.actor(), "WITHDRAWN"),
                Map.of(id(approvals.get(0).get("approver_id")), approvals.get(0).get("status"),
                    id(approvals.get(1).get("approver_id")), approvals.get(1).get("status"))),
            () -> assertEquals(0, get(RUNTIME + "/my-tasks", approverB).assertOk().json().path("total").asInt()),
            () -> assertTrue(activities.getItems().stream().anyMatch(item -> "APPROVAL".equals(item.getActivityType()))),
            () -> assertTrue(activities.getItems().stream().anyMatch(item -> "SUBMISSION".equals(item.getActivityType()))));
    }

    /** ALL：第一人同意后仍待审批，全部同意才放行。 */
    @Test
    void test_allApproval() {
        long instanceId = started(published("ALL_FLOW", review("ALL"), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        confirm(instanceId, "confirm", executor);

        approve(instanceId, approverA);
        String afterFirst = nodeStatus(instanceId, "review", 1);
        approve(instanceId, approverB);

        assertAll(() -> assertEquals("APPROVING", afterFirst), () -> assertEquals("COMPLETED", instanceStatus(instanceId)));
    }

    /** 可选动作不阻塞审批，节点放行时取消；纯审批节点激活即待审批；发起时可改审批人与执行人。 */
    @Test
    void test_optionalActionAndOverrides() {
        Operator other = person();
        JsonNode graph = WorkflowGraphJson.graph().task("review", "审核")
            .action("confirm", confirm, executor.accountId(), true).action("extra", confirm, executor.accountId(), false)
            .approval("ANY", approverA.accountId()).task("sign", "签批").approval("ANY", approverA.accountId())
            .end("end").chain("start", "review", "sign", "end").build();
        long definitionId = published("OPTIONAL_FLOW", graph, WorkflowGraphJson.emptyForm());
        var mutation = start(definitionId, admin, WorkflowGraphJson.empty(),
            List.of(new StartWorkflowInstanceRequestBody.NodeApprovers("sign", List.of(approverB.actor()))),
            List.of(new StartWorkflowInstanceRequestBody.ActionExecutor("extra", other.actor()))).assertOk().json();
        long instanceId = Long.parseLong(mutation.path("objectId").asString());
        // 审批行在必选动作完成后才生成；详情先给出各节点生效的审批人（含发起时的修改）。
        var beforeApproval = get(RUNTIME + "/instances/" + instanceId, admin).assertOk()
            .as(WorkflowInstanceDetailResponseBody.class);

        confirm(instanceId, "confirm", executor);
        String review = nodeStatus(instanceId, "review", 1);
        approve(instanceId, approverA);
        String sign = nodeStatus(instanceId, "sign", 1);
        approve(instanceId, approverB);

        Map<String, Object> extra = workflowData.actionRuns(instanceId).stream()
            .filter(row -> "extra".equals(row.get("action_key"))).findFirst().orElseThrow();
        assertAll(() -> assertTrue(beforeApproval.getApprovals().isEmpty()),
            () -> assertEquals(Map.of("review", List.of(approverA.actor()), "sign", List.of(approverB.actor())),
                beforeApproval.getNodeApprovers()),
            () -> assertNotNull(beforeApproval.getPeople().get(approverB.actor())),
            () -> assertEquals("APPROVING", review), () -> assertEquals("APPROVING", sign),
            () -> assertEquals("CANCELLED", extra.get("status")), () -> assertEquals(other.actor(),
                id(extra.get("assignee_id"))), () -> assertEquals("COMPLETED", instanceStatus(instanceId)));
    }

    /** 排他网关按启动表单取值走条件连线，否则走默认连线。 */
    @Test
    void test_exclusiveGateway() {
        JsonNode graph = WorkflowGraphJson.graph()
            .exclusive("gate", "gate_apple", "start.method", "APPLE_PAY", "gate_card")
            .task("apple", "Apple Pay").approval("ANY", approverA.accountId())
            .task("card", "卡").approval("ANY", approverA.accountId()).end("end")
            .edge("start_gate", "start", "gate").edge("gate_apple", "gate", "apple").edge("gate_card", "gate", "card")
            .edge("apple_end", "apple", "end").edge("card_end", "card", "end").build();
        long definitionId = published("GATE_FLOW", graph, WorkflowGraphJson.enumForm("method", true, "APPLE_PAY",
            "CARD"));

        long apple = started(definitionId, admin, WorkflowGraphJson.values("method", "APPLE_PAY"));
        long card = started(definitionId, admin, WorkflowGraphJson.values("method", "CARD"));
        start(definitionId, admin, WorkflowGraphJson.values("method", "CASH"), null, null).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertAll(() -> assertEquals("APPROVING", nodeStatus(apple, "apple", 1)),
            () -> assertNull(nodeStatus(apple, "card", 1)),
            () -> assertEquals("APPROVING", nodeStatus(card, "card", 1)),
            () -> assertNull(nodeStatus(card, "apple", 1)));
    }

    /** 并行分支全部放行后才进入汇合后的节点；汇合后驳回须指定分支，只重开该分支末节点。 */
    @Test
    void test_parallelAndChooseBranch() {
        long instanceId = started(published("PARALLEL_FLOW", parallel(), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());

        approve(instanceId, approverA, "left");
        String finalAfterLeft = nodeStatus(instanceId, "final", 1);
        approve(instanceId, approverA, "right");
        NodeRunBody finalRun = detail(instanceId, admin).getNodeRuns().stream()
            .filter(run -> "final".equals(run.getNodeKey())).findFirst().orElseThrow();
        decide(instanceId, approverB, "REJECT", "左侧有误", null).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        decide(instanceId, approverB, "REJECT", "左侧有误", "left").assertOk();

        assertAll(() -> assertNull(finalAfterLeft),
            () -> assertEquals(RejectionKind.CHOOSE_BRANCH.getCode(), finalRun.getRejectKind()),
            () -> assertEquals(List.of("left", "right"), finalRun.getRejectTargets().stream().sorted().toList()),
            () -> assertEquals("REJECTED", nodeStatus(instanceId, "final", 1)),
            () -> assertEquals("APPROVING", nodeStatus(instanceId, "left", 2)),
            () -> assertEquals("PASSED", nodeStatus(instanceId, "right", 1)));
    }

    /**
     * 第一个任务与分支第一个节点不能驳回；中间节点驳回退回上一任务新一轮；退回修改只重开本节点，纯审批节点不能退回修改；
     * 意见必填。
     */
    @Test
    void test_rejectPositions() {
        JsonNode serial = WorkflowGraphJson.graph().task("first", "初审")
            .action("confirm", confirm, executor.accountId(), true).approval("ANY", approverA.accountId())
            .task("second", "复审").approval("ANY", approverB.accountId()).end("end")
            .chain("start", "first", "second", "end").build();
        long instanceId = started(published("SERIAL_FLOW", serial, WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        long parallelId = started(published("PARALLEL_FLOW", parallel(), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());

        confirm(instanceId, "confirm", executor);
        var firstKind = rejectKind(instanceId, "first");
        decide(instanceId, approverA, "REJECT", "不通过", null).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        decide(instanceId, approverA, "RETURN", " ", null).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        decide(instanceId, approverA, "RETURN", "补充材料", null).assertOk();
        confirm(instanceId, "confirm", executor);
        approve(instanceId, approverA);
        var secondKind = rejectKind(instanceId, "second");
        decide(instanceId, approverB, "REJECT", "初审有误", null).assertOk();
        var branchKind = rejectKind(parallelId, "left");
        decide(parallelId, approverA, "REJECT", "不通过", null).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        decide(parallelId, approverA, "RETURN", "补充", null).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertAll(() -> assertEquals(RejectionKind.FIRST_TASK.getCode(), firstKind),
            () -> assertEquals(RejectionKind.PREVIOUS_TASK.getCode(), secondKind),
            () -> assertEquals(RejectionKind.BRANCH_FIRST_NODE.getCode(), branchKind),
            () -> assertEquals(List.of("first#1=RETURNED", "first#2=PASSED", "second#1=REJECTED", "first#3=ACTIVE"),
                nodeTrail(instanceId)),
            () -> assertEquals("RUNNING", instanceStatus(instanceId)));
    }

    /** 只有被指派人可以提交与审批；同一人不能既执行又审批同一节点；验证失败可重新提交；上传动作未带文件记为未通过。 */
    @Test
    void test_assigneeAndSamePerson() {
        long upload = template("UPLOAD_DOC", "FILE_UPLOAD",
            objectMapper.readTree("{\"minFiles\":1,\"maxFiles\":1,\"mimeTypes\":[\"application/pdf\"]}"));
        long form = template("RISK_FORM", "FORM", WorkflowGraphJson.enumForm("riskLevel", true, "LOW", "HIGH"));
        JsonNode graph = WorkflowGraphJson.graph().task("review", "审核")
            .action("risk", form, executor.accountId(), true).approval("ANY", approverA.accountId())
            .task("files", "资料").action("upload", upload, executor.accountId(), true).end("end")
            .chain("start", "review", "files", "end").build();
        long definitionId = published("ASSIGNEE_FLOW", graph, WorkflowGraphJson.emptyForm());
        long instanceId = start(definitionId, admin, WorkflowGraphJson.empty(),
            List.of(new StartWorkflowInstanceRequestBody.NodeApprovers("review", List.of(executor.actor()))), null)
            .assertOk().as(com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody.class)
            .getObjectId().transform(Long::parseLong);
        String risk = openAction(instanceId, "risk");

        submit(risk, approverA, WorkflowGraphJson.values("riskLevel", "LOW")).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        var failed = submit(risk, executor, WorkflowGraphJson.values("riskLevel", "MEDIUM")).assertOk()
            .as(WorkflowSubmissionResponseBody.class);
        var verified = submit(risk, executor, WorkflowGraphJson.values("riskLevel", "LOW")).assertOk()
            .as(WorkflowSubmissionResponseBody.class);
        decide(instanceId, executor, "APPROVE", null, null).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(RUNTIME + "/approvals/" + openApproval(instanceId, executor) + "/transfer", admin,
            new TransferWorkflowTaskRequestBody(approverA.actor(), "回避")).assertOk();
        approve(instanceId, approverA);
        var uploadResult = submit(openAction(instanceId, "upload"), executor, WorkflowGraphJson.empty()).assertOk()
            .as(WorkflowSubmissionResponseBody.class);

        assertAll(() -> assertEquals("UNVERIFIED", failed.getStatus()), () -> assertNotNull(failed.getFailureReason()),
            () -> assertEquals("VERIFIED", verified.getStatus()),
            () -> assertEquals(3, workflowData.verificationCount(instanceId)),
            () -> assertEquals("UNVERIFIED", uploadResult.getStatus()),
            () -> assertTrue(uploadResult.getFailureReason().contains("文件（files）必填")),
            () -> assertEquals("ACTIVE", nodeStatus(instanceId, "files", 1)));
    }

    /** 执行人转交动作后原执行人不能提交；不能转给自己；审批转交不能转给同节点其他审批人；转交写记录。 */
    @Test
    void test_transfer() {
        Operator other = person();
        long instanceId = started(published("ANY_FLOW", review("ANY"), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        String action = openAction(instanceId, "confirm");

        post(RUNTIME + "/action-runs/" + action + "/transfer", executor, new TransferWorkflowTaskRequestBody(
            executor.actor(), null)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(RUNTIME + "/action-runs/" + action + "/transfer", executor, new TransferWorkflowTaskRequestBody(
            other.actor(), "休假")).assertOk();
        submit(action, executor, WorkflowGraphJson.empty()).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        confirm(instanceId, "confirm", other);
        String approval = openApproval(instanceId, approverA);
        post(RUNTIME + "/approvals/" + approval + "/transfer", approverA, new TransferWorkflowTaskRequestBody(
            approverB.actor(), null)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(RUNTIME + "/approvals/" + approval + "/transfer", approverA, new TransferWorkflowTaskRequestBody(
            executor.actor(), null)).assertOk();
        approve(instanceId, executor);

        assertAll(() -> assertEquals(2, workflowData.transferCount(instanceId)),
            () -> assertEquals("COMPLETED", instanceStatus(instanceId)),
            () -> assertEquals(2, detail(instanceId, admin).getTransfers().size()));
    }

    /** 发起人终止后未完成动作取消、待审批撤回、实例只读；非发起人且无管理权限不能终止。 */
    @Test
    void test_terminate() {
        long role = operationsMocker.customRole("WORKFLOW_HANDLER", WorkflowPermissions.TASK_HANDLE);
        Operator handler = operationsMocker.operator(OperatorBuilder.administrator().roles(role));
        long instanceId = started(published("ANY_FLOW", review("ANY"), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        String action = openAction(instanceId, "confirm");

        post(RUNTIME + "/instances/" + instanceId + "/termination", handler, new TerminateWorkflowInstanceRequestBody(
            "撤销")).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(RUNTIME + "/instances/" + instanceId + "/termination", admin, new TerminateWorkflowInstanceRequestBody(
            "需求取消")).assertOk();
        auditChecker.assertEvent(requestId, "OPERATIONS_WORKFLOW_INSTANCE_TERMINATE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), "WORKFLOW_INSTANCE", Long.toString(instanceId));
        var readOnly = submit(action, executor, WorkflowGraphJson.empty()).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);

        Map<String, Object> instance = workflowData.instance(instanceId);
        assertAll(() -> assertEquals("TERMINATED", instance.get("status")),
            () -> assertEquals(admin.actor(), id(instance.get("terminated_by"))),
            () -> assertEquals("需求取消", instance.get("termination_reason")),
            () -> assertEquals("CANCELLED", workflowData.actionRuns(instanceId).get(0).get("status")),
            () -> assertEquals(List.of("review#1=CANCELLED"), nodeTrail(instanceId)),
            () -> assertTrue(readOnly.json().path("detail").asString().contains("实例已结束")));
    }

    /** 未参与者看不到实例详情（404）；只有办理权限不能按 ALL 查询；参与者按 MINE 查询可见；不存在的实例 404。 */
    @Test
    void test_visibilityAndPermission() {
        long role = operationsMocker.customRole("WORKFLOW_HANDLER", WorkflowPermissions.TASK_HANDLE);
        Operator outsider = operationsMocker.operator(OperatorBuilder.administrator().roles(role));
        long definitionId = published("ANY_FLOW", review("ANY"), WorkflowGraphJson.emptyForm());
        long instanceId = started(definitionId, admin, WorkflowGraphJson.empty());

        get(RUNTIME + "/instances/" + instanceId, outsider).assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(RUNTIME + "/instances?scope=ALL", outsider).assertProblem(403,
            OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        start(definitionId, outsider, WorkflowGraphJson.empty(), null, null).assertProblem(403,
            OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        get(RUNTIME + "/instances/123456789", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        var mine = get(RUNTIME + "/instances?scope=MINE", executor).assertOk().json();
        var outsiderMine = get(RUNTIME + "/instances", outsider).assertOk().json();
        var all = get(RUNTIME + "/instances?scope=ALL&status=RUNNING", admin).assertOk().json();

        assertAll(() -> assertEquals(1, mine.path("total").asInt()), () -> assertEquals(0, outsiderMine.path("total")
            .asInt()), () -> assertEquals(1, all.path("total").asInt()),
            () -> assertEquals(Long.toString(instanceId), all.path("items").get(0).path("instanceId").asString()));
    }

    /** 发起时版本号不是当前发布版本、启动值不合法或修改了不存在的动作时拒绝，不写实例。 */
    @Test
    void test_invalidParameter() {
        long definitionId = published("ANY_FLOW", review("ANY"), WorkflowGraphJson.emptyForm());

        post(RUNTIME + "/instances", admin, new StartWorkflowInstanceRequestBody(Long.toString(definitionId), 2, "实例",
            null, WorkflowGraphJson.empty(), null, null)).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        start(definitionId, admin, WorkflowGraphJson.values("unknown", "x"), null, null).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        start(definitionId, admin, WorkflowGraphJson.empty(), null,
            List.of(new StartWorkflowInstanceRequestBody.ActionExecutor("missing", executor.actor()))).assertProblem(400,
                CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(RUNTIME + "/instances", admin, new StartWorkflowInstanceRequestBody("abc", 1, "实例", null,
            WorkflowGraphJson.empty(), null, null)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("definitionId");

        assertEquals(0, get(RUNTIME + "/instances?scope=ALL", admin).assertOk().json().path("total").asInt());
    }

    /**
     * 列表附加信息：实例列表带流程名称、版本号、进度与当前节点处理人，按标题关键字搜索；待办带流程名与节点名；
     * 我已处理列出提交过的动作与已决定的审批；详情的动作运行带冻结的模板名称。
     */
    @Test
    void test_listEnrichmentAndHandled() {
        long definitionId = published("ANY_FLOW", review("ANY"), WorkflowGraphJson.emptyForm());
        long instanceId = started(definitionId, admin, WorkflowGraphJson.empty());
        started(definitionId, admin, WorkflowGraphJson.empty());
        String title = detail(instanceId, admin).getInstance().getTitle();
        String keyword = title.substring(title.indexOf(' ') + 1);

        var running = get(RUNTIME + "/instances?scope=ALL&keyword=" + keyword, admin).assertOk().json();
        var tasks = get(RUNTIME + "/my-tasks?keyword=" + keyword, executor).assertOk().json();
        confirm(instanceId, "confirm", executor);
        var approving = get(RUNTIME + "/instances?scope=ALL&keyword=" + keyword, admin).assertOk().json();
        approve(instanceId, approverA);
        var handledByExecutor = get(RUNTIME + "/my-tasks/handled", executor).assertOk().json();
        var handledByApprover = get(RUNTIME + "/my-tasks/handled?taskType=APPROVAL", approverA).assertOk().json();
        var done = get(RUNTIME + "/instances?scope=ALL&status=COMPLETED", admin).assertOk().json();
        var detail = detail(instanceId, admin);

        JsonNode first = running.path("items").get(0);
        JsonNode current = first.path("currentNodes").get(0);
        JsonNode approvingNode = approving.path("items").get(0).path("currentNodes").get(0);
        assertAll(() -> assertEquals(1, running.path("total").asInt()),
            () -> assertEquals("流程 " + definitionId, first.path("definitionName").asString()),
            () -> assertEquals(1, first.path("versionNo").asInt()),
            () -> assertEquals(0, first.path("completedTaskCount").asInt()),
            () -> assertEquals(1, first.path("totalTaskCount").asInt()),
            () -> assertEquals("review", current.path("nodeKey").asString()),
            () -> assertEquals("审核", current.path("nodeName").asString()),
            () -> assertEquals("ACTIVE", current.path("status").asString()),
            () -> assertEquals(executor.actor(), current.path("handlers").get(0).path("accountId").asString()),
            () -> assertEquals("APPROVING", approvingNode.path("status").asString()),
            () -> assertEquals(2, approvingNode.path("handlers").size()),
            () -> assertEquals(1, tasks.path("total").asInt()),
            () -> assertEquals("流程 " + definitionId, tasks.path("items").get(0).path("definitionName").asString()),
            () -> assertEquals("审核", tasks.path("items").get(0).path("nodeName").asString()),
            () -> assertEquals(1, handledByExecutor.path("total").asInt()),
            () -> assertEquals("VERIFIED", handledByExecutor.path("items").get(0).path("result").asString()),
            () -> assertEquals("审核", handledByExecutor.path("items").get(0).path("nodeName").asString()),
            () -> assertEquals("APPROVED", handledByApprover.path("items").get(0).path("result").asString()),
            () -> assertEquals("COMPLETED", handledByApprover.path("items").get(0).path("instanceStatus").asString()),
            () -> assertEquals(1, done.path("items").get(0).path("completedTaskCount").asInt()),
            () -> assertEquals(0, done.path("items").get(0).path("currentNodes").size()),
            () -> assertEquals("CONFIRM_SETUP 模板", detail.getActionRuns().get(0).getTemplateName()),
            () -> assertEquals("CONFIRM_SETUP 模板", detail.getActionSnapshot().path("templates").get(0)
                .path("templateName").asString()));
    }

    /** start → review（执行人确认 + 两名审批人）→ end。 */
    private JsonNode review(String mode) {
        return WorkflowGraphJson.graph().task("review", "审核").action("confirm", confirm, executor.accountId(), true)
            .approval(mode, approverA.accountId(), approverB.accountId()).end("end").chain("start", "review", "end")
            .build();
    }

    /** start → split → left / right（纯审批，审批人 A）→ join → final（审批人 B）→ end。 */
    private JsonNode parallel() {
        return WorkflowGraphJson.graph().split("split").task("left", "左").approval("ANY", approverA.accountId())
            .task("right", "右").approval("ANY", approverA.accountId()).join("join").task("final", "终审")
            .approval("ANY", approverB.accountId()).end("end")
            .edge("start_split", "start", "split").edge("split_left", "split", "left")
            .edge("split_right", "split", "right").edge("left_join", "left", "join").edge("right_join", "right", "join")
            .chain("join", "final", "end").build();
    }

    /** 某节点待决定审批（节点 key 限定）的同意。 */
    private void approve(long instanceId, Operator approver, String nodeKey) {
        var detail = detail(instanceId, admin);
        String nodeRunId = detail.getNodeRuns().stream().filter(run -> nodeKey.equals(run.getNodeKey()))
            .filter(run -> "APPROVING".equals(run.getStatus())).findFirst().orElseThrow().getNodeRunId();
        String approvalId = detail.getApprovals().stream().filter(row -> nodeRunId.equals(row.getNodeRunId()))
            .filter(row -> "PENDING".equals(row.getStatus())).findFirst().orElseThrow().getApprovalId();
        post(RUNTIME + "/approvals/" + approvalId + "/decision", approver,
            new com.paytech.platform.support.api.workflow.request.DecideWorkflowApprovalRequestBody("APPROVE", null,
                null)).assertOk();
    }

    /** 某节点最新一轮的状态；没有运行时为 null。 */
    private String nodeStatus(long instanceId, String nodeKey, int round) {
        return workflowData.nodeRuns(instanceId).stream().filter(row -> nodeKey.equals(row.get("node_key")))
            .filter(row -> ((Number) row.get("round_no")).intValue() == round).map(row -> (String) row.get("status"))
            .findFirst().orElse(null);
    }

    /** 待审批节点的驳回方式。 */
    private String rejectKind(long instanceId, String nodeKey) {
        return detail(instanceId, admin).getNodeRuns().stream().filter(run -> nodeKey.equals(run.getNodeKey()))
            .filter(run -> "APPROVING".equals(run.getStatus())).findFirst().orElseThrow().getRejectKind();
    }

    /** 某审批人在实例中的审批 ID（已决定的也算）。 */
    private String approvalId(long instanceId, Operator approver) {
        return id(workflowData.approvals(instanceId).stream().filter(row -> approver.actor().equals(id(
            row.get("approver_id")))).findFirst().orElseThrow().get("approval_id"));
    }

    private static String id(Object value) {
        return value == null ? null : Long.toString(((Number) value).longValue());
    }
}
