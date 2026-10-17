package com.paytech.platform.support.accesscontrol.domain.apicredential;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PLT-I03 按 Key Prefix 定位的 Credential 与所属 Tenant 持久化投影。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiCredentialCandidateView {

    /** API Credential 的稳定业务标识。 */
    private long credentialId;

    /** Credential 所属 Access Tenant 的稳定业务标识。 */
    private long tenantId;

    /** 仅用于有界候选定位的 Key Prefix，不代表 Tenant 或 Client 身份。 */
    private String keyPrefix;

    /** 完整 opaque Credential 的不可逆 Argon2id PHC verifier。 */
    private String keyVerifierPhc;

    /** Credential 到期的 UTC 时刻。 */
    private Instant expiresTime;

    /** Credential 当前生命周期代码。 */
    private ApiCredentialStatus status;

    /** 所属 Access Tenant 的类型代码；Tenant 行缺失时为空。 */
    private AccessTenantType tenantType;

    /** 所属 Client 的 CCT Owner 引用；Tenant 行缺失或状态非法时可为空。 */
    private Long ownerRefId;

    /** CCT 投影到 Access Tenant 的 Owner 状态；Tenant 行缺失时为空。 */
    private OperationsTenantOwnerStatus ownerStatus;

    /** Platform 对 Access Tenant 的安全状态；Tenant 行缺失时为空。 */
    private OperationsTenantSecurityStatus securityStatus;

    /** Credential 的乐观并发版本。 */
    private int credentialVersion;

    /** 所属 Access Tenant 的乐观并发版本。 */
    private int tenantVersion;

    /** 诊断时隐藏包含 PHC 的整个投影。 */
    @Override
    public String toString() {
        return "ApiCredentialCandidateView[protected projection]";
    }
}
