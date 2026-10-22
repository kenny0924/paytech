package com.paytech.platform.support.service.processor.workflowattachment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 签发实例附件下载授权的内部请求。 */
@Getter
@AllArgsConstructor
public class AuthorizeWorkflowAttachmentDownloadRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话。 */
    @NotBlank
    private final String session;

    /** 实例 ID。 */
    @Positive
    private final long instanceId;

    /** 文件 ID。 */
    @Positive
    private final long fileId;
}
