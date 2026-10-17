package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientMember;

/** CUSTOMER 账号的 CLIENT 租户成员只读查询；SQL 位于 XML。 */
public interface ClientMemberMapper {
    /** 读取 CLIENT 租户中的指定成员。 */
    ClientMember member(@Param("memberId") long memberId);

    /** 以共享锁读取指定成员及其租户，阻止并发状态变更越过本次校验。 */
    ClientMember lockMember(@Param("memberId") long memberId);

    /** 读取账号在非 PENDING CLIENT 租户中未被移除（非 REVOKED）的成员，按租户编号升序，最多 50 项。 */
    List<ClientMember> members(@Param("accountId") long accountId);
}
