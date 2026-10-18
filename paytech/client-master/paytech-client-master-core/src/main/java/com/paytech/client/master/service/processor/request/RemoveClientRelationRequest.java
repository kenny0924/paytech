package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 删除关系的内部请求；本申请下已不存在时同样完成。 */
@Getter
@AllArgsConstructor
public class RemoveClientRelationRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
    /** 申请 ID，也是申请资源锁的 Key。 */
    @Positive
    private final long applicationId;
    /** 关系 ID。 */
    @Positive
    private final long relationId;
}
