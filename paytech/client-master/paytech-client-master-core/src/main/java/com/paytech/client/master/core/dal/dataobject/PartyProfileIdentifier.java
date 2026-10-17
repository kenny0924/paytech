package com.paytech.client.master.core.dal.dataobject;

import lombok.Getter;
import lombok.Setter;

/** 资料填报的证件行（t_party_profile_identifier）；正式提交时绑定永久身份键。 */
@Getter
@Setter
public class PartyProfileIdentifier {
    /** 证件行 ID。 */
    private long profileIdentifierId;
    /** 所属资料 ID。 */
    private long profileId;
    /** 已绑定的永久身份键；未提交过为空。 */
    private Long identityKeyId;
    /** 已绑定时的签发作用域。 */
    private String issuerScopeKey;

    /** 号码类字段不在本对象中，toString 仍保持脱敏习惯。 */
    @Override
    public String toString() {
        return "PartyProfileIdentifier[redacted]";
    }
}
