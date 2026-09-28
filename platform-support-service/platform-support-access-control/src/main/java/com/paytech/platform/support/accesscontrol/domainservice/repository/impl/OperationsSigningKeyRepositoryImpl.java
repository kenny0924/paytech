package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSigningKey;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsIdentityMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsSigningKeyRepository;
import org.springframework.stereotype.Repository;
import lombok.AllArgsConstructor;

/** 条件写成功后同步原对象；事务由完整用例控制。 */
@Repository
@AllArgsConstructor
public class OperationsSigningKeyRepositoryImpl implements OperationsSigningKeyRepository {
    private final OperationsIdentityMapper mapper;
    /** 读取唯一活动的 Operations 签名密钥。 */
    @Override
    public OperationsSigningKey signingKey() {
        return mapper.signingKey();
    }

}
