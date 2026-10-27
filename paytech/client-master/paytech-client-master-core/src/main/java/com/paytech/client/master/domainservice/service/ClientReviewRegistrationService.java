package com.paytech.client.master.domainservice.service;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.domain.onboarding.ClientReviewSourceView;

/** 审核登记补偿入口使用的本地读取与结果保存；不登记补偿任务，不访问外部服务。 */
public interface ClientReviewRegistrationService {
    /**
     * 读取审核登记所需的提交事实
     *
     * @return 当前提交编号对应的事实；该编号已不是任何申请的当前提交时返回 null
     */
    ClientReviewSourceView reviewSource(String submissionId);

    /**
     * 审核任务登记成功后，同一提交的 SUBMITTED → UNDER_REVIEW
     *
     * @return 是否更新；已不是同一提交的 SUBMITTED 时返回 false
     */
    boolean startReview(ClientApplicationSubmission application, long caseId);

    /**
     * 撤回补偿任务读取申请当前的审核坐标
     *
     * @param applicationId 申请 ID
     * @return 申请提交坐标；不存在为 null
     */
    ClientApplicationSubmission withdrawalSource(long applicationId);
}
