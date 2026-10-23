package com.paytech.platform.support.service.processor.workflowattachment;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.service.processor.files.StartFileUploadProcessor;
import com.paytech.platform.support.service.processor.files.request.StartFileUploadRequest;
import com.paytech.platform.support.service.processor.files.response.StartFileUploadResponse;
import com.paytech.platform.support.service.processor.workflowattachment.request.StartWorkflowAttachmentUploadRequest;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;

import lombok.AllArgsConstructor;

/**
 * 开始上传工作流附件（技术方案第 7 节 #26）：重检 WORKFLOW_TASK_HANDLE → 以 platform-support / WORKFLOW_ATTACHMENT、
 * 当前操作人为上传人调用文件入口 StartFileUploadProcessor
 * <li>本入口不写工作流表、不开事务；上传登记与事务外签发由文件入口负责。文件能力未开启返回 503。</li>
 * <li>文件名、类型与大小的通用限制由文件入口与写入路由的扫描策略执行；字段或动作的数量与类型规则在提交时校验。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class StartWorkflowAttachmentUploadProcessor
    extends AbstractProcessor<StartWorkflowAttachmentUploadRequest, StartFileUploadResponse> {

    private final OperationsManagementPolicyService policy;

    private final ObjectProvider<StartFileUploadProcessor> uploads;

    @Override
    protected StartFileUploadResponse processRequest(StartWorkflowAttachmentUploadRequest request) {
        // 权限重检
        long actor = policy.requirePermission(request.getActor(), request.getSession(), WorkflowPermissions.TASK_HANDLE)
            .account().id();

        // 调用文件入口开始上传
        StartFileUploadProcessor processor = uploads.getIfAvailable();
        if (processor == null) {
            throw new ServiceUnavailableException();
        }
        return processor.process(new StartFileUploadRequest(AttachmentRules.SOURCE_SYSTEM, AttachmentRules.REASON_CODE,
            request.getFilename(), request.getMimeType(), request.getSizeBytes(), actor, FileAccessLevel.PRIVATE));
    }
}
