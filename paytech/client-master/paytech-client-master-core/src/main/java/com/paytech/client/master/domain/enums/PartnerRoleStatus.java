package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** t_partner_role.status 的数据库闭合值域。 */
public enum PartnerRoleStatus implements CodeEnum {
    /** 角色有效，占用 active_slot。 */
    ACTIVE("ACTIVE"),

    /** 角色已结束，active_slot 为空。 */
    ENDED("ENDED");

    private final String code;

    /** 固定 Code。 */
    PartnerRoleStatus(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
