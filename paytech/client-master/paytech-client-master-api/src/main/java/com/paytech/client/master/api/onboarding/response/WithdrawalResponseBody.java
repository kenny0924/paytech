package com.paytech.client.master.api.onboarding.response;

import lombok.Getter;
import lombok.Setter;

/** CM-8 撤回结果。 */
@Getter
@Setter
public class WithdrawalResponseBody {
    /** 申请 ID。 */
    private String applicationId;

    /** 撤回后状态，恒为 WITHDRAWN。 */
    private String status;

    /** 状态更新 UTC 时间（ISO-8601，微秒）。 */
    private String withdrawnAt;
}
