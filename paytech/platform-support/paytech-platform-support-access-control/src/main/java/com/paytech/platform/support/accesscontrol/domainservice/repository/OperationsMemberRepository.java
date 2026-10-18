package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;

/** Operations 成员事实访问。 */
public interface OperationsMemberRepository {
    /** 读取成员及关联 INTERNAL Tenant 当前资格。 */
    OperationsMember member(long accountId);

    /** 保存人员成员关系。 */
    void insert(OperationsMember member, Instant now);

    /** 按原版本推进授权聚合。 */
    void advance(OperationsMember member, Instant now);

    /** 按原版本撤销成员资格。 */
    void revoke(OperationsMember member, Instant now);
}
