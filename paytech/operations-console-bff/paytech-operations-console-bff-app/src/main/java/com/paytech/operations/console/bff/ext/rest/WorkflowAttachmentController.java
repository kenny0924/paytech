package com.paytech.operations.console.bff.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.integration.PlatformWorkflowClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.WorkflowModels;
import com.paytech.operations.console.bff.service.model.WorkflowModels.StartWorkflowAttachmentUploadRequestBody;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 工作流附件（开始上传、完成上传、实例附件下载授权）的浏览器入口，简单转发 Platform 同路径
 * <li>路由由 Gateway 绑定 WORKFLOW_TASK_HANDLE；上传人、参与人与文件归属由 Platform 判断。</li>
 * <li>响应含临时上传与下载地址，/workflow 前缀只记元数据日志；无 Body 的命令沿用 BFF 写请求的 JSON Content-Type 与 Origin 要求。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/workflow/v1")
public class WorkflowAttachmentController {

    private final PlatformWorkflowClient client;

    /** 开始上传，返回 201 与临时 PUT 授权。 */
    @PostMapping("/attachments/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponseBody startUpload(OperatorContext context,
        @Valid @RequestBody StartWorkflowAttachmentUploadRequestBody request) {
        return client.startAttachmentUpload(context, request);
    }

    /** 完成上传；只有上传人本人。 */
    @PostMapping("/attachments/uploads/{uploadId}/completion")
    public FileUploadCompletionResponseBody completeUpload(OperatorContext context,
        @PathVariable("uploadId") @Pattern(regexp = WorkflowModels.ID) String uploadId) {
        return client.completeAttachmentUpload(context, uploadId);
    }

    /**
     * 签发实例附件的短时下载地址
     * <li>可选 disposition（INLINE、ATTACHMENT）原样转发，缺省由 Platform 按 ATTACHMENT；响应带实际生效的下载方式，
     * INLINE 不满足 Platform 条件时为 ATTACHMENT。</li>
     */
    @PostMapping("/instances/{instanceId}/files/{fileId}/download")
    public FileDownloadResponseBody authorizeDownload(OperatorContext context,
        @PathVariable("instanceId") @Pattern(regexp = WorkflowModels.ID) String instanceId,
        @PathVariable("fileId") @Pattern(regexp = WorkflowModels.ID) String fileId,
        @RequestParam(name = "disposition", required = false) @Pattern(regexp = "INLINE|ATTACHMENT") String disposition) {
        return client.authorizeAttachmentDownload(context, instanceId, fileId, disposition);
    }
}
