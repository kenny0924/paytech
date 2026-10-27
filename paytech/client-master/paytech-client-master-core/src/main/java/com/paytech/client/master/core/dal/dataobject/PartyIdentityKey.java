package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 永久法律身份键（t_party_identity_key）；同一类型、作用域和号码（个人证件为 STABLE Token）只能属于一个 Party。 */
@Getter
@Setter
public class PartyIdentityKey {
    /** 身份键 ID。 */
    private long identityKeyId;
    /** 所属 Party ID。 */
    private long partyId;
    /** 证件类型代码。 */
    private String identifierType;
    /** 签发国；全局标识为空。 */
    private String issuingCountry;
    /** 唯一性作用域：GLOBAL、签发国代码或登记机关代码。 */
    private String issuerScopeKey;
    /** 唯一性比较值：VAULT_REQUIRED 类型为 STABLE Token，其他类型为规范化公开登记号；不进入普通日志。 */
    private String identifierKeyValue;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;

    /** 号码不进入日志。 */
    @Override
    public String toString() {
        return "PartyIdentityKey[redacted]";
    }
}
