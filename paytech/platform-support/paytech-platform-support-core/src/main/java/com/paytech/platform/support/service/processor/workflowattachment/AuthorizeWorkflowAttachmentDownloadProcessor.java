package com.paytech.platform.support.service.processor.workflowattachment;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.service.processor.files.AuthorizeFileDownloadProcessor;
import com.paytech.platform.support.service.processor.files.request.AuthorizeFileDownloadRequest;
import com.paytech.platform.support.service.processor.files.response.AuthorizeFileDownloadResponse;
import com.paytech.platform.support.service.processor.workflowattachment.request.AuthorizeWorkflowAttachmentDownloadRequest;
import com.paytech.platform.support.workflow.service.processor.WorkflowQueryProcessor;

import lombok.AllArgsConstructor;

/**
 * 签发实例附件的下载授权（技术方案第 7 节 #28）：工作流读侧检查（参与人或 WORKFLOW_INSTANCE_READ，文件出现在本实例的
 * 启动值或动作输出中）→ 调用文件入口 AuthorizeFileDownloadProcessor 签发 60 秒地址
 * <li>两步都不开事务、不写数据；读侧检查完成后才在事务外签发。实例不可见或文件不属于本实例返回 404，文件未扫描通过返回 409，
 * 文件能力未开启返回 503。地址只回传给本次调用，不落库、不进日志。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class AuthorizeWorkflowAttachmentDownloadProcessor
    extends AbstractProcessor<AuthorizeWorkflowAttachmentDownloadRequest, AuthorizeFileDownloadResponse> {

    private final WorkflowQueryProcessor queries;

    private final ObjectProvider<AuthorizeFileDownloadProcessor> downloads;

    @Override
    protected AuthorizeFileDownloadResponse processRequest(AuthorizeWorkflowAttachmentDownloadRequest request) {
        // 工作流读侧检查
        queries.requireAttachmentVisible(request.getActor(), request.getSession(), request.getInstanceId(),
            request.getFileId());

        // 事务外签发下载授权
        AuthorizeFileDownloadProcessor processor = downloads.getIfAvailable();
        if (processor == null) {
            throw new ServiceUnavailableException();
        }
        return processor.process(new AuthorizeFileDownloadRequest(request.getFileId()));
    }
}
