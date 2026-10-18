package com.paytech.platform.support.api.consolemanagement.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 动作代码必须与 Web 中的真实处理器一致。 */
public final class ActionCapability {
    /** 稳定资源编码。 */
    private final String resourceCode;

    /** 展示名称。 */
    private final String displayName;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public ActionCapability(@JsonProperty(value = "resourceCode") String resourceCode,
        @JsonProperty(value = "displayName") String displayName) {
        this.resourceCode = resourceCode;
        this.displayName = displayName;
    }

    /** 稳定资源编码。 */
    public String getResourceCode() {
        return resourceCode;
    }

    /** 展示名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
