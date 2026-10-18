package com.paytech.platform.support.api.consolemanagement.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 部署能力的身份由代码提供，资源实例由数据库管理。 */
public final class ConsoleResourceCapabilitiesResponseBody {
    /** 当前部署的页面组件。 */
    private final List<ComponentCapability> components;

    /** 支持的资源类型。 */
    private final List<String> resourceTypes;

    /** 资源树最大深度。 */
    private final int maxDepth;

    /** 完整资源树数量上限。 */
    private final int maxResources;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public ConsoleResourceCapabilitiesResponseBody(
        @JsonProperty(value = "components") List<ComponentCapability> components,
        @JsonProperty(value = "resourceTypes") List<String> resourceTypes,
        @JsonProperty(value = "maxDepth") int maxDepth, @JsonProperty(value = "maxResources") int maxResources) {
        this.components = components;
        this.resourceTypes = resourceTypes;
        this.maxDepth = maxDepth;
        this.maxResources = maxResources;
    }

    /** 当前部署的页面组件。 */
    public List<ComponentCapability> getComponents() {
        return components;
    }

    /** 支持的资源类型。 */
    public List<String> getResourceTypes() {
        return resourceTypes;
    }

    /** 资源树最大深度。 */
    public int getMaxDepth() {
        return maxDepth;
    }

    /** 完整资源树数量上限。 */
    public int getMaxResources() {
        return maxResources;
    }
}
