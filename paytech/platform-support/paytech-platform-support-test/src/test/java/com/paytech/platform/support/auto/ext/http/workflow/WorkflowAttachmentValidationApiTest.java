package com.paytech.platform.support.auto.ext.http.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 工作流附件的提交与发起校验（技术方案第 5 节）：文件值为文件地址，格式不合法（含纯数字 ID）返回 422 字段错误；上传人、来源与原因、扫描状态、数量与类型、不能跨实例复用；
 * FILE_UPLOAD 扫描中先记为未通过，扫描结束后重新验证通过并开放审批；实例详情返回附件元数据。
 * 文件前置数据直接插入（不经存储），真实上传与下载授权见 {@link WorkflowAttachmentApiTest}。
 *
 * @see WorkflowInstanceApi#startInstance(String, String, StartWorkflowInstanceRequestBody)
 * @see WorkflowInstanceApi#submitAction(String, String, String, SubmitWorkflowActionRequestBody)
 * @see WorkflowInstanceApi#reverifyAction(String, String, String)
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
     * 发起：他人上传、非工作流附件、扫描中、类型不符、数量超限均拒绝且不建实例；合法后详情返回附件元数据；
     * 同一文件不能再用于另一个实例。
     */
    @Test
    void test_startValidation() {
        long definitionId = published("FILE_START", reviewOnly(), contractForm());
        long others = files.available(approver.accountId());
        long onboarding = files.file(admin.accountId(), FileSourceCodes.PLATFORM_SUPPORT,
            FileBuilder.REASON_CODE, FileScanStatus.AVAILABLE, WorkflowFileMocker.PDF);
        long scanning = files.attachment(admin.accountId(), FileScanStatus.SCANNING, WorkflowFileMocker.PDF);
        long png = files.attachment(admin.accountId(), FileScanStatus.AVAILABLE, "image/png");
        long first = files.available(admin.accountId());
        long second = files.available(admin.accountId());
        long third = files.available(admin.accountId());

        String notOwn = rejected(definitionId, others);
        String notWorkflow = rejected(definitionId, onboarding);
        String scanningDetail = rejected(definitionId, scanning);
        String mimeDetail = rejected(definitionId, png);
        String countDetail = rejected(definitionId, first, second, third);
        start(definitionId, admin, contractValues(NODES.arrayNode().add(Long.toString(first))), null, null)
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("startValues.contractFiles[0]");
        long instanceId = started(definitionId, admin, contract(first));
        String reused = rejected(definitionId, first);
        WorkflowInstanceDetailResponseBody detail = detail(instanceId, admin);

        WorkflowAttachmentBody attachment = detail.getFiles().getFirst();
        assertAll(() -> assertTrue(notOwn.contains("不是提交人本人上传"), notOwn),
            () -> assertTrue(notWorkflow.contains("不是工作流附件"), notWorkflow),
            () -> assertEquals("文件扫描中，请稍后再提交", scanningDetail),
            () -> assertTrue(mimeDetail.contains("image/png"), mimeDetail),
            () -> assertTrue(countDetail.contains("最多 2 个文件"), countDetail),
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
            () -> assertEquals(1, workflowData.instanceCount()));
    }

    /**
     * 上传文件动作：他人上传的文件直接拒绝且不保存；扫描中提交记为未通过；非执行人与扫描未结束时重新验证不放行；
     * 扫描通过后重新验证通过，验证证据为文件 ID，节点开放审批；已通过的动作（节点已进入审批）不能再重新验证。
     */
    @Test
    void test_fileUploadScanThenReverify() {
        long template = template("FILE_SETUP", "FILE_UPLOAD", uploadConfig());
        long instanceId = started(published("FILE_ACTION", uploadTask(template), WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        String actionRunId = openAction(instanceId, "upload");
        long others = files.available(approver.accountId());
        long own = files.attachment(executor.accountId(), FileScanStatus.PENDING_SCAN, WorkflowFileMocker.PDF);

        ObjectNode badAddress = NODES.objectNode();
        badAddress.putArray("files").add(FileAddress.of(own).value()).add("/files/v1/0");
        submit(actionRunId, executor, badAddress).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("output.files[1]");
        var foreign = submit(actionRunId, executor, uploadOutput(others)).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        int afterForeign = workflowData.verificationCount(instanceId);
        var pending = submit(actionRunId, executor, uploadOutput(own)).assertOk().as(WorkflowSubmissionResponseBody.class);
        reverify(actionRunId, approver).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        var stillScanning = reverify(actionRunId, executor).assertOk().as(WorkflowSubmissionResponseBody.class);
        files.scanned(own, FileScanStatus.AVAILABLE);
        var passed = reverify(actionRunId, executor).assertOk().as(WorkflowSubmissionResponseBody.class);
        var again = reverify(actionRunId, executor).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        WorkflowInstanceDetailResponseBody detail = detail(instanceId, admin);
        var verification = detail.getVerifications().stream()
            .filter(row -> row.getVerificationId().equals(passed.getVerificationId())).findFirst().orElseThrow();
        assertAll(() -> assertTrue(foreign.json().path("detail").asString().contains("不是提交人本人上传")),
            () -> assertEquals(0, afterForeign),
            () -> assertEquals("UNVERIFIED", pending.getStatus()),
            () -> assertTrue(pending.getFailureReason().contains("文件扫描中"), pending.getFailureReason()),
            () -> assertEquals("UNVERIFIED", stillScanning.getStatus()),
            () -> assertEquals("VERIFIED", passed.getStatus()),
            () -> assertNull(passed.getFailureReason()),
            () -> assertEquals(List.of(FileAddress.of(own).value()), strings(verification.getEvidence().path("files"))),
            () -> assertEquals(executor.actor(), verification.getVerifiedBy()),
            () -> assertTrue(again.json().path("detail").asString().contains("不接受提交")),
            () -> assertEquals("APPROVING", nodeStatus(instanceId, "upload")),
            () -> assertEquals(3, workflowData.verificationCount(instanceId)),
            () -> assertEquals(Long.toString(own), detail.getFiles().getFirst().getFileId()));
    }

    /**
     * 表单动作的文件字段：类型不符记为未通过，可用合法文件重新提交并通过；文件字段的数量规则同样生效。
     * 没有文件的动作不能重新验证。
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

        var wrongType = submit(contract, executor, contract(png)).assertOk().as(WorkflowSubmissionResponseBody.class);
        var empty = submit(contract, executor, contractValues(NODES.arrayNode())).assertOk()
            .as(WorkflowSubmissionResponseBody.class);
        var noteResult = submit(note, executor, WorkflowGraphJson.empty()).assertOk()
            .as(WorkflowSubmissionResponseBody.class);
        var noFiles = reverify(note, executor).assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        var valid = submit(contract, executor, contract(pdf)).assertOk().as(WorkflowSubmissionResponseBody.class);

        assertAll(() -> assertEquals("UNVERIFIED", wrongType.getStatus()),
            () -> assertTrue(wrongType.getFailureReason().contains("image/png"), wrongType.getFailureReason()),
            () -> assertEquals("UNVERIFIED", empty.getStatus()),
            () -> assertTrue(empty.getFailureReason().contains("必填"), empty.getFailureReason()),
            () -> assertEquals("UNVERIFIED", noteResult.getStatus()),
            () -> assertTrue(noFiles.json().path("detail").asString().contains("只有含文件的动作")),
            () -> assertEquals("VERIFIED", valid.getStatus()),
            () -> assertEquals("APPROVING", nodeStatus(instanceId, "fill")));
    }

    /** 以给定文件发起并要求被拒，返回拒绝说明。 */
    private String rejected(long definitionId, long... fileIds) {
        PlatformResponseChecker response = start(definitionId, admin, contract(fileIds), null, null);
        return response.assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED).json().path("detail").asString();
    }

    /** 重新验证。 */
    private PlatformResponseChecker reverify(String actionRunId, Operator operator) {
        return post(RUNTIME + "/action-runs/" + actionRunId + "/verification", operator, null);
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

    /** 含一个必填 FILE 字段 contractFiles 的表单：1—2 个 PDF。 */
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

    /** 上传文件动作配置：1—2 个 PDF。 */
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
