package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.util.List;

/** Platform 管理维护策略的领域结果，不参与 Gateway 业务路由匹配。 */
public record ManagementProtection(List<String> editableFields, boolean statusChangeAllowed, String reason) {
    public ManagementProtection {
        editableFields = List.copyOf(editableFields);
    }

    /** 未受保护对象仍须通过编译器、权限和 CAS 校验。 */
    public static ManagementProtection editable(String... fields) {
        return new ManagementProtection(List.of(fields), true, null);
    }

    /** 内置管理对象仅允许列出的安全维护字段。 */
    public static ManagementProtection protectedFields(String... fields) {
        return new ManagementProtection(List.of(fields), false, "内置管理入口，请通过已评审的人工配置变更维护");
    }
}
