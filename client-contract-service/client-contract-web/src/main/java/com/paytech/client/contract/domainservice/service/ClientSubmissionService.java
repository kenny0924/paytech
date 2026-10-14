package com.paytech.client.contract.domainservice.service;

import com.paytech.client.contract.domain.onboarding.SubmitClientApplicationContext;

/** C14 正式提交的本地保存；不访问外部服务。 */
public interface ClientSubmissionService {
    /**
     * 原子保存一次提交
     * <li>申请 DRAFT/RETURNED → SUBMITTED、绑定永久身份键、登记审核登记任务在同一本地事务。</li>
     * <li>身份唯一索引冲突以 DuplicateKeyException 抛出，整体回滚。</li>
     */
    void submit(SubmitClientApplicationContext context);
}
