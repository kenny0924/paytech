package com.paytech.platform.support.auto.ext.http.workflow;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.workflow.WorkflowAttachmentApi;
import com.paytech.platform.support.api.workflow.request.StartWorkflowAttachmentUploadRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowSubmissionResponseBody;
import com.paytech.platform.support.service.handler.FileScanHandler;
import com.paytech.platform.support.test.base.WorkflowFileTestBase;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.WorkflowFileMocker;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 工作流附件经真实存储的上传与下载（本机 MinIO）：上传登记 platform-support / WORKFLOW_ATTACHMENT 与当前操作人，
 * 只有上传人能完成；提交后参与人可签发下载并取回原字节，非参与人与不属于本实例的文件按不存在返回 404；
 * 下载方式缺省为 ATTACHMENT，INLINE 只对 PDF / PNG / JPEG 生效，否则降为 ATTACHMENT，非法值返回 400。
 *
 * @see WorkflowAttachmentApi#startUpload(String, String, StartWorkflowAttachmentUploadRequestBody)
 * @see WorkflowAttachmentApi#completeUpload(String, String, String)
 * @see WorkflowAttachmentApi#authorizeDownload(String, String, String, String, String)
 */
class WorkflowAttachmentApiTest extends WorkflowFileTestBase {

    private static final String UPLOADS = RUNTIME + "/attachments/uploads";

    @Autowired
    private Clock clock;

    @Autowired
    private WorkflowFileMocker files;

    private Operator executor;
    private Operator approver;

    /** 本类公共：一名执行人与一名审批人。 */
    @BeforeEach
    void setUpAttachmentApiMocks() {
        executor = person();
        approver = person();
    }

    /**
     * 上传、完成、扫描、提交与下载：他人完成返回 404，重复完成返回原回执；参与人下载取回原字节，非参与人、
     * 不属于本实例的文件与不存在的实例返回 404。
     */
    @Test
    void test_normalFlow() {
        String uploadId = uploadPdf(executor);
        Map<String, Object> row = fileData.uploadRow(Long.parseLong(uploadId));

        complete(uploadId, approver).assertProblem(404, FileErrorCode.FILE_NOT_FOUND);
        var receipt = complete(uploadId, executor).assertOk().as(FileUploadCompletionResponseBody.class);
        var repeated = complete(uploadId, executor).assertOk().as(FileUploadCompletionResponseBody.class);
        awaitAvailable(receipt.getFileId());

        long template = template("FILE_SETUP", "FILE_UPLOAD", uploadConfig());
        JsonNode graph = WorkflowGraphJson.graph().task("upload", "上传合同")
            .action("upload", template, executor.accountId(), true).approval("ANY", approver.accountId()).end("end")
            .chain("start", "upload", "end").build();
        long instanceId = started(published("FILE_FLOW", graph, WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        var submitted = submit(openAction(instanceId, "upload"), executor, output(receipt.getFileId())).assertOk()
            .as(WorkflowSubmissionResponseBody.class);

        var download = download(instanceId, receipt.getFileId(), approver).assertOk().as(FileDownloadResponseBody.class);
        byte[] bytes = storage.browserGet(download.getUrl()).body();
        var inline = download(instanceId, receipt.getFileId(), approver, FileDisposition.INLINE.getCode()).assertOk()
            .as(FileDownloadResponseBody.class);
        var inlineResponse = storage.browserGet(inline.getUrl());
        long role = operationsMocker.customRole("WORKFLOW_HANDLER", WorkflowPermissions.TASK_HANDLE);
        Operator outsider = operationsMocker.operator(OperatorBuilder.administrator().roles(role));
        download(instanceId, receipt.getFileId(), outsider).assertProblem(404, CommonErrorCode.NOT_FOUND);
        long unrelated = files.available(executor.accountId());
        download(instanceId, Long.toString(unrelated), executor).assertProblem(404, CommonErrorCode.NOT_FOUND);
        download(OperatorBuilder.nextId(), receipt.getFileId(), admin).assertProblem(404, CommonErrorCode.NOT_FOUND);

        assertAll(() -> assertEquals(AttachmentRules.SOURCE_SYSTEM, row.get("source_system")),
            () -> assertEquals(AttachmentRules.REASON_CODE, row.get("reason_code")),
            () -> assertEquals(executor.accountId(), ((Number) row.get("uploader_id")).longValue()),
            () -> assertEquals(receipt.getFileId(), repeated.getFileId()),
            () -> assertEquals(ActionRunStatus.COMPLETED.getCode(), submitted.getStatus()),
            () -> assertTrue(download.getExpiresAt().isAfter(clock.instant())),
            () -> assertEquals("https", URI.create(download.getUrl()).getScheme()),
            () -> assertArrayEquals(FileBuilder.PDF, bytes),
            () -> assertEquals(FileDisposition.ATTACHMENT.getCode(), download.getDisposition()),
            () -> assertEquals(FileDisposition.INLINE.getCode(), inline.getDisposition()),
            () -> assertTrue(inlineResponse.headers().firstValue("Content-Disposition").orElse("").startsWith("inline")),
            () -> assertEquals(WorkflowFileMocker.PDF, inlineResponse.headers().firstValue("Content-Type").orElse(null)),
            () -> assertArrayEquals(FileBuilder.PDF, inlineResponse.body()));
    }

    /**
     * 下载方式：PDF 请求 INLINE 生效，缺省与 ATTACHMENT 按附件；不在内联白名单的类型请求 INLINE 静默降为 ATTACHMENT；
     * 未知或小写的下载方式返回 400。
     */
    @Test
    void test_downloadDisposition() {
        long pdf = files.stored(admin.accountId(), WorkflowFileMocker.PDF, storage.bucket());
        long gif = files.stored(admin.accountId(), "image/gif", storage.bucket());
        JsonNode graph = WorkflowGraphJson.graph().task("review", "审核").approval("ANY", approver.accountId())
            .end("end").chain("start", "review", "end").build();
        long instanceId = started(published("FILE_VIEW", graph, materialsForm()), admin, materials(pdf, gif));
        String pdfId = Long.toString(pdf);
        String gifId = Long.toString(gif);

        var inline = download(instanceId, pdfId, admin, FileDisposition.INLINE.getCode()).assertOk()
            .as(FileDownloadResponseBody.class);
        var absent = download(instanceId, pdfId, admin).assertOk().as(FileDownloadResponseBody.class);
        var attachment = download(instanceId, pdfId, admin, FileDisposition.ATTACHMENT.getCode()).assertOk()
            .as(FileDownloadResponseBody.class);
        var degraded = download(instanceId, gifId, admin, FileDisposition.INLINE.getCode()).assertOk()
            .as(FileDownloadResponseBody.class);
        download(instanceId, pdfId, admin, "PREVIEW").assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        download(instanceId, pdfId, admin, "inline").assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        assertAll(() -> assertEquals(FileDisposition.INLINE.getCode(), inline.getDisposition()),
            () -> assertEquals(FileDisposition.ATTACHMENT.getCode(), absent.getDisposition()),
            () -> assertEquals(FileDisposition.ATTACHMENT.getCode(), attachment.getDisposition()),
            () -> assertEquals(FileDisposition.ATTACHMENT.getCode(), degraded.getDisposition()),
            () -> assertTrue(URI.create(inline.getUrl()).getQuery().contains("response-content-disposition=inline")),
            () -> assertTrue(URI.create(degraded.getUrl()).getQuery().contains("response-content-disposition=attachment")));
    }

    /** 参数非法返回 422 字段错误，类型不在扫描策略内返回 422 策略拒绝，均不登记上传；不存在的上传返回 404。 */
    @Test
    void test_invalidParameter() {
        post(UPLOADS, executor, new StartWorkflowAttachmentUploadRequestBody("a.pdf", "PDF", "10"))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("mimeType");
        post(UPLOADS, executor, new StartWorkflowAttachmentUploadRequestBody("a.pdf", WorkflowFileMocker.PDF, "0"))
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("sizeBytes");
        post(UPLOADS, executor, new StartWorkflowAttachmentUploadRequestBody("a.txt", "text/plain", "10"))
            .assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);
        complete(Long.toString(OperatorBuilder.nextId()), executor).assertProblem(404,
            FileErrorCode.FILE_NOT_FOUND);

        assertTrue(fileData.uploadsOf(executor.accountId()).isEmpty());
    }

    /**
     * 声明 image/png 而实际上传 PDF：完成上传返回 422 FILE_POLICY_REJECTED 与中文原因，不登记文件、上传仍未完成
     * （e2e 中 ops-bff 曾因未登记该错误码而报 502）。
     */
    @Test
    void test_declaredTypeMismatch() {
        String uploadId = uploadPdf(executor, "image/png");

        var rejected = complete(uploadId, executor).assertProblem(422, FileErrorCode.FILE_POLICY_REJECTED);

        assertAll(() -> assertEquals("文件类型或大小不符合要求", rejected.json().path("detail").asString()),
            () -> assertTrue(fileData.filesOfUpload(Long.parseLong(uploadId)).isEmpty()),
            () -> assertEquals("CREATED", fileData.uploadRow(Long.parseLong(uploadId)).get("status")));
    }

    /** 以操作人身份开始上传并按授权直传 PDF，返回上传 ID。 */
    private String uploadPdf(Operator operator) {
        return uploadPdf(operator, WorkflowFileMocker.PDF);
    }

    /** 以声明的类型开始上传并按授权直传 PDF 字节，返回上传 ID。 */
    private String uploadPdf(Operator operator, String declaredMime) {
        FileUploadResponseBody upload = post(UPLOADS, operator, new StartWorkflowAttachmentUploadRequestBody(
            "contract.pdf", declaredMime, Integer.toString(FileBuilder.PDF.length)))
            .assertStatus(201).as(FileUploadResponseBody.class);
        storage.track((String) fileData.uploadRow(Long.parseLong(upload.getUploadId())).get("storage_object_ref"));
        var authorization = upload.getAuthorization();
        assertEquals("PUT", authorization.getMethod());
        assertEquals(200, storage.browserPut(authorization.getUrl(), authorization.getHeaders(),
            FileBuilder.PDF).statusCode());
        return upload.getUploadId();
    }

    /** 完成上传。 */
    private PlatformResponseChecker complete(String uploadId, Operator operator) {
        return post(UPLOADS + "/" + uploadId + "/completion", operator, null);
    }

    /** 签发下载，不带下载方式。 */
    private PlatformResponseChecker download(long instanceId, String fileId, Operator operator) {
        return post(RUNTIME + "/instances/" + instanceId + "/files/" + fileId + "/download", operator, null);
    }

    /** 签发下载，带下载方式 Query 参数。 */
    private PlatformResponseChecker download(long instanceId, String fileId, Operator operator, String disposition) {
        return post(RUNTIME + "/instances/" + instanceId + "/files/" + fileId + "/download?disposition=" + disposition,
            operator, null);
    }

    /** 有界等待扫描任务把文件推进为 AVAILABLE。 */
    private void awaitAvailable(String fileId) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(
            () -> FileScanStatus.AVAILABLE.getCode().equals(fileData.file(Long.parseLong(fileId)).get("scan_status")));
        await().atMost(Duration.ofSeconds(15)).until(() -> fileData.activeTasks(FileScanHandler.EVENT_TYPE) == 0);
    }

    /** 上传文件动作配置：1—2 个 PDF。 */
    private static JsonNode uploadConfig() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.put("minFiles", 1);
        config.put("maxFiles", 2);
        config.putArray("mimeTypes").add(WorkflowFileMocker.PDF);
        config.put("instruction", "上传签署后的合同");
        return config;
    }

    /** 含一个必填 FILE 字段 materials 的表单：1—2 个文件，不限类型。 */
    private static JsonNode materialsForm() {
        ObjectNode form = JsonNodeFactory.instance.objectNode();
        ObjectNode field = form.putArray("fields").addObject();
        field.put("key", "materials");
        field.put("label", "材料");
        field.put("type", "FILE");
        field.put("required", true);
        ObjectNode rules = field.putObject("rules");
        rules.put("minFiles", 1);
        rules.put("maxFiles", 2);
        return form;
    }

    /** {"materials": [文件地址...]}。 */
    private static JsonNode materials(long... fileIds) {
        ObjectNode values = JsonNodeFactory.instance.objectNode();
        var array = values.putArray("materials");
        for (long fileId : fileIds) {
            array.add(FileAddress.of(fileId).value());
        }
        return values;
    }

    /** {"files": [文件地址]}。 */
    private static JsonNode output(String fileId) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.putArray("files").add(FileAddress.of(Long.parseLong(fileId)).value());
        return output;
    }
}
