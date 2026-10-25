package com.paytech.client.master.domain.enums;

import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.foundation.common.enums.CodeEnum;

/** t_party_role.role_code 的数据库闭合值域。 */
public enum PartyRoleCode implements CodeEnum {
    /** 平台 Client；subject_id 为 client_id。 */
    CLIENT("CLIENT"),

    /** 经营主体；subject_id 为 entity_no。 */
    OPERATING_ENTITY("OPERATING_ENTITY"),

    /** OE 提供方；subject_id 为 local_partner_id。 */
    OE_PROVIDER("OE_PROVIDER"),

    /** 推荐方；subject_id 为 local_partner_id。 */
    REFERRER("REFERRER"),

    /** 分账收款方；本期没有写入方。 */
    SPLIT_PAYEE("SPLIT_PAYEE");

    private final String code;

    /** 固定 Code。 */
    PartyRoleCode(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }

    /** Partner 角色在统一角色索引中的对应项。 */
    public static PartyRoleCode of(PartnerRoleCode role) {
        return role == PartnerRoleCode.OE_PROVIDER ? OE_PROVIDER : REFERRER;
    }
}
