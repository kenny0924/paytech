package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.JwtSigningKey;

/**
 * JWT 签名密钥集合查询。
 */
public interface JwtSigningKeyMapper {

    /**
     * 查询 Operations Realm 中可能进入 JWKS 的当前生命周期记录。
     *
     * @return ACTIVE 与 RETIRING 候选记录
     */
    List<JwtSigningKey> selectOperationsVerificationCandidates();

    /** 读取 CUSTOMER Realm 中 ACTIVE 与 RETIRING 候选记录。 */
    List<JwtSigningKey> selectCustomerVerificationCandidates();
}
