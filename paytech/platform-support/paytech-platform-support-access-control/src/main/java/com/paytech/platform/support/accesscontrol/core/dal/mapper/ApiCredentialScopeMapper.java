package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialScopeGrantView;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiCredentialEffectiveScope;

/** API Credential Scope 的本库只读 SQL 边界。 */
public interface ApiCredentialScopeMapper {

    /** 按半开有效期读取指定凭证的授权，保留 65 项查询上限以检测超限。 */
    List<ApiCredentialEffectiveScope> selectEffectiveScopes(@Param("credentialId") long credentialId,
        @Param("effectiveAt") Instant effectiveAt, @Param("limit") int limit);

    /** 查询全部授权及可空目录名称，按授权主键升序分页。 */
    List<ApiCredentialScopeGrantView> scopes(@Param("id") long id, @Param("offset") long offset, @Param("size") int size);

    /** 统计指定凭证的全部授权记录。 */
    long scopeCount(@Param("id") long id);
}
