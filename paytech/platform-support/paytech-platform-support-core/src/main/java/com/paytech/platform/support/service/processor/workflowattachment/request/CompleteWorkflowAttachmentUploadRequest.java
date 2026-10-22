package com.paytech.platform.support.service.processor.workflowattachment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成工作流附件上传的内部请求。 */
@Getter
@AllArgsConstructor
public class CompleteWorkflowAttachmentUploadRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 上传 ID。 */
    @Positive
    private final long uploadId;
}
