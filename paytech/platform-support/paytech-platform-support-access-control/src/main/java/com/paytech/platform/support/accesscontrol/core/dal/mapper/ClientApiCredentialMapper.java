package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredential;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredentialScopeCode;

/** 工作区自助 API 凭证 SQL；SQL 位于 Mapper XML。 */
@Mapper
public interface ClientApiCredentialMapper {
    /** 本租户凭证，按创建倒序；status 为空时不过滤。 */
    List<ClientApiCredential> credentials(@Param("tenantId") long tenantId, @Param("status") String status,
        @Param("asOf") Instant asOf);

    /** 本租户指定凭证；不属于本租户为空。 */
    ClientApiCredential credential(@Param("tenantId") long tenantId, @Param("credentialId") long credentialId);

    /** 锁定本租户指定凭证；不属于本租户为空。 */
    ClientApiCredential lock(@Param("tenantId") long tenantId, @Param("credentialId") long credentialId);

    /** 本租户未撤销且未到期的凭证数量。 */
    long activeCount(@Param("tenantId") long tenantId, @Param("asOf") Instant asOf);

    /** 新凭证。 */
    int insert(@Param("row") ClientApiCredential row, @Param("verifierPhc") String verifierPhc);

    /** 按版本撤销。 */
    int revoke(@Param("credentialId") long credentialId, @Param("expectedVersion") int expectedVersion,
        @Param("now") Instant now);

    /** 按版本把到期时间提前到宽限截止时刻。 */
    int shorten(@Param("credentialId") long credentialId, @Param("expectedVersion") int expectedVersion,
        @Param("expiresTime") Instant expiresTime, @Param("now") Instant now);

    /** 指定凭证在给定时刻有效的 Scope 编码。 */
    List<ClientApiCredentialScopeCode> scopeCodes(@Param("credentialIds") Collection<Long> credentialIds,
        @Param("asOf") Instant asOf);

    /** 写入一项 ACTIVE Scope 授权。 */
    int insertScope(@Param("scopeId") long scopeId, @Param("credentialId") long credentialId,
        @Param("scopeCode") String scopeCode, @Param("now") Instant now);

    /** 目录中存在的 Scope 编码。 */
    List<String> catalogCodes(@Param("codes") Collection<String> codes);
}
