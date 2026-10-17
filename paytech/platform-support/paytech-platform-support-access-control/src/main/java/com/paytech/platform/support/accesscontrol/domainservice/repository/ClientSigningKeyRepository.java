package com.paytech.platform.support.accesscontrol.domainservice.repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSigningKey;

/** ClientSigningKey 单表持久化合同；更新成功后同步传入对象。 */
public interface ClientSigningKeyRepository {
    /** 读取 CUSTOMER 唯一 ACTIVE 签名密钥。 */
    ClientSigningKey signingKey();

}
