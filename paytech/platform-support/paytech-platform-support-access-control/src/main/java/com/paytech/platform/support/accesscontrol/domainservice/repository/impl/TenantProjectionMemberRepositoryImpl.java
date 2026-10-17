package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionMember;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantProjectionMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionMemberRepository;

import lombok.AllArgsConstructor;

/** 复用本表 SQL，不开启事务或调用外部能力。 */
@Repository
@AllArgsConstructor
public class TenantProjectionMemberRepositoryImpl implements TenantProjectionMemberRepository {
    private final TenantProjectionMapper mapper;

    /** 读取初始 OWNER，不过滤独立成员状态。 */
    @Override
    public List<TenantProjectionMember> owners(long tenantId) {
        return mapper.owners(tenantId);
    }

    /** 保存原申请账号的 OWNER 成员。 */
    @Override
    public void insert(TenantProjectionMember member, long tenantId, Instant now) {
        requireOne(mapper.insertMember(member.memberId(), tenantId, member.accountId(), now));
    }

    /** 写入必须准确影响一行，不将其他唯一键冲突解释为成功。 */
    private void requireOne(int rows) {
        if (rows != 1) throw new ConcurrentRequestException("租户投影已被并发更新，请重试原来源命令");
    }
}
