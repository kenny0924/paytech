package com.paytech.client.master.domainservice.service;

import com.paytech.client.master.domain.onboarding.ClientSuspensionContext;

/** Client 生命周期的本地原子变更；Platform 工作区投影由既有补偿任务在提交后推送。 */
public interface ClientLifecycleService {
    /**
     * 挂起 Client：条件更新状态、追加生命周期历史并登记 CLIENT_TENANT_PROJECTION，同一事务
     *
     * @param context 已校验为 ACTIVE 的 Client 与下一版历史
     */
    void suspend(ClientSuspensionContext context);
}
