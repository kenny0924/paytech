package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** CM-8 撤回申请。 */
@Getter
@AllArgsConstructor
public class WithdrawClientApplicationRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话。 */
    @Positive
    private final long sessionId;
    /** 申请 ID，也是申请资源锁的 Key。 */
    @Positive
    private final long applicationId;
}
