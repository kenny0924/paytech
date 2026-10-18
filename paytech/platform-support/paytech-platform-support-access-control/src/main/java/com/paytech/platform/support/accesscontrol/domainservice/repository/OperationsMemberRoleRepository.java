package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;

/** Operations OperationsMemberRole 单表事实访问。 */
public interface OperationsMemberRoleRepository {
    /** 为新成员保存首次管理员授权；资格由条件插入最终保护。 */
    void grant(long id, OperationsMember member, OperationsRole role, long actorId, Instant now);

    /** 撤销该成员全部当前角色关联，保留历史。 */
    void revoke(long memberId, Instant now);

    /** 恢复本事务同微秒刚撤销的授权，否则插入新授权。 */
    void replace(long id, OperationsMember member, OperationsRole role, long actorId, Instant now);
}
