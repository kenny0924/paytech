package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientMember;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ClientMemberMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientMemberRepository;

import lombok.AllArgsConstructor;

/** CLIENT 租户成员的只读持久化实现。 */
@Repository
@AllArgsConstructor
public class ClientMemberRepositoryImpl implements ClientMemberRepository {
    private final ClientMemberMapper mapper;

    /** 读取 CLIENT 租户中的指定成员；不存在为空。 */
    @Override
    public ClientMember member(long memberId) {
        return mapper.member(memberId);
    }

    /** 在事务中以共享锁读取指定成员及其租户；不存在为空。 */
    @Override
    public ClientMember lockMember(long memberId) {
        return mapper.lockMember(memberId);
    }

    /** 读取账号在非 PENDING CLIENT 租户中的成员。 */
    @Override
    public List<ClientMember> members(long accountId) {
        return mapper.members(accountId);
    }
}
