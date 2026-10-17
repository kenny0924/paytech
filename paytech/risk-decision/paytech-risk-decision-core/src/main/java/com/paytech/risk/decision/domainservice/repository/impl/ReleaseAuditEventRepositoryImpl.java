package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.core.dal.mapper.ReleaseAuditEventMapper;
import com.paytech.risk.decision.domainservice.repository.ReleaseAuditEventRepository;

import lombok.AllArgsConstructor;

/** 追加审计必须命中准确行数，由外层保存阶段提供原子事务。 */
@Repository
@AllArgsConstructor
public class ReleaseAuditEventRepositoryImpl implements ReleaseAuditEventRepository {
    private final ReleaseAuditEventMapper mapper;

    /** 返回完整审计链。 */
    @Override
    public List<ReleaseAuditEvent> findByRelease(long releaseId) {
        return List.copyOf(mapper.selectReleaseAuditEvents(releaseId));
    }

    /** 空页不发送 IN 查询。 */
    @Override
    public List<ReleaseAuditEvent> findByReleases(List<Long> releaseIds) {
        return releaseIds.isEmpty() ? List.of() : List.copyOf(mapper.selectReleaseAuditEventsByReleaseIds(releaseIds));
    }

    /** 追加单条状态事实。 */
    @Override
    public void insert(ReleaseAuditEvent event) {
        if (mapper.insertReleaseAuditEvent(event) != 1) { throw new ErrorException("追加发布审计影响行数异常"); }
    }

    /** 八项检查同批写入；任意失败由外层事务整体回滚。 */
    @Override
    public void insertChecks(List<ReleaseAuditEvent> events) {
        if (mapper.insertReleaseAuditEvents(events) != events.size()) {
            throw new ErrorException("追加发布静态检查影响行数异常");
        }
    }
}
