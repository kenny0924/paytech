package com.paytech.platform.support.accesscontrol.domainservice.service;

import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.authentication.PublicVerificationKey;

/**
 * Operations Realm JWKS 查询服务。
 */
public interface OperationsJwksService {

    /**
     * 查询当前完整且可安全发布的 Operations JWKS。
     *
     * @return Operations Realm 完整的已校验公开验签密钥列表
     */
    List<PublicVerificationKey> queryCurrentJwks();
}
