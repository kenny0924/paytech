package com.paytech.platform.support.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** CCT 关联文件前的内部核验请求；期望值均由 CCT 自行核验后传入，Platform 不回调 CCT。 */
@Getter
@AllArgsConstructor
public class VerifyOnboardingFileRequest {

    /** 原上传账号 ID。 */
    @Positive(message = "账号 ID 必须为正整数")
    private final long accountId;

    /** 文件 ID。 */
    @Positive(message = "文件 ID 必须为正整数")
    private final long fileId;

    /** 期望的原申请 ID。 */
    @Positive(message = "申请 ID 必须为正整数")
    private final long applicationId;

    /** 期望的材料主体 Party ID。 */
    @Positive(message = "主体 ID 必须为正整数")
    private final long subjectPartyId;

    /** 期望的申请所属 Client ID，用于核对原 Tenant。 */
    @Positive(message = "Client ID 必须为正整数")
    private final long clientId;
}
