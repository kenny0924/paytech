package com.paytech.client.master.service.processor.request;

import com.paytech.client.contract.api.onboarding.ClientPeopleModels.CreatePersonRequestBody;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新增关联人员的内部请求。 */
@Getter
@AllArgsConstructor
public class CreateClientPersonRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
    /** 申请 ID，也是申请资源锁的 Key。 */
    @Positive
    private final long applicationId;
    /** 人员资料与关系；为空时按草稿无效拒绝。 */
    private final CreatePersonRequestBody body;
}
