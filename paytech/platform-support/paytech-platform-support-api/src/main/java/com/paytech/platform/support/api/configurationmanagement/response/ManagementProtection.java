package com.paytech.platform.support.api.configurationmanagement.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 服务端计算的可维护字段；仍需同时满足操作者的业务权限。 */
public final class ManagementProtection {
    /** 当前允许维护的字段。 */
    private final List<String> editableFields;

    /** 是否允许启停；仍需操作者具备业务权限。 */
    private final boolean statusChangeAllowed;

    /** 操作原因或维护限制说明。 */
    private final String reason;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public ManagementProtection(@JsonProperty(value = "editableFields") List<String> editableFields,
        @JsonProperty(value = "statusChangeAllowed") boolean statusChangeAllowed,
        @JsonProperty(value = "reason") String reason) {
        this.editableFields = List.copyOf(editableFields);
        this.statusChangeAllowed = statusChangeAllowed;
        this.reason = reason;
    }

    /** 当前允许维护的字段。 */
    public List<String> getEditableFields() {
        return editableFields;
    }

    /** 是否允许启停；仍需操作者具备业务权限。 */
    public boolean isStatusChangeAllowed() {
        return statusChangeAllowed;
    }

    /** 操作原因或维护限制说明。 */
    public String getReason() {
        return reason;
    }
}
