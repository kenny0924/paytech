package com.paytech.platform.support.accesscontrol.domainservice.service;

import java.util.List;
import com.paytech.platform.support.accesscontrol.domain.authentication.PublicVerificationKey;

/** CUSTOMER Realm 的独立公钥发布能力。 */
public interface CustomerJwksService {
    /** 返回当前 ACTIVE 及仍在验签窗口内的 RETIRING 公钥。 */
    List<PublicVerificationKey> queryCurrentJwks();
}
