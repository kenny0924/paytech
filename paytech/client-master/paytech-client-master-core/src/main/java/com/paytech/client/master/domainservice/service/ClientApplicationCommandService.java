package com.paytech.client.master.domainservice.service;

import java.time.Instant;

import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;

/** 申请级本地写入：声明追加与撤回；事务由本服务以短事务完成，不访问外部服务。 */
public interface ClientApplicationCommandService {
    /**
     * 追加一条声明；申请须仍可编辑（条件在 Processor 锁内已核对，写入时由申请行状态再次保护）
     *
     * @param declaration 已准备 ID 与时间的声明
     */
    void declare(ApplicationDeclaration declaration);

    /**
     * 撤回申请；已立案（有审核任务）时同一事务登记 CLIENT_REVIEW_WITHDRAWAL 补偿任务
     *
     * @param application DRAFT 或 RETURNED 的申请，成功后同步为 WITHDRAWN
     * @param now 当前时间
     * @return 是否本次撤回；并发下已被改变时为 false
     */
    boolean withdraw(ClientApplicationSubmission application, Instant now);
}
