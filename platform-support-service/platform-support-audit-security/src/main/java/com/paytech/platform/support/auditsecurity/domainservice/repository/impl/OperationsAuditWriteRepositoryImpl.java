package com.paytech.platform.support.auditsecurity.domainservice.repository.impl;

import java.time.Instant;
import java.time.LocalDate;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.paytech.platform.support.auditsecurity.core.dal.dataobject.OperationsAuditChain;
import com.paytech.platform.support.auditsecurity.core.dal.mapper.OperationsAuditMapper;
import com.paytech.platform.support.auditsecurity.domainservice.repository.OperationsAuditWriteRepository;

/** 使用专用审计会话复用同一份 Mapper SQL，不参与业务 Tenant 锁。 */
@Repository
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${operations.authentication.enabled:false} || ${client.authentication.enabled:false}")
public class OperationsAuditWriteRepositoryImpl implements OperationsAuditWriteRepository {
    private final OperationsAuditMapper mapper;

    public OperationsAuditWriteRepositoryImpl(@Qualifier("auditWriteSqlSessionTemplate") SqlSessionTemplate template) {
        mapper = template.getMapper(OperationsAuditMapper.class);
    }

    @Override
    public void ensureChain(long id, String key, LocalDate date, Instant now) {
        mapper.ensureChain(id, key, date, now);
    }

    @Override
    public OperationsAuditChain lockChain(LocalDate date) {
        return mapper.lockChain(date);
    }

    @Override
    public void insertEvent(long id, long chainId, int sequence, String previous, String attributeHash,
        String eventHash, String actorId, Long tenantId, String action, String resourceType, String resourceId,
        String result, String requestId, Instant now, Instant receivedAt) {
        mapper.insertEvent(id, chainId, sequence, previous, attributeHash, eventHash, actorId, tenantId, action,
            resourceType, resourceId, result, requestId, now, receivedAt);
    }

    @Override
    public void insertAttribute(long id, long eventId, int number, String key, String value, String hash, Instant now) {
        mapper.insertAttribute(id, eventId, number, key, value, hash, now);
    }

    @Override
    public int advanceChain(long id, int sequence, String hash, Instant now) {
        return mapper.advanceChain(id, sequence, hash, now);
    }

    @Override
    public void insertSecurity(long id, String action, String actorId, Long tenantId, String eventId, String hash,
        Instant now, Instant receivedAt) {
        mapper.insertSecurity(id, action, actorId, tenantId, eventId, hash, now, receivedAt);
    }

}
