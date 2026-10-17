package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsDirectoryMapper;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsIdentityMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsMemberRepository;

import lombok.AllArgsConstructor;

/** 条件写成功后同步原对象；事务由完整用例控制。 */
@Repository
@AllArgsConstructor
public class OperationsMemberRepositoryImpl implements OperationsMemberRepository {
    private final OperationsIdentityMapper mapper;
    private final OperationsDirectoryMapper directory;
    /** 读取成员及关联 INTERNAL Tenant 当前资格。 */
    @Override
    public OperationsMember member(long accountId) {
        return mapper.member(accountId);
    }

    /** 保存人员成员关系。 */
    @Override
    public void insert(OperationsMember member, Instant now) {
        requireOne(mapper.insertMember(member.id(), member.tenantId(), member.accountId(), now));
    }

    /** 条件写必须影响恰好一行，否则使整个本地事务回滚。 */
    void requireOne(int rows) {
        if (rows != 1) { throw new ConcurrentRequestException("认证事实已变化，请重新操作"); }
    }

    /** 保存授权版本，SQL 成功后回写原成员。 */
    @Override
    public void advance(OperationsMember member, Instant now) {
        requireOne(directory.advanceMember(member.id(), member.version(), now));
        member.version(member.version() + 1);
    }

    /** 保存撤销状态，SQL 成功后回写原成员。 */
    @Override
    public void revoke(OperationsMember member, Instant now) {
        requireOne(mapper.revokeMember(member.id(), member.tenantId(), member.version(), now));
        member.status(OperationsMemberStatus.REVOKED);
        member.version(member.version() + 1);
    }
}
