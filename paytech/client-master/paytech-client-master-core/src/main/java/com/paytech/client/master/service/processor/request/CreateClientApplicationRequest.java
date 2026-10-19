package com.paytech.client.master.service.processor.request;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartApplicationRequestBody;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 开始新申请的内部请求；申请类型的闭合目录校验以 clientType 字段错误返回，保留在 Processor 内。 */
@Getter
@AllArgsConstructor
public class CreateClientApplicationRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
    /** 开始申请请求体；可为空，按申请类型无效拒绝。 */
    private final StartApplicationRequestBody body;
}
