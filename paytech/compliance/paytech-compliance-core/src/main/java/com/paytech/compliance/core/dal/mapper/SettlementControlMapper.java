package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

/** t_settlement_control 的只读 SQL：资格查询只判断是否存在生效中的结算冻结；业务 SQL 位于 XML。 */
public interface SettlementControlMapper {
    /** 给定角色中存在 ACTIVE 且在生效期内的 SETTLEMENT_BLOCK 的角色 ID，去重。 */
    List<Long> findBlockedRoleIds(@Param("roleType") String roleType, @Param("roleIds") Collection<Long> roleIds,
        @Param("now") Instant now);
}
