package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.JwtSigningKey;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.JwtSigningKeyMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.JwtSigningKeyRepository;

import lombok.AllArgsConstructor;

/** 直接返回所属模块的签名密钥投影，SQL 决定 Realm、候选范围与顺序。 */
@Repository
@AllArgsConstructor
public class JwtSigningKeyRepositoryImpl implements JwtSigningKeyRepository {

    private final JwtSigningKeyMapper mapper;

    /** 读取 Operations 发布候选。 */
    @Override
    public List<JwtSigningKey> findOperationsVerificationCandidates() {
        return mapper.selectOperationsVerificationCandidates();
    }

    /** 读取 CUSTOMER 发布候选。 */
    @Override
    public List<JwtSigningKey> findCustomerVerificationCandidates() {
        return mapper.selectCustomerVerificationCandidates();
    }
}
