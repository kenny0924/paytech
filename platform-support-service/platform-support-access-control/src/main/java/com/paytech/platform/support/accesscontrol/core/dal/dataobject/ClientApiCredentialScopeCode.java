package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 凭证与其一个当前有效 Scope 编码。 */
@Getter
@Setter
@NoArgsConstructor
public class ClientApiCredentialScopeCode {
    /** 凭证主键。 */
    private long credentialId;

    /** Scope 编码。 */
    private String scopeCode;
}
