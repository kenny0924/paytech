package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 工作区自助视角的 API 凭证行（t_api_credential）；不含 PHC 校验值。 */
@Getter
@Setter
@NoArgsConstructor
public class ClientApiCredential {
    /** 凭证主键。 */
    private long credentialId;

    /** 所属租户。 */
    private long tenantId;

    /** 密钥前缀。 */
    private String keyPrefix;

    /** 显示名称；历史凭证为空。 */
    private String credentialName;

    /** 到期时间。 */
    private Instant expiresTime;

    /** 持久化状态；到期按时间推导，不依赖后台改写。 */
    private ApiCredentialStatus storedStatus;

    /** 轮换来源凭证。 */
    private Long rotatedFromId;

    /** 并发版本。 */
    private int version;

    /** 创建时间。 */
    private Instant createTime;

    /** 当前有效 Scope 编码；查询后由 Repository 回填。 */
    private List<String> scopeCodes = List.of();

    /** 对外状态：已撤销优先，其次按到期时间推导 EXPIRED。 */
    public ApiCredentialStatus effectiveStatus(Instant now) {
        if (storedStatus == ApiCredentialStatus.REVOKED) {
            return ApiCredentialStatus.REVOKED;
        }
        if (storedStatus == ApiCredentialStatus.EXPIRED || !expiresTime.isAfter(now)) {
            return ApiCredentialStatus.EXPIRED;
        }
        return ApiCredentialStatus.ACTIVE;
    }

    /** 避免前缀与租户进入普通日志。 */
    @Override
    public String toString() {
        return "ClientApiCredential[REDACTED]";
    }
}
