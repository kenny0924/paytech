package com.paytech.platform.support.auditsecurity.domainservice.service;

import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Event;

/** 在独立事务追加一个冻结审计事件；失败交由调用方分类，不自动重试。 */
public interface OperationsAuditWriter {
    void write(Event event);
}
