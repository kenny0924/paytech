package com.paytech.arch.model;

import com.paytech.arch.catalog.ServiceCatalog;

import java.util.Optional;
import java.util.Set;

/**
 * 模块归属：所属服务与角色。
 *
 * @param groupId Maven groupId
 * @param artifactId Maven artifactId
 * @param service 所属逻辑服务；非服务模块为 null
 * @param role 模块角色
 * @author Claude
 */
public record ModuleIdentity(String groupId, String artifactId, String service, ModuleRole role) {

    private static final String PAYTECH_GROUP = "com.paytech";

    private static final Set<String> BUILD_ARTIFACTS = Set.of("paytech", "paytech-dependencies", "paytech-parent");

    private static final Set<String> TOOL_PREFIXES = Set.of("paytech-arch-rules", "paytech-instance-manifest",
        "paytech-migration-runner");

    private static final Set<String> FOUNDATION_ARTIFACTS = Set.of("paytech-foundation", "paytech-common-core",
        "paytech-http-problem", "paytech-json-canonicalization", "paytech-http-client-support",
        "paytech-mybatis-support", "paytech-snowflake-id", "paytech-mvc-web-starter", "paytech-caffeine-starter",
        "paytech-redis-starter", "paytech-compensation-event", "paytech-xxl-job-starter", "paytech-test-support",
        "paytech-command-idempotency", "paytech-messaging", "paytech-messaging-api", "paytech-internal-auth");

    /**
     * 按 Maven 坐标判定模块归属。
     *
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @return 模块归属
     */
    public static ModuleIdentity classify(String groupId, String artifactId) {
        if (!PAYTECH_GROUP.equals(groupId)) {
            return new ModuleIdentity(groupId, artifactId, null, ModuleRole.UNCLASSIFIED);
        }
        if (BUILD_ARTIFACTS.contains(artifactId)) {
            return new ModuleIdentity(groupId, artifactId, null, ModuleRole.BUILD);
        }
        if (FOUNDATION_ARTIFACTS.contains(artifactId)) {
            return new ModuleIdentity(groupId, artifactId, null, ModuleRole.FOUNDATION);
        }
        if (TOOL_PREFIXES.stream().anyMatch(artifactId::startsWith)) {
            return new ModuleIdentity(groupId, artifactId, null, ModuleRole.TOOL);
        }
        Optional<String> service = ServiceCatalog.serviceOfArtifact(artifactId);
        if (service.isEmpty()) {
            return new ModuleIdentity(groupId, artifactId, null, ModuleRole.UNCLASSIFIED);
        }
        String suffix = artifactId.substring(("paytech-" + service.get()).length());
        return new ModuleIdentity(groupId, artifactId, service.get(), roleOfSuffix(suffix));
    }

    private static ModuleRole roleOfSuffix(String suffix) {
        return switch (suffix) {
            case "" -> ModuleRole.SERVICE_AGGREGATOR;
            case "-api" -> ModuleRole.API;
            case "-app" -> ModuleRole.APP;
            case "-test" -> ModuleRole.TEST;
            case "-client" -> ModuleRole.CLIENT;
            default -> ModuleRole.CORE;
        };
    }

    /**
     * 是否属于某个逻辑服务。
     *
     * @return 属于服务返回 true
     */
    public boolean isServiceModule() {
        return service != null;
    }

    /**
     * 是否属于另一个服务的指定角色模块。
     *
     * @param ownService 当前服务
     * @param roles 目标角色
     * @return 是返回 true
     */
    public boolean isOtherServiceRole(String ownService, ModuleRole... roles) {
        return isServiceModule() && !service.equals(ownService) && hasRole(roles);
    }

    /**
     * 角色是否在给定集合中。
     *
     * @param roles 角色集合
     * @return 是返回 true
     */
    public boolean hasRole(ModuleRole... roles) {
        for (ModuleRole candidate : roles) {
            if (candidate == role) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回 {@code groupId:artifactId}。
     *
     * @return Maven 坐标
     */
    public String coordinate() {
        return groupId + ":" + artifactId;
    }
}
