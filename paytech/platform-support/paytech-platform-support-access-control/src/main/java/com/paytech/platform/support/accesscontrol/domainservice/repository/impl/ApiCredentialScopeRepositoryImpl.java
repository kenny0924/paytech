package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialScopeGrantView;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiCredentialEffectiveScope;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ApiCredentialScopeMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiCredentialScopeRepository;

import lombok.AllArgsConstructor;

/** 封装本模块ApiCredentialScope查询，不创建事务或复制同义模型。 */
@Repository
@AllArgsConstructor
public class ApiCredentialScopeRepositoryImpl implements ApiCredentialScopeRepository {

    private final ApiCredentialScopeMapper mapper;

    /** 返回 Mapper 的只读投影，由认证用例判断业务有效性。 */
    @Override
    public List<ApiCredentialEffectiveScope> effectiveScopes(long credentialId, Instant effectiveAt, int limit) {
        return mapper.selectEffectiveScopes(credentialId, effectiveAt, limit);
    }

    /** 分页读取全部授权及目录名称，不按凭证或授权有效性过滤。 */
    @Override
    public List<ApiCredentialScopeGrantView> scopes(long id, long offset, int size) {
        return mapper.scopes(id, offset, size);
    }

    /** 统计凭证的全部授权记录，与管理列表共用同一读取事务。 */
    @Override
    public long scopeCount(long id) {
        return mapper.scopeCount(id);
    }
}
