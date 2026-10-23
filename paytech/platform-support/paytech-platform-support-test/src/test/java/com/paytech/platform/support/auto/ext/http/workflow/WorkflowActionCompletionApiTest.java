package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.workflow.WorkflowInstanceApi;
import com.paytech.platform.support.api.workflow.request.SubmitWorkflowActionRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowSubmissionResponseBody;
import com.paytech.platform.support.test.base.WorkflowApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.ApprovalStatus;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 完成动作（需求 E 组，2026-10-22）：只检查必填与技术保护，不合格 422 且不改状态；合格即 COMPLETED 并按放行规则 A 推进；
 * 必选动作在节点进入审批后锁定（409），可选动作到放行前可完成；同意时兜底检查必选动作（409）；上传动作存量 minFiles 兼容。
 * 文件相关场景（扫描中可完成、数量与类型不再校验）见 {@link WorkflowAttachmentValidationApiTest}。
 *
 * @see WorkflowInstanceApi#submitAction(String, String, String, SubmitWorkflowActionRequestBody)
 */
class WorkflowActionCompletionApiTest extends WorkflowApiTestBase {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Operator executor;
    private Operator helper;
    private Operator approver;
    private long form;
    private long confirm;

    /** 本类公共：执行人、可选动作执行人、审批人；表单模板（必填 merchantName，选填 amount）与人工确认模板。 */
    @BeforeEach
    void setUpCompletionMocks() {
        executor = person();
        helper = person();
        approver = person();
        form = template("MERCHANT_FORM", WorkflowActionType.FORM.getCode(), merchantForm());
        confirm = template("CONFIRM_DONE", WorkflowActionType.MANUAL_CONFIRM.getCode(),
            WorkflowGraphJson.confirmConfig(true));
    }

    /** 必填缺失、值类型不符、未定义字段、说明超长均返回 422 字段错误，动作状态、版本与输出都不变，也不写验证记录。 */
    @Test
    void test_invalidParameter() {
        long instanceId = started(published("FILL_FLOW", fill(), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        String merchant = openAction(instanceId, "merchant");
        Map<String, Object> before = actionRun(instanceId, "merchant");

        submit(merchant, executor, merchant(null, "100")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("output.merchantName");
        submit(merchant, executor, merchant("商户 A", "12a")).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("output.amount");
        submit(merchant, executor, ((ObjectNode) merchant("商户 A", null)).put("extra", "x")).assertProblem(422,
            CommonErrorCode.VALIDATION_ERROR).assertFieldError("output.extra");
        submit(openAction(instanceId, "confirm"), executor, NODES.objectNode().put("note", "长".repeat(4001)))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("output.note");

        Map<String, Object> after = actionRun(instanceId, "merchant");
        assertAll(() -> assertEquals(ActionRunStatus.PENDING.getCode(), after.get("status")),
            () -> assertEquals(before.get("version"), after.get("version")),
            () -> assertNull(after.get("output_json")),
            () -> assertEquals(ActionRunStatus.PENDING.getCode(), actionRun(instanceId, "confirm").get("status")),
            () -> assertEquals(NodeRunStatus.ACTIVE.getCode(), nodeStatus(instanceId)),
            () -> assertEquals(0, workflowData.verificationCount(instanceId)));
    }

    /**
     * 合格即 COMPLETED（存量 maxLength、数字范围与小数位不再校验）；节点执行中可修改已完成的必选动作；必选全部完成后进入审批；可选动作说明选填（存量 noteRequired 忽略），
     * 在待审批时仍列入执行人待办并可完成；同意后放行，实例完成。
     */
    @Test
    void test_normalFlow() {
        long instanceId = started(published("FILL_FLOW", fill(), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        String merchant = openAction(instanceId, "merchant");

        var first = submit(merchant, executor, merchant("示例商户有限公司", null)).assertOk()
            .as(WorkflowSubmissionResponseBody.class);
        String afterFirst = nodeStatus(instanceId);
        submit(merchant, executor, merchant("商户 B", "88.5")).assertOk();
        confirm(instanceId, "confirm", executor);
        String afterRequired = nodeStatus(instanceId);
        var helperTasks = get(RUNTIME + "/my-tasks", helper).assertOk().json();
        var optional = submit(openAction(instanceId, "extra"), helper, WorkflowGraphJson.empty()).assertOk()
            .as(WorkflowSubmissionResponseBody.class);
        String afterOptional = nodeStatus(instanceId);
        approve(instanceId, approver);

        JsonNode output = objectMapper.readTree((String) actionRun(instanceId, "merchant").get("output_json"));
        assertAll(() -> assertEquals(ActionRunStatus.COMPLETED.getCode(), first.getStatus()),
            () -> assertEquals(NodeRunStatus.ACTIVE.getCode(), afterFirst),
            () -> assertEquals("商户 B", output.path("merchantName").asString()),
            () -> assertEquals("88.5", output.path("amount").asString()),
            () -> assertEquals(NodeRunStatus.APPROVING.getCode(), afterRequired),
            () -> assertEquals(1, helperTasks.path("total").asInt()),
            () -> assertEquals(ActionRunStatus.COMPLETED.getCode(), optional.getStatus()),
            () -> assertEquals(NodeRunStatus.APPROVING.getCode(), afterOptional),
            () -> assertEquals(NodeRunStatus.PASSED.getCode(), nodeStatus(instanceId)),
            () -> assertEquals(WorkflowInstanceStatus.COMPLETED.getCode(), instanceStatus(instanceId)),
            () -> assertEquals(0, workflowData.verificationCount(instanceId)));
    }

    /**
     * 节点进入审批后不能再完成或修改必选动作（409 列出动作名，状态与输出不变）；同意时兜底检查：必选动作未全部完成（构造数据）返回 409
     * 并列出动作名，审批仍待决定。
     */
    @Test
    void test_businessRuleViolation() {
        long instanceId = started(published("FILL_FLOW", fill(), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        String merchant = openAction(instanceId, "merchant");
        submit(merchant, executor, merchant("商户 A", null)).assertOk();
        confirm(instanceId, "confirm", executor);
        Map<String, Object> locked = actionRun(instanceId, "merchant");

        var changed = submit(merchant, executor, merchant("商户 B", null)).assertProblem(409,
            CommonErrorCode.CONCURRENT_REQUEST);
        long confirmRun = ((Number) actionRun(instanceId, "confirm").get("action_run_id")).longValue();
        workflowData.forceActionStatus(confirmRun, ActionRunStatus.PENDING.getCode());
        var approval = decide(instanceId, approver, "APPROVE", null, null).assertProblem(409,
            CommonErrorCode.CONCURRENT_REQUEST);

        assertAll(() -> assertTrue(changed.json().path("detail").asString().contains("节点已进入审批"),
                changed.json().path("detail").asString()),
            () -> assertTrue(changed.json().path("detail").asString().contains("MERCHANT_FORM 模板"),
                changed.json().path("detail").asString()),
            () -> assertFalse(changed.json().path("detail").asString().contains("：merchant"),
                changed.json().path("detail").asString()),
            () -> assertEquals(locked.get("version"), actionRun(instanceId, "merchant").get("version")),
            () -> assertEquals(locked.get("output_json"), actionRun(instanceId, "merchant").get("output_json")),
            () -> assertTrue(approval.json().path("detail").asString().contains("CONFIRM_DONE 模板"),
                approval.json().path("detail").asString()),
            () -> assertEquals(ApprovalStatus.PENDING.getCode(), workflowData.approvals(instanceId).get(0).get("status")),
            () -> assertEquals(NodeRunStatus.APPROVING.getCode(), nodeStatus(instanceId)));
    }

    /**
     * 上传文件动作配置兼容：存量 minFiles ≥ 1 与新 required 都表示至少一个文件，未带文件返回 422 output.files；
     * minFiles 为 0 且未要求时可不带文件完成。
     */
    @Test
    void test_edgeCase() {
        long legacy = template("UPLOAD_LEGACY", WorkflowActionType.FILE_UPLOAD.getCode(), NODES.objectNode()
            .put("minFiles", 1).put("maxFiles", 1));
        long flagged = template("UPLOAD_REQUIRED", WorkflowActionType.FILE_UPLOAD.getCode(), NODES.objectNode()
            .put("required", true));
        long optional = template("UPLOAD_OPTIONAL", WorkflowActionType.FILE_UPLOAD.getCode(), NODES.objectNode()
            .put("minFiles", 0));
        JsonNode graph = WorkflowGraphJson.graph().task("fill", "上传")
            .action("legacy", legacy, executor.accountId(), true).action("flagged", flagged, executor.accountId(), true)
            .action("optional", optional, executor.accountId(), true).approval("ANY", approver.accountId())
            .end("end").chain("start", "fill", "end").build();
        long instanceId = started(published("UPLOAD_FLOW", graph, WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());

        submit(openAction(instanceId, "legacy"), executor, WorkflowGraphJson.empty()).assertProblem(422,
            CommonErrorCode.VALIDATION_ERROR).assertFieldError("output.files");
        submit(openAction(instanceId, "flagged"), executor, NODES.objectNode().set("files", NODES.arrayNode()))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("output.files");
        var withoutFiles = submit(openAction(instanceId, "optional"), executor, WorkflowGraphJson.empty()).assertOk()
            .as(WorkflowSubmissionResponseBody.class);

        assertAll(() -> assertEquals(ActionRunStatus.PENDING.getCode(), actionRun(instanceId, "legacy").get("status")),
            () -> assertEquals(ActionRunStatus.PENDING.getCode(), actionRun(instanceId, "flagged").get("status")),
            () -> assertEquals(ActionRunStatus.COMPLETED.getCode(), withoutFiles.getStatus()),
            () -> assertEquals(NodeRunStatus.ACTIVE.getCode(), nodeStatus(instanceId)));
    }

    /** start → fill（必选表单 merchant、必选确认 confirm、可选确认 extra 由 helper 执行，审批人一名）→ end。 */
    private JsonNode fill() {
        return WorkflowGraphJson.graph().task("fill", "填写资料")
            .action("merchant", form, executor.accountId(), true).action("confirm", confirm, executor.accountId(), true)
            .action("extra", confirm, helper.accountId(), false).approval("ANY", approver.accountId())
            .end("end").chain("start", "fill", "end").build();
    }

    /** 表单：merchantName 必填 TEXT（存量 maxLength 5，已忽略），amount 选填 NUMBER（存量范围，已忽略）。 */
    private static JsonNode merchantForm() {
        ObjectNode config = NODES.objectNode();
        var fields = config.putArray("fields");
        ObjectNode name = fields.addObject().put("key", "merchantName").put("label", "商户名称")
            .put("type", FormFieldType.TEXT.getCode()).put("required", true);
        name.putObject("rules").put("maxLength", 5);
        ObjectNode amount = fields.addObject().put("key", "amount").put("label", "金额")
            .put("type", FormFieldType.NUMBER.getCode()).put("required", false);
        amount.putObject("rules").put("min", "0").put("max", "10").put("scale", 0);
        return config;
    }

    /** 表单提交值；为 null 的字段不出现。 */
    private static JsonNode merchant(String name, String amount) {
        ObjectNode values = NODES.objectNode();
        if (name != null) {
            values.put("merchantName", name);
        }
        if (amount != null) {
            values.put("amount", amount);
        }
        return values;
    }

    /** 某动作最新一轮的动作运行行。 */
    private Map<String, Object> actionRun(long instanceId, String actionKey) {
        return workflowData.actionRuns(instanceId).stream().filter(row -> actionKey.equals(row.get("action_key")))
            .reduce((first, last) -> last).orElseThrow();
    }

    /** fill 节点最新一轮状态。 */
    private String nodeStatus(long instanceId) {
        return workflowData.nodeRuns(instanceId).stream().filter(row -> "fill".equals(row.get("node_key")))
            .map(row -> (String) row.get("status")).reduce((first, last) -> last).orElseThrow();
    }
}
