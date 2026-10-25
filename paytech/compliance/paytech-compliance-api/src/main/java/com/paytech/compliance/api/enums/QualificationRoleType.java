package com.paytech.compliance.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 资格判定与运营登记接受的角色类型
 * <li>OPERATING_ENTITY 以 entity_no（例如 AE001）标识；CLIENT、LOCAL_PARTNER 以正十进制 ID 标识。</li>
 * <li>要求的资格类型：CLIENT 为 INDIVIDUAL_KYC 或 ORGANIZATION_KYB，OPERATING_ENTITY 为 ENTITY_UNDERWRITING，LOCAL_PARTNER 为
 * PARTNER_DUE_DILIGENCE。</li>
 */
public enum QualificationRoleType implements CodeEnum {
    CLIENT("CLIENT"),

    OPERATING_ENTITY("OPERATING_ENTITY"),

    LOCAL_PARTNER("LOCAL_PARTNER");

    private final String code;

    /** 固定业务代码，不依赖 Java 枚举名称。 */
    QualificationRoleType(String code) {
        this.code = code;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }
}
