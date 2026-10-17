package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

/** 批量成员角色查询结果，以成员主键归组后提供完整角色展示事实。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class OperationsMemberRole {
    /** 当前有效授权所属的成员主键。 */
    private long memberId;

    /** 被授权角色的稳定主键。 */
    private long roleId;

    /** 被授权角色的稳定业务代码。 */
    private String code;

    /** 被授权角色的受控展示名称。 */
    private String name;

    /** 是否为禁止普通 Operations 修改的系统角色。 */
    private boolean systemRole;

    /** 角色聚合的当前并发版本。 */
    private int version;

    /** 是否属于 PLATFORM 角色范围。 */
    private boolean platformRole;

    /** 还原该有效关联指向的角色事实。 */
    public OperationsRole role() {
        return new OperationsRole(roleId, code, name, systemRole, version, platformRole);
    }
}
