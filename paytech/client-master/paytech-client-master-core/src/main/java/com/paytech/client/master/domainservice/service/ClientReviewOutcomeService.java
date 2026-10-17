package com.paytech.client.master.domainservice.service;

import com.paytech.client.master.domain.onboarding.ClientReviewOutcomeContext;

/** Risk 审核结果在 CCT 的本地落地；不访问外部服务。 */
public interface ClientReviewOutcomeService {
    /**
     * 一个本地事务内结束申请审核；通过时同时激活 Client、追加生命周期历史并登记 Platform 工作区投影任务
     * <li>任一条件更新未命中抛出并发冲突并整体回滚。</li>
     */
    void conclude(ClientReviewOutcomeContext context);
}
