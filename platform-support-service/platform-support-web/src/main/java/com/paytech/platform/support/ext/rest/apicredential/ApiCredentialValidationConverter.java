package com.paytech.platform.support.ext.rest.apicredential;

import com.paytech.platform.support.accesscontrol.domain.apicredential.ApiCredentialIdentityView;
import com.paytech.platform.support.api.apicredential.response.ValidatedCredentialResponseBody;

/** 将 Owner 可信身份转换为 HTTP 的十进制 ID 和稳定 Code。 */
public final class ApiCredentialValidationConverter {

    /** 禁止实例化无状态的入站转换器。 */
    private ApiCredentialValidationConverter() {}

    /** 仅转换传输类型，不查询持久化或重新执行认证。 */
    public static ValidatedCredentialResponseBody toResponseBody(ApiCredentialIdentityView identity) {
        return new ValidatedCredentialResponseBody(Long.toString(identity.getTenantId()),
            identity.getTenantType().getCode(), Long.toString(identity.getOwnerRefId()),
            Long.toString(identity.getCredentialId()), identity.getEffectiveScopeCodes());
    }
}
