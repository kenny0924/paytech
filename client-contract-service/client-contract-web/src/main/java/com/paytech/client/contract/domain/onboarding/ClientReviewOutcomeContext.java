package com.paytech.client.contract.domain.onboarding;

import java.time.Instant;

import com.paytech.client.contract.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.contract.core.dal.dataobject.ClientProjectionHistory;
import com.paytech.client.contract.domain.enums.ClientApplicationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次 Risk 审核结果在 CCT 的落地：申请结论；通过时另含 Client 激活的生命周期历史。 */
@Getter
@AllArgsConstructor
public class ClientReviewOutcomeContext {
    /** 审核中且提交、任务均匹配的申请。 */
    private final ClientApplicationSubmission application;
    /** 目标状态：RETURNED、APPROVED 或 REJECTED。 */
    private final ClientApplicationStatus status;
    /** Risk 主任务最终决定 ID；退回为空。 */
    private final Long decisionId;
    /** 通过时追加的 ACTIVE 生命周期历史；其他结果为空。 */
    private final ClientProjectionHistory activation;
    /** activation 的不可变快照 JSON；其他结果为空。 */
    private final String activationSnapshot;
    /** 服务端时间。 */
    private final Instant now;

    /** 是否通过并激活 Client。 */
    public boolean activates() {
        return activation != null;
    }
}
