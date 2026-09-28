package com.paytech.risk.decision.domainservice.repository;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;

/** 发布审计事件只读或追加，不修改历史事实。 */
public interface ReleaseAuditEventRepository {
    /** 读取一个发布的完整审计链。 */
    List<ReleaseAuditEvent> findByRelease(long releaseId);
    /** 批量读取分页所需审计。 */
    List<ReleaseAuditEvent> findByReleases(List<Long> releaseIds);
    /** 追加状态事件。 */
    void insert(ReleaseAuditEvent event);
    /** 同批追加完整静态检查。 */
    void insertChecks(List<ReleaseAuditEvent> events);
}
