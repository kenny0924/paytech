package com.paytech.client.master.service.processor.request;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 保存草稿的内部请求；字段级规范化由 Processor 在会话核验后执行。 */
@Getter
@AllArgsConstructor
public class SaveClientDraftRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
    /** 申请 ID，也是申请资源锁的 Key。 */
    @Positive
    private final long applicationId;
    /** 草稿补丁，按 dirtyFields 逐字段规范化。 */
    private final DraftPatchRequestBody patch;
}
