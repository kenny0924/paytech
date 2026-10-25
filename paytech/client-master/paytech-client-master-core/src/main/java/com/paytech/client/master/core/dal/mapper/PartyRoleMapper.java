package com.paytech.client.master.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.PartyRole;
import com.paytech.client.master.domain.enums.PartyRoleCode;

/** t_party_role 的持久化写入。 */
public interface PartyRoleMapper {
    /** 插入一条角色索引行。 */
    void insert(@Param("row") PartyRole row);

    /** 结束同一角色与主体的 ACTIVE 行，返回影响行数。 */
    int endActive(@Param("roleCode") PartyRoleCode roleCode, @Param("subjectId") String subjectId, @Param("now") Instant now);
}
