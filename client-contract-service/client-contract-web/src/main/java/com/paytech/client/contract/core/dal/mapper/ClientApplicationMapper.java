package com.paytech.client.contract.core.dal.mapper;

import com.paytech.client.contract.domain.enums.ClientApplicationStatus;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.contract.core.dal.dataobject.ClientWorkspaceSummary;

/** t_client_application 的提交与审核登记 SQL；业务 SQL 位于 XML。 */
public interface ClientApplicationMapper {
    /** 按申请和发起账号读取提交坐标。 */
    ClientApplicationSubmission findByApplication(@Param("applicationId") long applicationId, @Param("accountId") long accountId);

    /** 按提交编号读取提交坐标。 */
    ClientApplicationSubmission findBySubmission(@Param("submissionId") String submissionId);

    /** DRAFT/RETURNED 条件更新为 SUBMITTED，返回影响行数。 */
    int submit(@Param("applicationId") long applicationId, @Param("submissionId") String submissionId, @Param("now") Instant now);

    /** 同一提交的 SUBMITTED 条件更新为 UNDER_REVIEW，返回影响行数。 */
    int startReview(@Param("applicationId") long applicationId, @Param("submissionId") String submissionId,
        @Param("caseId") long caseId, @Param("now") Instant now);

    /** 按申请读取提交坐标，不限发起账号；只供内部审核结果使用。 */
    ClientApplicationSubmission findById(@Param("applicationId") long applicationId);

    /** 当前任务为指定任务的申请（任意状态）。 */
    ClientApplicationSubmission findByCase(@Param("caseId") long caseId);

    /** 同一提交、同一任务的 UNDER_REVIEW 条件结束审核，返回影响行数。 */
    int concludeReview(@Param("row") ClientApplicationSubmission row, @Param("status") ClientApplicationStatus status,
        @Param("decisionId") Long decisionId, @Param("release") boolean release, @Param("now") Instant now);

    /** 存在 APPROVED 申请的 Client 摘要，按 Client ID 升序；成员关系由调用方依据 Platform 事实决定。 */
    List<ClientWorkspaceSummary> findApprovedSummaries(@Param("clientIds") List<Long> clientIds);
}
