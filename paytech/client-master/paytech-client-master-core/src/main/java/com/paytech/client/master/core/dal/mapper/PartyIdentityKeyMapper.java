package com.paytech.client.master.core.dal.mapper;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.PartyIdentityKey;

/** t_party_identity_key SQL；业务 SQL 位于 XML。 */
public interface PartyIdentityKeyMapper {
    /** 按唯一身份读取。 */
    PartyIdentityKey find(@Param("identifierType") String identifierType, @Param("issuerScopeKey") String issuerScopeKey,
        @Param("keyValue") String keyValue);

    /** 新增身份键；唯一索引冲突由调用方回滚。 */
    int insert(@Param("key") PartyIdentityKey key);
}
