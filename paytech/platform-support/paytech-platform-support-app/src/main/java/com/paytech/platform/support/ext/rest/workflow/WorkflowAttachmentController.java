package com.paytech.platform.support.ext.rest.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.enums.FileDisposition;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadAuthorization;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.workflow.WorkflowAttachmentApi;
import com.paytech.platform.support.api.workflow.request.StartWorkflowAttachmentUploadRequestBody;
import com.paytech.platform.support.service.processor.files.response.AuthorizeFileDownloadResponse;
import com.paytech.platform.support.service.processor.files.response.CompleteFileUploadResponse;
import com.paytech.platform.support.service.processor.files.response.StartFileUploadResponse;
import com.paytech.platform.support.service.processor.workflowattachment.AuthorizeWorkflowAttachmentDownloadProcessor;
import com.paytech.platform.support.service.processor.workflowattachment.CompleteWorkflowAttachmentUploadProcessor;
import com.paytech.platform.support.service.processor.workflowattachment.StartWorkflowAttachmentUploadProcessor;
import com.paytech.platform.support.service.processor.workflowattachment.request.AuthorizeWorkflowAttachmentDownloadRequest;
import com.paytech.platform.support.service.processor.workflowattachment.request.CompleteWorkflowAttachmentUploadRequest;
import com.paytech.platform.support.service.processor.workflowattachment.request.StartWorkflowAttachmentUploadRequest;

import lombok.AllArgsConstructor;

/**
 * 工作流附件的协议适配：十进制字符串转换与委托 Processor；临时上传与下载地址只回传、不记日志（/workflow 为元数据日志前缀）。
 * <li>附件上传与下载授权不写工作流表，按文件内部接口的约定不登记 Operations 审计。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowAttachmentController implements WorkflowAttachmentApi {

    /** 浏览器直传使用的 HTTP 方法。 */
    private static final String UPLOAD_METHOD = "PUT";

    private final WorkflowConverter converter;
    private final StartWorkflowAttachmentUploadProcessor startProcessor;
    private final CompleteWorkflowAttachmentUploadProcessor completeProcessor;
    private final AuthorizeWorkflowAttachmentDownloadProcessor downloadProcessor;

    /** 开始上传，返回 201。 */
    @Override
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponseBody startUpload(String actor, String session,
        StartWorkflowAttachmentUploadRequestBody request) {
        StartFileUploadResponse response = startProcessor.process(new StartWorkflowAttachmentUploadRequest(actor,
            session, request.getFilename(), request.getMimeType(), size(request.getSizeBytes())));
        var grant = response.getGrant();
        return new FileUploadResponseBody(Long.toString(response.getUploadId()),
            new FileUploadAuthorization(UPLOAD_METHOD, grant.url(), grant.headers(), grant.expiresAt()),
            response.getExpiresAt());
    }

    /** 完成上传；重复完成返回原回执。 */
    @Override
    public FileUploadCompletionResponseBody completeUpload(String actor, String session, String uploadId) {
        CompleteFileUploadResponse response = completeProcessor.process(new CompleteWorkflowAttachmentUploadRequest(
            actor, session, converter.id(uploadId, "uploadId")));
        return new FileUploadCompletionResponseBody(Long.toString(response.getUploadId()),
            Long.toString(response.getFileId()), FileAddress.of(response.getFileId()).value(), response.getCompletedAt());
    }

    /** 签发实例附件的短时下载地址；响应带实际生效的下载方式。 */
    @Override
    public FileDownloadResponseBody authorizeDownload(String actor, String session, String instanceId,
        String fileId, String disposition) {
        AuthorizeFileDownloadResponse response = downloadProcessor.process(
            new AuthorizeWorkflowAttachmentDownloadRequest(actor, session, converter.id(instanceId, "instanceId"),
                converter.id(fileId, "fileId"), disposition(disposition)));
        return new FileDownloadResponseBody(response.getUrl(), response.getExpiresAt(), response.getDisposition().getCode());
    }

    /** 下载方式：缺省或为空按附件，未知 Code 为参数错误。 */
    static FileDisposition disposition(String value) {
        if (value == null || value.isEmpty()) {
            return FileDisposition.ATTACHMENT;
        }
        try {
            return FileDisposition.fromCode(value);
        } catch (IllegalArgumentException unknown) {
            throw new InvalidParameterException("disposition 只能为 INLINE 或 ATTACHMENT", unknown);
        }
    }

    /** 声明字节数：Body 已校验为正整数十进制字符串，超出 long 为参数错误。 */
    static long size(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new InvalidParameterException("sizeBytes 超出范围", exception);
        }
    }
}
