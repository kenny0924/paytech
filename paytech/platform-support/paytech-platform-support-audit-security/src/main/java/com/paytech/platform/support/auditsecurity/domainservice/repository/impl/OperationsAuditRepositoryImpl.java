package com.paytech.platform.support.auditsecurity.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditAttribute;
import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditRecord;
import com.paytech.platform.support.auditsecurity.core.dal.mapper.OperationsAuditMapper;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditQuery;
import com.paytech.platform.support.auditsecurity.domainservice.repository.OperationsAuditRepository;

import lombok.AllArgsConstructor;

/** 封装审计本库访问；查询继续采用 Mapper 的安全分类投影。 */
@Repository
@AllArgsConstructor
public class OperationsAuditRepositoryImpl implements OperationsAuditRepository {
    private final OperationsAuditMapper mapper;

    @Override
    public List<OperationsAuditRecord> events(OperationsAuditQuery query, int offset, int size) {
        return mapper.events(query, offset, size);
    }

    @Override
    public long eventCount(OperationsAuditQuery query) {
        return mapper.eventCount(query);
    }

    @Override
    public OperationsAuditRecord event(long id) {
        return mapper.event(id);
    }

    @Override
    public List<OperationsAuditAttribute> attributes(long eventId) {
        return mapper.attributes(eventId);
    }

}
