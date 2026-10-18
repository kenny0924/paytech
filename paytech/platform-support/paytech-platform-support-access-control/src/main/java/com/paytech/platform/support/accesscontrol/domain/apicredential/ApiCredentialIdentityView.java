package com.paytech.platform.support.accesscontrol.domain.apicredential;

import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;

import lombok.Getter;

/** 凭证、租户与授权多表查询验证后的可信身份，不携带 PHC 或原始凭证。 */
@Getter
public final class ApiCredentialIdentityView {

    /** 已验证的租户标识。 */
    private final long tenantId;

    /** 已验证的租户类型。 */
    private final AccessTenantType tenantType;

    /** 租户对应的 Client 标识。 */
    private final long ownerRefId;

    /** 已验证的凭证标识。 */
    private final long credentialId;

    /** 同一快照内生效的授权代码。 */
    private final List<String> effectiveScopeCodes;

    /** 冻结可信身份和不可变授权列表供协议边界消费。 */
    public ApiCredentialIdentityView(long tenantId, AccessTenantType tenantType, long ownerRefId,
        long credentialId, List<String> effectiveScopeCodes) {
        this.tenantId = tenantId;
        this.tenantType = tenantType;
        this.ownerRefId = ownerRefId;
        this.credentialId = credentialId;
        this.effectiveScopeCodes = List.copyOf(effectiveScopeCodes);
    }
}
