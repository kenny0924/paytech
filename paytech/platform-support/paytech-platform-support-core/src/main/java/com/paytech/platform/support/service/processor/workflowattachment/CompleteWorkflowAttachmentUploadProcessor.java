package com.paytech.platform.support.service.processor.workflowattachment;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.service.processor.files.CompleteFileUploadProcessor;
import com.paytech.platform.support.service.processor.files.request.CompleteFileUploadRequest;
import com.paytech.platform.support.service.processor.files.response.CompleteFileUploadResponse;
import com.paytech.platform.support.service.processor.workflowattachment.request.CompleteWorkflowAttachmentUploadRequest;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;

import lombok.AllArgsConstructor;

/**
 * 完成工作流附件上传（技术方案第 7 节 #27）：重检 WORKFLOW_TASK_HANDLE → 读取原上传，只有本人以工作流原因开始的上传可以完成
 * → 调用文件入口 CompleteFileUploadProcessor
 * <li>来源、原因或上传人不符按不存在返回 404，不暴露其他上传。上传读取不加锁；完成的资源锁、存储核对与登记事务由文件入口负责，
 * 本入口不开事务。重复完成返回原回执。文件能力未开启返回 503。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CompleteWorkflowAttachmentUploadProcessor
    extends AbstractProcessor<CompleteWorkflowAttachmentUploadRequest, CompleteFileUploadResponse> {

    private final FileLifecycleService files;

    private final OperationsManagementPolicyService policy;

    private final ObjectProvider<CompleteFileUploadProcessor> completions;

    @Override
    protected CompleteFileUploadResponse processRequest(CompleteWorkflowAttachmentUploadRequest request) {
        // 权限重检
        long actor = policy.requirePermission(request.getActor(), request.getSession(), WorkflowPermissions.TASK_HANDLE)
            .account().id();
        CompleteFileUploadProcessor processor = completions.getIfAvailable();
        if (processor == null) {
            throw new ServiceUnavailableException();
        }

        // 只有本人的工作流附件上传
        requireOwnUpload(files.sourceUpload(request.getUploadId()), actor);

        // 调用文件入口完成上传
        return processor.process(new CompleteFileUploadRequest(AttachmentRules.SOURCE_SYSTEM, request.getUploadId()));
    }

    /** 来源、原因或上传人不符按不存在处理。 */
    void requireOwnUpload(Upload upload, long actor) {
        if (!AttachmentRules.SOURCE_SYSTEM.equals(upload.sourceSystem())
            || !AttachmentRules.REASON_CODE.equals(upload.reasonCode())
            || upload.uploaderId() == null || upload.uploaderId() != actor) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
    }
}
