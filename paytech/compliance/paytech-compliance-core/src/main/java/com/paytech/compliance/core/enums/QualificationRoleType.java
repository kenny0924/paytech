package com.paytech.compliance.core.enums;

import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 可查询、可手工登记角色资格的角色类型，及其角色标识格式与允许的资格类型
 * <li>OPERATING_ENTITY 以 entity_no（两位大写字母加三位数字，例如 AE001）标识；CLIENT、LOCAL_PARTNER 以正十进制 ID 标识。</li>
 * <li>CLIENT 允许 INDIVIDUAL_KYC 或 ORGANIZATION_KYB 任一；OPERATING_ENTITY 只允许 ENTITY_UNDERWRITING；LOCAL_PARTNER 只允许 PARTNER_DUE_DILIGENCE。</li>
 */
public enum QualificationRoleType implements CodeEnum {
    CLIENT("CLIENT", "[1-9][0-9]{0,18}", Set.of(QualificationType.INDIVIDUAL_KYC, QualificationType.ORGANIZATION_KYB)),

    OPERATING_ENTITY("OPERATING_ENTITY", "[A-Z]{2}[0-9]{3}", Set.of(QualificationType.ENTITY_UNDERWRITING)),

    LOCAL_PARTNER("LOCAL_PARTNER", "[1-9][0-9]{0,18}", Set.of(QualificationType.PARTNER_DUE_DILIGENCE));

    private final String code;

    private final Pattern roleIdFormat;

    private final Set<QualificationType> qualificationTypes;

    /** 固定业务代码、角色标识格式与允许的资格类型。 */
    QualificationRoleType(String code, String roleIdFormat, Set<QualificationType> qualificationTypes) {
        this.code = code;
        this.roleIdFormat = Pattern.compile(roleIdFormat);
        this.qualificationTypes = qualificationTypes;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 该角色要求的资格类型；查询时满足任一即可。 */
    public Set<QualificationType> getQualificationTypes() {
        return qualificationTypes;
    }

    /** 角色标识是否符合该角色的格式；十进制 ID 还必须在 BIGINT 范围内。 */
    public boolean acceptsRoleId(String roleId) {
        if (roleId == null || !roleIdFormat.matcher(roleId).matches()) {
            return false;
        }
        if (this == OPERATING_ENTITY) {
            return true;
        }
        try {
            Long.parseLong(roleId);
            return true;
        } catch (NumberFormatException overflow) {
            return false;
        }
    }

    /** 该角色是否允许登记给定资格类型。 */
    public boolean allows(QualificationType qualificationType) {
        return qualificationTypes.contains(qualificationType);
    }

    /** 按代码查找，未知代码返回 null 由调用方 Fail-Closed。 */
    public static QualificationRoleType ofCode(String code) {
        for (QualificationRoleType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
