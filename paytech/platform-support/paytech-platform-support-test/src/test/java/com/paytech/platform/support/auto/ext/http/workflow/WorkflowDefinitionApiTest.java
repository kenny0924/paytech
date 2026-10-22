package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.workflow.WorkflowDefinitionManagementApi;
import com.paytech.platform.support.api.workflow.request.ChangeWorkflowStatusRequestBody;
import com.paytech.platform.support.api.workflow.request.CreateWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.PublishWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.SaveWorkflowDraftRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowLaunchResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowProblemBody;
import com.paytech.platform.support.api.workflow.response.WorkflowVersionBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.base.WorkflowApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 流程定义：新建带空白草稿、草稿可保存不完整内容并返回问题、发布严格校验并冻结动作模板快照、
 * 发布后再编辑生成新草稿、停用后不可发起、版本冲突与权限。
 *
 * @see WorkflowDefinitionManagementApi
 */
class WorkflowDefinitionApiTest extends WorkflowApiTestBase {

    /** 新建为 ACTIVE、草稿 1；编码重复 400；详情给出空白草稿与问题列表。 */
    @Test
    void test_createDefinition() {
        var mutation = post(DEFINITIONS, admin, new CreateWorkflowDefinitionRequestBody("ONBOARDING", "开通", null))
            .assertOk().as(WorkflowMutationResponseBody.class);
        auditChecker.assertEvent(requestId, "OPERATIONS_WORKFLOW_DEFINITION_CREATE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), "WORKFLOW_DEFINITION", mutation.getObjectId());
        post(DEFINITIONS, admin, new CreateWorkflowDefinitionRequestBody("ONBOARDING", "重复", null))
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        var detail = definitionDetail(Long.parseLong(mutation.getObjectId()));
        assertAll(() -> assertEquals("ACTIVE", mutation.getStatus()), () -> assertEquals(1, mutation.getVersion()),
            () -> assertEquals(1, detail.getDefinition().getDraftVersionNo()),
            () -> assertNull(detail.getDefinition().getCurrentVersionNo()),
            () -> assertEquals("DRAFT", detail.getDraft().getStatus()),
            () -> assertEquals(2, detail.getDraft().getGraph().path("nodes").size()),
            () -> assertEquals(List.of("DRAFT"), detail.getVersions().stream().map(WorkflowVersionBody::getStatus)
                .toList()));
    }

    /** 不完整的草稿照常保存并返回问题；发布拒绝并提示问题数，详情返回同一问题列表，数据不变。 */
    @Test
    void test_draftProblemsBlockPublication() {
        long definitionId = createDefinition("ONBOARDING");
        JsonNode graph = WorkflowGraphJson.graph().task("review", "审核").end("end").chain("start", "review", "end")
            .build();

        var draft = saveDraft(definitionId, graph, WorkflowGraphJson.emptyForm());
        var rejected = publish(definitionId).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        var detail = definitionDetail(definitionId);

        assertAll(() -> assertEquals(2, draft.getVersion()), () -> assertEquals(1, draft.getDraftVersionNo()),
            () -> assertTrue(codes(draft.getProblems()).contains(WorkflowProblemCode.TASK_EMPTY.getCode())),
            () -> assertTrue(rejected.json().path("detail").asString().startsWith("发布校验未通过")),
            () -> assertEquals(codes(draft.getProblems()), codes(detail.getProblems())),
            () -> assertNull(workflowData.definition(definitionId).get("current_version_no")));
    }

    /** 发布冻结动作模板快照；再编辑生成草稿 2，v1 不变；发布 v2 后当前版本指向 2，两版均可读取。 */
    @Test
    void test_publishAndRevise() {
        Operator executor = person();
        Operator approver = person();
        long confirm = confirmTemplate("CONFIRM_SETUP");
        long definitionId = createDefinition("ONBOARDING");
        saveDraft(definitionId, simple(confirm, executor, approver), WorkflowGraphJson.emptyForm());

        var published = publish(definitionId).assertOk().as(WorkflowMutationResponseBody.class);
        auditChecker.assertEvent(requestId, "OPERATIONS_WORKFLOW_PUBLISH", AuditModels.Result.SUCCEEDED.getCode(),
            admin.actor(), "WORKFLOW_DEFINITION", Long.toString(definitionId));
        var revised = saveDraft(definitionId, simple(confirm, approver, executor), WorkflowGraphJson.emptyForm());
        publish(definitionId).assertOk();
        var v1 = get(DEFINITIONS + "/" + definitionId + "/versions/1", admin).assertOk().as(WorkflowVersionBody.class);
        var detail = definitionDetail(definitionId);
        var versions = workflowData.versions(definitionId);

        assertAll(() -> assertEquals("ACTIVE", published.getStatus()),
            () -> assertEquals(2, revised.getDraftVersionNo()),
            () -> assertEquals(2, ((Number) workflowData.definition(definitionId).get("current_version_no")).intValue()),
            () -> assertNull(workflowData.definition(definitionId).get("draft_version_no")),
            () -> assertEquals(List.of("PUBLISHED", "PUBLISHED"), versions.stream().map(row -> row.get("status"))
                .toList()),
            () -> assertTrue(((String) versions.get(0).get("action_snapshot_json")).contains(Long.toString(confirm))),
            () -> assertEquals(executor.actor(), v1.getGraph().path("nodes").get(1).path("actions").get(0)
                .path("executorId").asString()),
            () -> assertNull(detail.getDraft()), () -> assertEquals(2, detail.getVersions().size()),
            () -> assertEquals(java.util.Set.of(executor.actor(), approver.actor()), detail.getPeople().keySet()),
            () -> assertTrue(detail.getPeople().get(executor.actor()).endsWith("@example.test")));
    }

    /** 流程图超过 48 KiB 或启动表单超过 12 KiB 时拒绝保存草稿，定义版本不变；恰好在上限内可以保存。 */
    @Test
    void test_draftSizeLimits() {
        long definitionId = createDefinition("ONBOARDING");
        JsonNode bigGraph = WorkflowGraphJson.graph().task("review", "x".repeat(48 * 1024)).end("end")
            .chain("start", "review", "end").build();
        JsonNode bigForm = WorkflowGraphJson.enumForm("x".repeat(12 * 1024), false, "A");

        var graphRejected = put(DEFINITIONS + "/" + definitionId + "/draft", admin, new SaveWorkflowDraftRequestBody(
            "名称", null, bigGraph, WorkflowGraphJson.emptyForm(), 1)).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        var formRejected = put(DEFINITIONS + "/" + definitionId + "/draft", admin, new SaveWorkflowDraftRequestBody(
            "名称", null, WorkflowGraphJson.graph().end("end").chain("start", "end").build(), bigForm, 1))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        JsonNode nearLimit = WorkflowGraphJson.graph().task("review", "y".repeat(47 * 1024)).end("end")
            .chain("start", "review", "end").build();
        var saved = saveDraft(definitionId, nearLimit, WorkflowGraphJson.emptyForm());

        assertAll(() -> assertTrue(graphRejected.json().path("detail").asString().contains("48 KiB")),
            () -> assertTrue(formRejected.json().path("detail").asString().contains("12 KiB")),
            () -> assertEquals(2, saved.getVersion()));
    }

    /**
     * 流程图或启动表单结构不合法时按请求体字段返回 422 VALIDATION_ERROR，字段路径以 graph、startForm 为根；
     * 草稿与定义版本不变。
     */
    @Test
    void test_draftStructureErrors() {
        long definitionId = createDefinition("ONBOARDING");
        ObjectNode badNode = (ObjectNode) WorkflowGraphJson.graph().end("end").chain("start", "end").build();
        ((ObjectNode) badNode.get("nodes").get(1)).put("type", "NOPE");
        ObjectNode badForm = (ObjectNode) WorkflowGraphJson.enumForm("level", true, "A");
        ((ObjectNode) badForm.get("fields").get(0)).put("type", "NOPE");
        JsonNode valid = WorkflowGraphJson.graph().end("end").chain("start", "end").build();

        put(DEFINITIONS + "/" + definitionId + "/draft", admin, new SaveWorkflowDraftRequestBody("名称", null, badNode,
            WorkflowGraphJson.emptyForm(), 1)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("graph.nodes[1].type");
        put(DEFINITIONS + "/" + definitionId + "/draft", admin, new SaveWorkflowDraftRequestBody("名称", null, valid,
            badForm, 1)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("startForm.fields[0].type");

        assertEquals(1, ((Number) workflowData.definition(definitionId).get("version")).intValue());
    }

    /** 引用已停用模板或停用人员的流程不能发布。 */
    @Test
    void test_businessRuleViolation() {
        Operator executor = person();
        Operator approver = operationsMocker.operator(OperatorBuilder.administrator().accountStatus("DISABLED"));
        long confirm = confirmTemplate("CONFIRM_SETUP");
        post(TEMPLATES + "/" + confirm + "/status", admin, new ChangeWorkflowStatusRequestBody("INACTIVE", 1))
            .assertOk();
        long definitionId = createDefinition("ONBOARDING");

        var draft = saveDraft(definitionId, simple(confirm, executor, approver), WorkflowGraphJson.emptyForm());
        publish(definitionId).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertAll(() -> assertTrue(codes(draft.getProblems()).contains(WorkflowProblemCode.TEMPLATE_INACTIVE.getCode())),
            () -> assertTrue(codes(draft.getProblems()).contains(WorkflowProblemCode.APPROVER_INACTIVE.getCode())));
    }

    /** 停用后不在可发起列表中，发起被拒绝；版本不符的保存、发布与启停返回 409。 */
    @Test
    void test_statusAndConflict() {
        long definitionId = published("ONBOARDING", simple(confirmTemplate("CONFIRM_SETUP"), person(), person()),
            WorkflowGraphJson.emptyForm());
        int version = ((Number) workflowData.definition(definitionId).get("version")).intValue();
        var launch = get(RUNTIME + "/launchable-definitions/" + definitionId, admin).assertOk()
            .as(WorkflowLaunchResponseBody.class);

        post(DEFINITIONS + "/" + definitionId + "/status", admin, new ChangeWorkflowStatusRequestBody("INACTIVE",
            version)).assertOk();
        put(DEFINITIONS + "/" + definitionId + "/draft", admin, new SaveWorkflowDraftRequestBody("名称", null,
            WorkflowGraphJson.graph().end("end").chain("start", "end").build(), WorkflowGraphJson.emptyForm(),
            version)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        post(DEFINITIONS + "/" + definitionId + "/publication", admin, new PublishWorkflowDefinitionRequestBody(
            version)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        var launchable = get(RUNTIME + "/launchable-definitions", admin).assertOk().json();
        start(definitionId, admin, WorkflowGraphJson.empty(), null, null).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertAll(() -> assertEquals(1, launch.getVersionNo()), () -> assertEquals(0, launchable.path("total").asInt()),
            () -> assertEquals("INACTIVE", workflowData.definition(definitionId).get("status")),
            () -> assertEquals(3, workflowData.rowCount(), "只有定义、版本与模板三行"));
    }

    /** 只有读取权限的人员可以查看定义，不能新建、保存、发布或启停；不存在的定义 404。 */
    @Test
    void test_permissionDenied() {
        long definitionId = createDefinition("ONBOARDING");
        long role = operationsMocker.customRole("WORKFLOW_READER", WorkflowPermissions.DEFINITION_READ,
            WorkflowPermissions.TASK_HANDLE);
        Operator reader = operationsMocker.operator(OperatorBuilder.administrator().roles(role));

        get(DEFINITIONS + "/" + definitionId, reader).assertOk();
        post(DEFINITIONS, reader, new CreateWorkflowDefinitionRequestBody("OTHER", "其他", null))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        put(DEFINITIONS + "/" + definitionId + "/draft", reader, new SaveWorkflowDraftRequestBody("名称", null,
            WorkflowGraphJson.graph().build(), WorkflowGraphJson.emptyForm(), 1))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        post(DEFINITIONS + "/" + definitionId + "/publication", reader, new PublishWorkflowDefinitionRequestBody(1))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        get(RUNTIME + "/launchable-definitions", reader).assertProblem(403,
            OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        get(DEFINITIONS + "/123456789", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);

        assertEquals(1, ((Number) workflowData.definition(definitionId).get("version")).intValue());
    }

    /** start → review(执行人确认 + 审批人 ANY) → end。 */
    private static JsonNode simple(long templateId, Operator executor, Operator approver) {
        return WorkflowGraphJson.graph().task("review", "审核").action("confirm", templateId, executor.accountId(), true)
            .approval("ANY", approver.accountId()).end("end").chain("start", "review", "end").build();
    }

    private WorkflowDefinitionDetailResponseBody definitionDetail(long definitionId) {
        return get(DEFINITIONS + "/" + definitionId, admin).assertOk().as(WorkflowDefinitionDetailResponseBody.class);
    }

    private static List<String> codes(List<WorkflowProblemBody> problems) {
        return problems.stream().map(WorkflowProblemBody::getCode).toList();
    }
}
