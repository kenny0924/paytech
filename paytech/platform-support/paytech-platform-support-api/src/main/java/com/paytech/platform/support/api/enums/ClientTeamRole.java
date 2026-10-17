package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** CLIENT 工作区的系统角色；每个工作区各有一份，成员同时只持有一个。 */
public enum ClientTeamRole implements CodeMessageEnum {
    /** 入驻申请人，拥有全部权限；不可邀请、修改或移除。 */
    TENANT_OWNER("TENANT_OWNER", "所有者", "OWNER", false),

    TENANT_ADMIN("TENANT_ADMIN", "管理员", "ADMIN", true),

    TENANT_DEVELOPER("TENANT_DEVELOPER", "开发者", "MEMBER", true),

    TENANT_VIEWER("TENANT_VIEWER", "只读成员", "MEMBER", true);

    private final String code;
    private final String message;
    private final String memberType;
    private final boolean assignable;

    /** 绑定角色代码、显示名、对应成员类型和是否可邀请 / 改授。 */
    ClientTeamRole(String code, String message, String memberType, boolean assignable) {
        this.code = code;
        this.message = message;
        this.memberType = memberType;
        this.assignable = assignable;
    }

    /** 返回稳定角色代码。 */
    @Override public String getCode() { return code; }

    /** 返回角色显示名。 */
    @Override public String getMessage() { return message; }

    /** 返回授予该角色时成员的类型（OWNER、ADMIN 或 MEMBER）。 */
    public String getMemberType() { return memberType; }

    /** 是否可以通过邀请或改授获得。 */
    public boolean isAssignable() { return assignable; }
}
