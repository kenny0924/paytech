package com.paytech.platform.support.api.authorization.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 当前权限过滤后的导航响应。 */
public final class NavigationResponseBody {
    /** 可见的 MENU/BUTTON 列表，按排序号及资源主键排列；父子关系由 parentResourceId 表达。 */
    private final List<ConsoleResource> resources;

    /** 绑定当前可见资源列表。 */
    @JsonCreator
    public NavigationResponseBody(@JsonProperty("resources") List<ConsoleResource> resources) {
        this.resources = resources;
    }

    /** 返回当前可见资源列表。 */
    public List<ConsoleResource> getResources() {
        return resources;
    }
}
