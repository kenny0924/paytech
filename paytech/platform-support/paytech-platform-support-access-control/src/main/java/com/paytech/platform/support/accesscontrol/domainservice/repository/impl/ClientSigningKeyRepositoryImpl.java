package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSigningKey;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerAuthenticationMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientSigningKeyRepository;
import org.springframework.stereotype.Repository;
import lombok.AllArgsConstructor;

/** ClientSigningKey 的单表持久化实现。 */
@Repository
@AllArgsConstructor
public class ClientSigningKeyRepositoryImpl implements ClientSigningKeyRepository {
    private final CustomerAuthenticationMapper authentication;

    /** 读取 CUSTOMER 唯一 ACTIVE 签名密钥。 */
    @Override
    public ClientSigningKey signingKey() {
        return authentication.signingKey();
    }
}
