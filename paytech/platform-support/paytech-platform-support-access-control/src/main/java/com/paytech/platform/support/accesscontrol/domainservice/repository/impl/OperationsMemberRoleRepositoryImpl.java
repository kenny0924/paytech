package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsDirectoryMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsMemberRoleRepository;

import lombok.AllArgsConstructor;

/** 条件写成功后同步原对象；事务由完整用例控制。 */
@Repository
@AllArgsConstructor
public class OperationsMemberRoleRepositoryImpl implements OperationsMemberRoleRepository {
    private final OperationsDirectoryMapper mapper;
    /** 为新成员保存首次管理员授权；资格由条件插入最终保护。 */
    @Override
    public void grant(long id, OperationsMember member, OperationsRole role, long actorId, Instant now) {
        requireOne(mapper.grantRole(id, member.id(), member.tenantId(), role.id(), actorId, now));
    }

    /** 条件写必须影响恰好一行，否则使整个本地事务回滚。 */
    void requireOne(int rows) {
        if (rows != 1) { throw new ConcurrentRequestException("认证事实已变化，请重新操作"); }
    }

    /** 角色替换由 Service 在成员 CAS 后调用。 */
    @Override
    public void revoke(long memberId, Instant now) {
        mapper.revokeRoles(memberId, now);
    }

    /** 同一微秒内的替换复用原关联，不吞未知唯一冲突。 */
    @Override
    public void replace(long id, OperationsMember member, OperationsRole role, long actorId, Instant now) {
        int restored = mapper.restoreJustRevokedRole(member.id(), member.tenantId(), role.id(), actorId, now);
        if (restored == 0) {
            grant(id, member, role, actorId, now);
        } else {
            requireOne(restored);
        }
    }
}
