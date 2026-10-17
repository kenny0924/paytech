package com.paytech.platform.support.accesscontrol.domainservice.repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSigningKey;

/** Operations OperationsSigningKey 单表事实访问。 */
public interface OperationsSigningKeyRepository {
    /** 读取唯一活动的 Operations 签名密钥。 */
    OperationsSigningKey signingKey();

}
