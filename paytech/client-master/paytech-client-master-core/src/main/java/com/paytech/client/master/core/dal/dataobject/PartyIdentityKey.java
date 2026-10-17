package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 永久法律身份键（t_party_identity_key）；同一类型、签发作用域和规范化号码只能属于一个 Party。 */
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
    /** 签发作用域：签发国代码或 GLOBAL。 */
    private String issuerScopeKey;
    /** 规范化后的证件号码；不进入普通日志。 */
    private String normalizedIdentifierValue;
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
