package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import java.time.Instant;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientWorkspaceSummary;

/** t_client_application 的提交与审核登记持久化。 */
public interface ClientApplicationRepository {
    /** 按申请和发起账号读取提交坐标，不存在返回 null。 */
    ClientApplicationSubmission findByApplication(long applicationId, long accountId);

    /** 按提交编号读取提交坐标，不存在返回 null。 */
    ClientApplicationSubmission findBySubmission(String submissionId);

    /**
     * DRAFT/RETURNED 条件迁移为 SUBMITTED 并写入新提交编号，成功后同步原对象。
     * <li>状态已变化时抛出并发冲突，由外层事务回滚。</li>
     */
    void submit(ClientApplicationSubmission application, String submissionId, Instant now);

    /**
     * 同一提交的 SUBMITTED 条件迁移为 UNDER_REVIEW，成功后同步原对象。
     *
     * @return 是否更新；状态已不是同一提交的 SUBMITTED 时返回 false
     */
    boolean startReview(ClientApplicationSubmission application, long caseId, Instant now);

    /** 按申请读取，不限发起账号；不存在返回 null。 */
    ClientApplicationSubmission findById(long applicationId);

    /** Client 的全部申请，新在前。 */
    List<ClientApplicationSubmission> findByClient(long clientId);

    /** 当前任务为指定任务的申请（任意状态）；不存在返回 null。 */
    ClientApplicationSubmission findByCase(long caseId);

    /**
     * 同一提交、同一任务的 UNDER_REVIEW 条件迁移为审核结果状态，成功后同步原对象
     * <li>RETURNED 保留未完成名额；APPROVED/REJECTED 释放名额。状态已变化时抛出并发冲突。</li>
     */
    /**
     * 条件结束审核：RETURNED/APPROVED/REJECTED 要求当前为 UNDER_REVIEW，WITHDRAWN（退回超期）要求当前为 RETURNED
     *
     * @param dueTime 退回补件截止时间；其他结果为空
     */
    void concludeReview(ClientApplicationSubmission application, ClientApplicationStatus status, Long decisionId, Instant dueTime,
        Instant now);

    /**
     * Client 撤回：DRAFT/RETURNED → WITHDRAWN 并释放名额
     *
     * @return 是否本次撤回；状态已变化时为 false
     */
    boolean withdraw(ClientApplicationSubmission application, Instant now);

    /** 存在 APPROVED 申请的 Client 摘要；其余 ID 不返回，不判断成员关系。 */
    List<ClientWorkspaceSummary> findApprovedSummaries(List<Long> clientIds);
}
