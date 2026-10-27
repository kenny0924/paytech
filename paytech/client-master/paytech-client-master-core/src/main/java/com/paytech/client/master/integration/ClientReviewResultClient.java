package com.paytech.client.master.integration;

import java.time.Instant;
import java.util.List;

/** compliance CP-4 审核结果出站端口：只读取退回的需求级错误；只能在数据库事务外调用。 */
public interface ClientReviewResultClient {
    /**
     * 审核员退回的需求级错误
     *
     * @param applicationId 申请 ID
     * @param caseId 申请当前绑定的审核任务；结果不属于该任务时视为没有退回错误
     * @return 当前任务处于退回时的错误；从未登记、已不是退回或任务不符时为空
     *         <li>compliance 不可用时失败关闭为 ONBOARDING_DEPENDENCY_UNAVAILABLE（503）。</li>
     */
    List<ReturnedError> returnedErrors(long applicationId, long caseId);

    /**
     * 一项退回错误
     *
     * @param requirementKey 需求键，与本服务需求清单同一命名
     * @param subjectPartyId 所属主体 Party ID；不针对具体主体（申请级）时为空
     * @param code 错误码
     * @param remediation 补救动作
     * @param dueTime 补件截止时间，可空
     */
    record ReturnedError(String requirementKey, Long subjectPartyId, String code, String remediation, Instant dueTime) {
    }
}
