package com.paytech.operations.console.bff.service.model;

import java.util.List;

/**
 * 已由 Platform 当前权限过滤的导航数据；BFF 不解释或生成权限。
 *
 * @author Codex
 */
public final class NavigationModels {
    private NavigationModels() {
    }

    public record NavigationResponseBody(List<ConsoleResource> resources) {
    }

    public record ConsoleResource(String resourceId, String resourceCode, String resourceType, String parentResourceId,
                                  String displayName, String frontendRoute, String componentKey, int sortOrder) {
    }
}
