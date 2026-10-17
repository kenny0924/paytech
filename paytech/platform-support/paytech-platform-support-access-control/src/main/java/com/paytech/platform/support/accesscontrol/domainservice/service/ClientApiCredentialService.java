package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientApiCredentialChange;

/** 工作区自助 API 凭证的本地保存；只在调用方事务中读写本库。 */
public interface ClientApiCredentialService {
    /** 保存新凭证与其 Scope 授权。 */
    void create(ClientApiCredentialChange change);

    /** 保存新凭证，并撤销旧凭证或把其到期时间提前到宽限截止。 */
    void rotate(ClientApiCredentialChange change);

    /** 撤销旧凭证。 */
    void revoke(ClientApiCredentialChange change);
}
