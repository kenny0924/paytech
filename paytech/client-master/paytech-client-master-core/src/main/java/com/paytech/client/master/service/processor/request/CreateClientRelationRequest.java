package com.paytech.client.master.service.processor.request;

import com.paytech.client.contract.api.onboarding.ClientPeopleModels.RelationFields;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 为已有人员新建关系的内部请求。 */
@Getter
@AllArgsConstructor
public class CreateClientRelationRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
    /** 申请 ID，也是申请资源锁的 Key。 */
    @Positive
    private final long applicationId;
    /** 关系对象人员 Party ID，也是人员资源锁的 Key。 */
    @Positive
    private final long partyId;
    /** 关系字段。 */
    private final RelationFields relation;
}
