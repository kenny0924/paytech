package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.JwtSigningKey;

/** JWT 签名密钥单表仓储，候选仅在所属模块内流转。 */
public interface JwtSigningKeyRepository {

    /** 读取 Operations Realm 的 ACTIVE 与 RETIRING 发布候选。 */
    List<JwtSigningKey> findOperationsVerificationCandidates();

    /** 读取 CUSTOMER Realm 的 ACTIVE 与 RETIRING 发布候选。 */
    List<JwtSigningKey> findCustomerVerificationCandidates();
}
