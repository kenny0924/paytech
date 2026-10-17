package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialScopeGrantView;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiCredentialEffectiveScope;

/** API Credential Scope 表的只读持久化端口。 */
public interface ApiCredentialScopeRepository {

    /** 读取指定时点内生效、按代码去重的有限授权集合。 */
    List<ApiCredentialEffectiveScope> effectiveScopes(long credentialId, Instant effectiveAt, int limit);

    /** 分页读取全部授权及目录名称，不按凭证或授权有效性过滤。 */
    List<ApiCredentialScopeGrantView> scopes(long id, long offset, int size);

    /** 统计凭证的全部授权记录，与管理列表共用同一读取事务。 */
    long scopeCount(long id);
}
