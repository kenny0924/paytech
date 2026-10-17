package com.paytech.client.master.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.PartyProfileIdentifier;

/** t_party_profile_identifier 的身份绑定 SQL；业务 SQL 位于 XML。 */
public interface PartyProfileIdentifierMapper {
    /** 读取资料唯一的证件行。 */
    PartyProfileIdentifier findByProfile(@Param("profileId") long profileId);

    /** 以原绑定值为条件绑定新身份键，返回影响行数。 */
    int bind(@Param("identifier") PartyProfileIdentifier identifier, @Param("identityKeyId") long identityKeyId,
        @Param("issuerScopeKey") String issuerScopeKey, @Param("now") Instant now);
}
