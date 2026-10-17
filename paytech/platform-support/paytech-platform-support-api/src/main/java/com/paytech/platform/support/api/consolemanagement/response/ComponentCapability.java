package com.paytech.platform.support.api.consolemanagement.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 已部署页面和真实支持的按钮动作。 */
public final class ComponentCapability {
    /** 已部署组件键，目录和按钮可空。 */
    private final String componentKey;

    /** 展示名称。 */
    private final String displayName;

    /** 由已部署组件派生的前端路径。 */
    private final String frontendRoute;

    /** 组件实际支持的按钮动作。 */
    private final List<ActionCapability> actions;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public ComponentCapability(@JsonProperty(value = "componentKey") String componentKey,
        @JsonProperty(value = "displayName") String displayName,
        @JsonProperty(value = "frontendRoute") String frontendRoute,
        @JsonProperty(value = "actions") List<ActionCapability> actions) {
        this.componentKey = componentKey;
        this.displayName = displayName;
        this.frontendRoute = frontendRoute;
        this.actions = actions;
    }

    /** 已部署组件键，目录和按钮可空。 */
    public String getComponentKey() {
        return componentKey;
    }

    /** 展示名称。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 由已部署组件派生的前端路径。 */
    public String getFrontendRoute() {
        return frontendRoute;
    }

    /** 组件实际支持的按钮动作。 */
    public List<ActionCapability> getActions() {
        return actions;
    }
}
