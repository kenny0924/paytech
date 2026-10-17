package com.paytech.platform.support.accesscontrol.domain.operations;

import com.paytech.platform.support.api.enums.OperationsConsoleResourceType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 当前角色及权限过滤后的导航资源查询结果。 */
@Getter
@Setter
@NoArgsConstructor
public class OperationsNavigationResourceView {
    /** 菜单或按钮主键。 */
    private long id;

    /** 同 Channel 父资源主键；根节点为空。 */
    private Long parentId;

    /** 稳定前端资源代码。 */
    private String code;

    /** 资源类型，仅 MENU 或 BUTTON。 */
    private OperationsConsoleResourceType type;

    /** 展示名称。 */
    private String displayName;

    /** 前端固定路径；不跳转的目录或按钮可空。 */
    private String frontendRoute;

    /** 前端预注册组件键，可空。 */
    private String componentKey;

    /** 绑定权限主键，供可授予资源查询复用，不进入导航 HTTP 响应。 */
    private long permissionId;

    /** 非负排序号；同序号按资源主键排序。 */
    private int sortOrder;
}
