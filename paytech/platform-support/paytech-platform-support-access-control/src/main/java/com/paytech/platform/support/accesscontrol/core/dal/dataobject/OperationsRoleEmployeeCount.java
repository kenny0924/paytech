package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

/** 一次分组查询返回的角色人员关联数量。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class OperationsRoleEmployeeCount {
    /** 当前 Operations 范围内的角色主键。 */
    private long roleId;

    /** 含未来授权、暂停成员和停用人员的去重账号数。 */
    private long employeeCount;

}
