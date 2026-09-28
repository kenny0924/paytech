package com.paytech.platform.support.accesscontrol.domain.operations;

import java.util.List;

/** Operations 角色与当前权限关联的联合展示视图。 */
@lombok.Getter
public class OperationsRoleView {
    /** 角色 ID，十进制字符串。 */
    private final String roleId;

    /** 不可变角色代码，1 至 64 个大写字母、数字或下划线，首字符为字母。 */
    private final String roleCode;

    /** 角色名称，去除首尾空白后为 1 至 255 个字符。 */
    private final String roleName;

    /** 是否为禁止普通管理操作修改的系统角色。 */
    private final boolean systemRole;

    /** 当前账号或角色聚合版本。 */
    private final int version;

    /** 完整权限 ID 集合，不允许重复；可显式提交空列表。 */
    private final List<String> permissionIds;

    /** 冻结本次查询集合，调用方不能改变已组装的响应事实。 */
    public OperationsRoleView(String roleId, String roleCode, String roleName, boolean systemRole, int version,
        List<String> permissionIds) {
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.systemRole = systemRole;
        this.version = version;
        this.permissionIds = List.copyOf(permissionIds);
    }
}
