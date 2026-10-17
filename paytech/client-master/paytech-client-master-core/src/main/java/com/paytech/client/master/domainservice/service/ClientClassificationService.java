package com.paytech.client.master.domainservice.service;

import com.paytech.client.master.domain.onboarding.ClientClassificationContext;

/** 审核任务分类的本地保存与交付读取；不访问外部服务。 */
public interface ClientClassificationService {
    /**
     * O03：把经营资料分类绑定到本任务并整体替换分类行
     * <li>本任务已确认或并发确认时抛出并发冲突并整体回滚。</li>
     */
    void replace(ClientClassificationContext context);

    /**
     * O04：写入确认人与时间，并在同一事务登记交付 Risk 的补偿任务
     * <li>已确认或任务已变化时抛出并发冲突并整体回滚。</li>
     */
    void confirm(ClientClassificationContext context);
}
