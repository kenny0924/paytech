package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** CLIENT 工作区成员当前权限的只读 SQL；SQL 位于 Mapper XML。 */
@Mapper
public interface ClientPermissionMapper {
    /** 成员在本租户内通过有效角色授予、有效角色与有效权限得到的权限码，限定在给定集合内。 */
    List<String> granted(@Param("memberId") long memberId, @Param("tenantId") long tenantId,
        @Param("codes") Collection<String> codes, @Param("now") Instant now);
}
