package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientMember;

/** CLIENT 租户成员（t_tenant_member 及其租户状态）的只读持久化合同。 */
public interface ClientMemberRepository {
    /** 读取 CLIENT 租户中的指定成员；不存在为空。 */
    ClientMember member(long memberId);

    /** 在事务中以共享锁读取指定成员及其租户；不存在为空。 */
    ClientMember lockMember(long memberId);

    /** 读取账号在非 PENDING CLIENT 租户中的成员。 */
    List<ClientMember> members(long accountId);
}
