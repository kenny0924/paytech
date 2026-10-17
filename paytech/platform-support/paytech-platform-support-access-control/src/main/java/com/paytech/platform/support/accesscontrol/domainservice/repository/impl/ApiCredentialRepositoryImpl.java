package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.domain.apicredential.ApiCredentialCandidateView;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ApiCredentialMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiCredentialRepository;

import lombok.AllArgsConstructor;

/** 封装本模块ApiCredential查询，不创建事务或复制同义模型。 */
@Repository
@AllArgsConstructor
public class ApiCredentialRepositoryImpl implements ApiCredentialRepository {

    private final ApiCredentialMapper mapper;

    /** 返回 Mapper 的只读投影，由认证用例判断业务有效性。 */
    @Override
    public List<ApiCredentialCandidateView> candidates(String keyPrefix, int limit) {
        return mapper.selectCandidates(keyPrefix, limit);
    }
}
