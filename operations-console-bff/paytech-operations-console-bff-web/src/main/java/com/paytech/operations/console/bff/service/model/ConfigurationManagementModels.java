package com.paytech.operations.console.bff.service.model;

import java.util.List;

/** 配置管理共用的轻量保存回执与当前维护边界。 */
public final class ConfigurationManagementModels {
    private ConfigurationManagementModels() {}

    /** 保存当次的身份、状态及版本；重放不读取后续的新版本。 */
    public record ConfigurationMutationResponseBody(String objectType, String objectId, String objectCode,
        String status, int version) {}

    /** 服务端计算的可维护字段；仍需同时满足操作者的业务权限。 */
    public record ManagementProtection(List<String> editableFields, boolean statusChangeAllowed, String reason) {
        public ManagementProtection {
            editableFields = List.copyOf(editableFields);
        }
    }
}
