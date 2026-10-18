package com.paytech.client.master.domainservice.service;

import com.paytech.client.master.domain.onboarding.ClientClassificationContext;

/** I02 交付补偿入口的本地读取；不依赖补偿登记器，避免与 Handler 注册表形成循环。 */
public interface ClientClassificationSourceService {
    /**
     * 本任务仍在审核且分类已确认时返回交付上下文
     *
     * @param caseId Risk 主审核任务 ID
     * @return 交付上下文；申请已不在本任务审核中时返回 null
     *         <li>已登记交付却未确认属于数据不一致，抛出系统异常。</li>
     */
    ClientClassificationContext deliverySource(long caseId);
}
