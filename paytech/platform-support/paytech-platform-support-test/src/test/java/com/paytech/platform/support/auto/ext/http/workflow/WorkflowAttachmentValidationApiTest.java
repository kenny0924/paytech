package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.workflow.WorkflowInstanceApi;
import com.paytech.platform.support.api.workflow.request.StartWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.request.SubmitWorkflowActionRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowAttachmentBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowSubmissionResponseBody;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.base.WorkflowApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.WorkflowFileMocker;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 工作流附件的完成与发起校验（技术方案第 5 节，2026-10-22 去掉动作验证）：文件值为文件地址，格式不合法（含纯数字 ID）返回
 * 422 字段错误；上传人、来源与原因、不能跨实例复用；发起与完成动作同一扫描规则：扫描中不挡，已确认未通过的文件在该文件
 * 路径上返回 422；文件数量与类型不再校验；实例详情返回附件元数据与扫描状态。
 * 文件前置数据直接插入（不经存储），真实上传与下载授权见 {@link WorkflowAttachmentApiTest}。
 *
 * @see WorkflowInstanceApi#startInstance(String, String, StartWorkflowInstanceRequestBody)
 * @see WorkflowInstanceApi#submitAction(String, String, String, SubmitWorkflowActionRequestBody)
 * @see WorkflowInstanceApi#getInstance(String, String, String)
 */
class WorkflowAttachmentValidationApiTest extends WorkflowApiTestBase {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Autowired
    private WorkflowFileMocker files;

    private Operator executor;
    private Operator approver;

    /** 本类公共：一名执行人与一名审批人。 */
    @BeforeEach
    void setUpAttachmentMocks() {
        executor = person();
        approver = person();
    }

    /**
     * 发起：他人上传、非工作流附件拒绝且不建实例；未通过扫描返回 422 字段错误；扫描中可以发起；类型与数量不再校验；
     * 合法后详情返回附件元数据；同一文件不能再用于另一个实例。
     */
    @Test
    void test_startValidation() {
        long definitionId = published("FILE_START", reviewOnly(), contractForm());
        long others = files.available(approver.accountId());
        long onboarding = files.file(admin.accountId(), FileSourceCodes.PLATFORM_SUPPORT,
            FileBuilder.REASON_CODE, FileScanStatus.AVAILABLE, WorkflowFileMocker.PDF);
        long scanning = files.attachment(admin.accountId(), FileScanStatus.SCANNING, WorkflowFileMocker.PDF);
        long quarantined = files.attachment(admin.accountId(), FileScanStatus.QUARANTINED, WorkflowFileMocker.PDF);
        long png = files.attachment(admin.accountId(), FileScanStatus.AVAILABLE, "image/png");
        long first = files.available(admin.accountId());
        long second = files.available(admin.accountId());

        String notOwn = rejected(definitionId, others);
        String notWorkflow = rejected(definitionId, onboarding);
        assertScanFailed(start(definitionId, admin, contract(quarantined), null, null), "startValues.contractFiles[0]");
        long scanningInstance = started(definitionId, admin, contract(scanning));
        start(definitionId, admin, contractValues(NODES.arrayNode().add(Long.toString(first))), null, null)
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("startValues.contractFiles[0]");
        long instanceId = started(definitionId, admin, contract(first));
        long ignoredRules = started(definitionId, admin, contract(png, second));
        String reused = rejected(definitionId, first);
        WorkflowInstanceDetailResponseBody detail = detail(instanceId, admin);

        WorkflowAttachmentBody attachment = detail.getFiles().getFirst();
        assertAll(() -> assertTrue(notOwn.contains("不是提交人本人上传"), notOwn),
            () -> assertTrue(notWorkflow.contains("不是工作流附件"), notWorkflow),
            () -> assertEquals(FileScanStatus.SCANNING.getCode(),
                detail(scanningInstance, admin).getFiles().getFirst().getScanStatus()),
            () -> assertEquals(2, detail(ignoredRules, admin).getFiles().size()),
            () -> assertTrue(reused.contains("已用于其他流程实例"), reused),
            () -> assertEquals(1, detail.getFiles().size()),
            () -> assertEquals(Long.toString(first), attachment.getFileId()),
            () -> assertEquals(FileAddress.of(first).value(), attachment.getFileAddress()),
            () -> assertEquals(List.of(FileAddress.of(first).value()),
                strings(detail.getStartValues().path("contractFiles"))),
            () -> assertEquals("AVAILABLE", attachment.getScanStatus()),
            () -> assertEquals(WorkflowFileMocker.PDF, attachment.getMimeType()),
            () -> assertEquals(Long.toString(WorkflowFileMocker.SIZE), attachment.getSizeBytes()),
            () -> assertEquals(admin.actor(), attachment.getUploaderId()),
            () -> assertEquals(3, workflowData.instanceCount()));
    }

    /**
     * 上传文件动作：地址格式不合法返回 422 字段错误；他人上传的文件直接拒绝且不保存；已确认未通过扫描的文件返回 422
     * 且状态不变；仍在扫描的文件可以完成，
     * 节点开放审批，不写验证记录；实例详情给出文件当前扫描状态。
     */
    @Test
    void test_fileUploadScanningCompletes() {
        long template = template("FILE_SETUP", "FILE_UPLOAD", uploadConfig());
        long instanceId = started(published("FILE_ACTION", uploadTask(template), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        String actionRunId = openAction(instanceId, "upload");
        long others = files.available(approver.accountId());
        long own = files.attachment(executor.accountId(), FileScanStatus.PENDING_SCAN, WorkflowFileMocker.PDF);
        long quarantined = files.attachment(executor.accountId(), FileScanStatus.QUARANTINED, WorkflowFileMocker.PDF);

        ObjectNode badAddress = NODES.objectNode();
        badAddress.putArray("files").add(FileAddress.of(own).value()).add("/files/v1/0");
        submit(actionRunId, executor, badAddress).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("output.files[1]");
        var foreign = submit(actionRunId, executor, uploadOutput(others)).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        String afterForeign = actionStatus(instanceId, "upload");
        assertScanFailed(submit(actionRunId, executor, uploadOutput(quarantined)), "output.files[0]");
        String afterFailedScan = actionStatus(instanceId, "upload");
        var completed = submit(actionRunId, executor, uploadOutput(own)).assertOk()
            .as(WorkflowSubmissionResponseBody.class);

        WorkflowInstanceDetailResponseBody detail = detail(instanceId, admin);
        assertAll(() -> assertTrue(foreign.json().path("detail").asString().contains("不是提交人本人上传")),
            () -> assertEquals(ActionRunStatus.PENDING.getCode(), afterForeign),
            () -> assertEquals(ActionRunStatus.PENDING.getCode(), afterFailedScan),
            () -> assertEquals(ActionRunStatus.COMPLETED.getCode(), completed.getStatus()),
            () -> assertEquals(NodeRunStatus.APPROVING.getCode(), nodeStatus(instanceId, "upload")),
            () -> assertEquals(0, workflowData.verificationCount(instanceId)),
            () -> assertEquals(Long.toString(own), detail.getFiles().getFirst().getFileId()),
            () -> assertEquals(FileScanStatus.PENDING_SCAN.getCode(), detail.getFiles().getFirst().getScanStatus()));
    }

    /**
     * 表单动作的文件字段：必填为空返回 422；未通过扫描的文件在该元素路径上返回 422；存量的数量与类型规则不再校验
     * （3 个文件含 PNG 也可完成）；
     * 可选的人工确认存量 noteRequired 忽略，不带说明也能完成。
     */
    @Test
    void test_formFileFieldAndRules() {
        long form = template("FILE_FORM", "FORM", contractForm());
        long confirm = template("NOTE_CONFIRM", "MANUAL_CONFIRM", WorkflowGraphJson.confirmConfig(true));
        JsonNode graph = WorkflowGraphJson.graph().task("fill", "填写").action("contract", form, executor.accountId(), true)
            .action("note", confirm, executor.accountId(), false).approval("ANY", approver.accountId()).end("end")
            .chain("start", "fill", "end").build();
        long instanceId = started(published("FILE_FORM_FLOW", graph, WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        String contract = openAction(instanceId, "contract");
        String note = openAction(instanceId, "note");
        long png = files.attachment(executor.accountId(), FileScanStatus.AVAILABLE, "image/png");
        long pdf = files.available(executor.accountId());
        long rejected = files.attachment(executor.accountId(), FileScanStatus.REJECTED, WorkflowFileMocker.PDF);
        long third = files.available(executor.accountId());

        submit(contract, executor, contractValues(NODES.arrayNode())).assertProblem(422,
            CommonErrorCode.VALIDATION_ERROR).assertFieldError("output.contractFiles");
        var noteResult = submit(note, executor, WorkflowGraphJson.empty()).assertOk()
            .as(WorkflowSubmissionResponseBody.class);
        String afterNote = nodeStatus(instanceId, "fill");
        assertScanFailed(submit(contract, executor, contract(png, pdf, rejected)), "output.contractFiles[2]");
        var valid = submit(contract, executor, contract(png, pdf, third)).assertOk()
            .as(WorkflowSubmissionResponseBody.class);

        assertAll(() -> assertEquals(ActionRunStatus.COMPLETED.getCode(), noteResult.getStatus()),
            () -> assertEquals(NodeRunStatus.ACTIVE.getCode(), afterNote),
            () -> assertEquals(ActionRunStatus.COMPLETED.getCode(), valid.getStatus()),
            () -> assertEquals(3, detail(instanceId, admin).getFiles().size()),
            () -> assertEquals(NodeRunStatus.APPROVING.getCode(), nodeStatus(instanceId, "fill")));
    }

    /** 以给定文件发起并要求被拒，返回拒绝说明。 */
    private String rejected(long definitionId, long... fileIds) {
        PlatformResponseChecker response = start(definitionId, admin, contract(fileIds), null, null);
        return response.assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED).json().path("detail").asString();
    }

    /** 已确认未通过扫描的文件：422 且字段错误落在该文件路径上，原因为统一文案。 */
    private static void assertScanFailed(PlatformResponseChecker response, String field) {
        JsonNode errors = response.assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError(field).json()
            .path("errors");
        assertTrue(errors.valueStream().anyMatch(error -> field.equals(error.path("field").asString())
            && AttachmentRules.SCAN_FAILED.equals(error.path("message").asString())), errors.toString());
    }

    /** 某动作最新一轮的状态。 */
    private String actionStatus(long instanceId, String actionKey) {
        return workflowData.actionRuns(instanceId).stream().filter(row -> actionKey.equals(row.get("action_key")))
            .map(row -> (String) row.get("status")).reduce((first, last) -> last).orElseThrow();
    }

    /** 节点最新一轮状态。 */
    private String nodeStatus(long instanceId, String nodeKey) {
        return workflowData.nodeRuns(instanceId).stream().filter(row -> nodeKey.equals(row.get("node_key")))
            .map(row -> (String) row.get("status")).reduce((first, last) -> last).orElseThrow();
    }

    /** 只有审批的单节点流程。 */
    private JsonNode reviewOnly() {
        return WorkflowGraphJson.graph().task("review", "审核").approval("ANY", approver.accountId()).end("end")
            .chain("start", "review", "end").build();
    }

    /** 上传任务节点：执行人上传、审批人审批。 */
    private JsonNode uploadTask(long template) {
        return WorkflowGraphJson.graph().task("upload", "上传合同").action("upload", template, executor.accountId(), true)
            .approval("ANY", approver.accountId()).end("end").chain("start", "upload", "end").build();
    }

    /** 含一个必填 FILE 字段 contractFiles 的表单：存量规则 1—2 个 PDF（2026-10-22 起忽略）。 */
    private static JsonNode contractForm() {
        ObjectNode form = NODES.objectNode();
        ObjectNode field = form.putArray("fields").addObject();
        field.put("key", "contractFiles");
        field.put("label", "渠道合同");
        field.put("type", "FILE");
        field.put("required", true);
        ObjectNode rules = field.putObject("rules");
        rules.put("minFiles", 1);
        rules.put("maxFiles", 2);
        rules.putArray("mimeTypes").add(WorkflowFileMocker.PDF);
        return form;
    }

    /** 上传文件动作配置：存量 1—2 个 PDF（minFiles 1 视为至少一个文件，数量与类型不再校验）。 */
    private static JsonNode uploadConfig() {
        ObjectNode config = NODES.objectNode();
        config.put("minFiles", 1);
        config.put("maxFiles", 2);
        config.putArray("mimeTypes").add(WorkflowFileMocker.PDF);
        config.put("instruction", "上传签署后的合同");
        return config;
    }

    /** {"contractFiles": [文件 ID...]}。 */
    private static JsonNode contract(long... fileIds) {
        return contractValues(ids(fileIds));
    }

    /** {"contractFiles": 值}。 */
    private static JsonNode contractValues(JsonNode value) {
        ObjectNode values = NODES.objectNode();
        values.set("contractFiles", value);
        return values;
    }

    /** {"files": [文件 ID...]}。 */
    private static JsonNode uploadOutput(long... fileIds) {
        ObjectNode output = NODES.objectNode();
        output.set("files", ids(fileIds));
        return output;
    }

    /** 文件地址数组。 */
    private static ArrayNode ids(long... fileIds) {
        ArrayNode array = NODES.arrayNode();
        for (long fileId : fileIds) {
            array.add(FileAddress.of(fileId).value());
        }
        return array;
    }

    /** 字符串数组转列表。 */
    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asString()));
        return values;
    }
}
