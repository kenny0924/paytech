package com.paytech.client.master.domainservice.repository;

import java.time.Instant;

import com.paytech.client.master.core.dal.dataobject.PartyRole;
import com.paytech.client.master.domain.enums.PartyRoleCode;

/** t_party_role 的本地仓储；本期只有写入方，不负责其他表或事务。 */
public interface PartyRoleRepository {
    /** 插入一条 ACTIVE 角色索引行；同角色同主体已 ACTIVE 时由唯一键拒绝并整体回滚。 */
    void insert(PartyRole role);

    /**
     * 结束同一角色与主体的 ACTIVE 行
     * <li>迁移前的历史主体可能没有索引行，影响 0 行视为已结束；多于 1 行说明唯一键失效，抛出系统异常。</li>
     */
    void endActive(PartyRoleCode roleCode, String subjectId, Instant now);
}
