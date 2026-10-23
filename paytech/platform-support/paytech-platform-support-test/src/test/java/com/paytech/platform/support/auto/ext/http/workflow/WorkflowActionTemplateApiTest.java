package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.workflow.WorkflowActionTemplateManagementApi;
import com.paytech.platform.support.api.workflow.request.ChangeWorkflowStatusRequestBody;
import com.paytech.platform.support.api.workflow.request.CreateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.request.UpdateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels;
import com.paytech.platform.support.test.base.WorkflowApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 动作模板维护：新建即 ACTIVE、配置按类型严格校验、修改与启停递增版本、版本冲突 409、编码唯一、权限复核与审计。
 *
 * @see WorkflowActionTemplateManagementApi
 */
class WorkflowActionTemplateApiTest extends WorkflowApiTestBase {

    /** 新建为 ACTIVE 版本 1，详情返回配置，列表按类型过滤不带配置，写一条审计。 */
    @Test
    void test_createAndRead() {
        var mutation = post(TEMPLATES, admin, new CreateWorkflowActionTemplateRequestBody("CONFIRM_SETUP", "确认配置",
            "MANUAL_CONFIRM", "人工确认", WorkflowGraphJson.confirmConfig(true))).assertOk()
            .as(WorkflowMutationResponseBody.class);
        auditChecker.assertEvent(requestId, "OPERATIONS_WORKFLOW_TEMPLATE_CREATE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), "WORKFLOW_ACTION_TEMPLATE",
            mutation.getObjectId());
        template("FILL_FORM", "FORM", WorkflowGraphJson.enumForm("riskLevel", true, "LOW", "HIGH"));

        var detail = get(TEMPLATES + "/" + mutation.getObjectId(), admin).assertOk()
            .as(WorkflowActionTemplateResponseBody.class);
        var page = get(TEMPLATES + "?actionType=MANUAL_CONFIRM", admin).assertOk().json();

        assertAll(() -> assertEquals("WORKFLOW_ACTION_TEMPLATE", mutation.getObjectType()),
            () -> assertEquals("ACTIVE", mutation.getStatus()), () -> assertEquals(1, mutation.getVersion()),
            () -> assertEquals("CONFIRM_SETUP", detail.getTemplateCode()),
            () -> assertEquals(Long.toString(admin.accountId()), detail.getUpdatedBy()),
            () -> assertNotNull(detail.getUpdatedByName()),
            () -> assertEquals(detail.getUpdatedByName(), page.path("items").get(0).path("updatedByName").asString()),
            () -> assertTrue(detail.getConfig().path("noteRequired").asBoolean()),
            () -> assertEquals(1, page.path("items").size()),
            () -> assertEquals("CONFIRM_SETUP", page.path("items").get(0).path("templateCode").asString()),
            () -> assertTrue(page.path("items").get(0).path("config").isMissingNode()
                || page.path("items").get(0).path("config").isNull()));
    }

    /** 编码重复返回 400；配置含未知属性或字段类型不合法时按请求体字段返回 422，均不写库。 */
    @Test
    void test_invalidParameter() {
        long id = confirmTemplate("CONFIRM_SETUP");
        ObjectNode badForm = (ObjectNode) WorkflowGraphJson.enumForm("riskLevel", true, "LOW", "HIGH");
        ((ArrayNode) badForm.get("fields")).addObject().put("key", "note").put("label", "备注").put("type", "NOPE");

        post(TEMPLATES, admin, new CreateWorkflowActionTemplateRequestBody("CONFIRM_SETUP", "重复", "MANUAL_CONFIRM",
            null, WorkflowGraphJson.confirmConfig(false))).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        post(TEMPLATES, admin, new CreateWorkflowActionTemplateRequestBody("BAD_CONFIG", "未知属性", "MANUAL_CONFIRM",
            null, WorkflowGraphJson.values("unknown", "x"))).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("config.unknown");
        post(TEMPLATES, admin, new CreateWorkflowActionTemplateRequestBody("BAD_FIELD_TYPE", "字段类型", "FORM",
            null, badForm)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("config.fields[1].type");
        put(TEMPLATES + "/" + id, admin, new UpdateWorkflowActionTemplateRequestBody("改名", null,
            WorkflowGraphJson.values("noteRequired", "yes"), 1)).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("config.noteRequired");
        post(TEMPLATES, admin, new CreateWorkflowActionTemplateRequestBody("bad", "编码", "MANUAL_CONFIRM", null,
            WorkflowGraphJson.confirmConfig(false))).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("templateCode");
        post(TEMPLATES, admin, new CreateWorkflowActionTemplateRequestBody("BAD_TYPE", "类型", "SCRIPT", null,
            WorkflowGraphJson.confirmConfig(false))).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("actionType");

        assertAll(() -> assertEquals(1, get(TEMPLATES, admin).assertOk().json().path("total").asInt()),
            () -> assertEquals(1, ((Number) workflowData.template(id).get("version")).intValue()));
    }

    /** 修改与停用、启用各递增版本；读取时版本不符返回 409，数据不变。 */
    @Test
    void test_updateStatusAndConflict() {
        long id = confirmTemplate("CONFIRM_SETUP");

        var updated = put(TEMPLATES + "/" + id, admin, new UpdateWorkflowActionTemplateRequestBody("新名称", "说明",
            WorkflowGraphJson.confirmConfig(true), 1)).assertOk().as(WorkflowMutationResponseBody.class);
        auditChecker.assertEvent(requestId, "OPERATIONS_WORKFLOW_TEMPLATE_UPDATE",
            AuditModels.Result.SUCCEEDED.getCode(), admin.actor(), "WORKFLOW_ACTION_TEMPLATE", Long.toString(id));
        var inactive = post(TEMPLATES + "/" + id + "/status", admin, new ChangeWorkflowStatusRequestBody("INACTIVE", 2))
            .assertOk().as(WorkflowMutationResponseBody.class);
        put(TEMPLATES + "/" + id, admin, new UpdateWorkflowActionTemplateRequestBody("过期", null,
            WorkflowGraphJson.confirmConfig(false), 2)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        post(TEMPLATES + "/" + id + "/status", admin, new ChangeWorkflowStatusRequestBody("ACTIVE", 2))
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);

        Map<String, Object> row = workflowData.template(id);
        assertAll(() -> assertEquals(2, updated.getVersion()), () -> assertEquals("INACTIVE", inactive.getStatus()),
            () -> assertEquals(3, inactive.getVersion()), () -> assertEquals("INACTIVE", row.get("status")),
            () -> assertEquals(3, ((Number) row.get("version")).intValue()),
            () -> assertTrue(objectMapper.readTree((String) row.get("config_json")).path("noteRequired").asBoolean()));
    }

    /** 引用查询：列出冻结该模板的已发布版本、冻结版本是否等于当前版本与运行中实例数；不存在的模板 404。 */
    @Test
    void test_templateReferences() {
        long id = confirmTemplate("CONFIRM_SETUP");
        Operator executor = person();
        Operator approver = person();
        long definitionId = createDefinition("ONBOARDING");
        saveDraft(definitionId, WorkflowGraphJson.graph().task("review", "审核")
            .action("confirm", id, executor.accountId(), true).approval("ANY", approver.accountId()).end("end")
            .chain("start", "review", "end").build(), WorkflowGraphJson.emptyForm());
        publish(definitionId).assertOk();
        post(RUNTIME + "/instances", admin, new com.paytech.platform.support.api.workflow.request
            .StartWorkflowInstanceRequestBody(Long.toString(definitionId), 1, "实例", null, WorkflowGraphJson.empty(), null,
                null)).assertOk();
        put(TEMPLATES + "/" + id, admin, new UpdateWorkflowActionTemplateRequestBody("新名称", null,
            WorkflowGraphJson.confirmConfig(true), 1)).assertOk();

        var references = get(TEMPLATES + "/" + id + "/references", admin).assertOk().json();
        var unused = get(TEMPLATES + "/" + confirmTemplate("UNUSED") + "/references", admin).assertOk().json();
        get(TEMPLATES + "/123456789/references", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);

        var item = references.path("items").get(0);
        assertAll(() -> assertEquals(2, references.path("currentTemplateVersion").asInt()),
            () -> assertEquals(1, references.path("items").size()),
            () -> assertEquals(Long.toString(definitionId), item.path("definitionId").asString()),
            () -> assertEquals(1, item.path("versionNo").asInt()),
            () -> assertTrue(item.path("currentVersion").asBoolean()),
            () -> assertEquals(1, item.path("frozenTemplateVersion").asInt()),
            () -> assertFalse(item.path("sameAsCurrent").asBoolean()),
            () -> assertEquals(1, item.path("runningInstanceCount").asLong()),
            () -> assertEquals(0, unused.path("items").size()));
    }

    /**
     * 列表的被引用流程数：按流程定义当前发布版本去重计数——两个流程引用为 2；只有旧版本引用、当前版本已不引用的流程不计；
     * 只在草稿中引用或从未被引用为 0。
     */
    @Test
    void test_listReferencedDefinitionCount() {
        long shared = confirmTemplate("SHARED_CONFIRM");
        long other = confirmTemplate("OTHER_CONFIRM");
        long unused = confirmTemplate("UNUSED_CONFIRM");
        long draftOnly = confirmTemplate("DRAFT_ONLY_CONFIRM");
        Operator executor = person();
        Operator approver = person();
        published("ONBOARDING", referencing(shared, executor, approver), WorkflowGraphJson.emptyForm());
        published("CHANNEL_SETUP", referencing(shared, executor, approver), WorkflowGraphJson.emptyForm());
        long replaced = published("REPLACED", referencing(shared, executor, approver), WorkflowGraphJson.emptyForm());
        saveDraft(replaced, referencing(other, executor, approver), WorkflowGraphJson.emptyForm());
        publish(replaced).assertOk();
        saveDraft(createDefinition("DRAFT_ONLY"), referencing(draftOnly, executor, approver),
            WorkflowGraphJson.emptyForm());

        var items = get(TEMPLATES + "?size=50", admin).assertOk().json().path("items");
        Map<String, Integer> counts = new HashMap<>();
        items.forEach(item -> counts.put(item.path("templateId").asString(),
            item.path("referencedDefinitionCount").asInt(-1)));
        var detail = get(TEMPLATES + "/" + shared, admin).assertOk().json();

        assertAll(() -> assertEquals(4, items.size()),
            () -> assertEquals(2, counts.get(Long.toString(shared))),
            () -> assertEquals(1, counts.get(Long.toString(other))),
            () -> assertEquals(0, counts.get(Long.toString(unused))),
            () -> assertEquals(0, counts.get(Long.toString(draftOnly))),
            () -> assertTrue(detail.path("referencedDefinitionCount").isMissingNode()
                || detail.path("referencedDefinitionCount").isNull()));
    }

    /** 一个任务节点引用指定模板的流程图。 */
    private static JsonNode referencing(long templateId, Operator executor, Operator approver) {
        return WorkflowGraphJson.graph().task("review", "审核").action("confirm", templateId, executor.accountId(), true)
            .approval("ANY", approver.accountId()).end("end").chain("start", "review", "end").build();
    }

    /** 不存在的模板：读取、修改与启停均 404。 */
    @Test
    void test_noDataFound() {
        get(TEMPLATES + "/123456789", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        put(TEMPLATES + "/123456789", admin, new UpdateWorkflowActionTemplateRequestBody("名称", null,
            WorkflowGraphJson.confirmConfig(false), 1)).assertProblem(404, CommonErrorCode.NOT_FOUND);
        post(TEMPLATES + "/123456789/status", admin, new ChangeWorkflowStatusRequestBody("INACTIVE", 1))
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 只有定义读取权限的人员可以读取模板，不能新建、修改或启停。 */
    @Test
    void test_permissionDenied() {
        long id = confirmTemplate("CONFIRM_SETUP");
        long role = operationsMocker.customRole("WORKFLOW_READER", WorkflowPermissions.DEFINITION_READ);
        Operator reader = operationsMocker.operator(OperatorBuilder.administrator().roles(role));

        get(TEMPLATES + "/" + id, reader).assertOk();
        post(TEMPLATES, reader, new CreateWorkflowActionTemplateRequestBody("OTHER", "其他", "MANUAL_CONFIRM", null,
            WorkflowGraphJson.confirmConfig(false))).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        put(TEMPLATES + "/" + id, reader, new UpdateWorkflowActionTemplateRequestBody("名称", null,
            WorkflowGraphJson.confirmConfig(false), 1))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        post(TEMPLATES + "/" + id + "/status", reader, new ChangeWorkflowStatusRequestBody("INACTIVE", 1))
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);

        assertAll(() -> assertEquals(1, ((Number) workflowData.template(id).get("version")).intValue()),
            () -> assertEquals(0, get(TEMPLATES + "?keyword=OTHER", admin).assertOk().json().path("total").asInt()));
    }
}
