package com.paytech.platform.support.service.processor.workflowattachment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 开始上传工作流附件的内部请求；来源系统、原因与上传人由入口确定。 */
@Getter
@AllArgsConstructor
public class StartWorkflowAttachmentUploadRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 原始文件名。 */
    @NotBlank
    private final String filename;

    /** 声明的规范 MIME。 */
    @NotBlank
    private final String mimeType;

    /** 声明字节数。 */
    @Positive
    private final long sizeBytes;

    /** 不记录可能包含个人信息的文件名。 */
    @Override
    public String toString() {
        return "StartWorkflowAttachmentUploadRequest[REDACTED]";
    }
}
