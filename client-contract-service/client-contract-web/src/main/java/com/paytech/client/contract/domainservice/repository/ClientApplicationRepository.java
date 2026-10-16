package com.paytech.client.contract.domainservice.repository;

import com.paytech.client.contract.domain.enums.ClientApplicationStatus;
import java.time.Instant;
import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.contract.core.dal.dataobject.ClientWorkspaceSummary;

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

    /** 当前任务为指定任务的申请（任意状态）；不存在返回 null。 */
    ClientApplicationSubmission findByCase(long caseId);

    /**
     * 同一提交、同一任务的 UNDER_REVIEW 条件迁移为审核结果状态，成功后同步原对象
     * <li>RETURNED 保留未完成名额；APPROVED/REJECTED 释放名额。状态已变化时抛出并发冲突。</li>
     */
    void concludeReview(ClientApplicationSubmission application, ClientApplicationStatus status, Long decisionId, Instant now);

    /** 本账号作为申请人且存在 APPROVED 申请的 Client 摘要；其余 ID 不返回。 */
    List<ClientWorkspaceSummary> findApprovedSummaries(long accountId, List<Long> clientIds);
}
