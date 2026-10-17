package com.paytech.platform.support.api.gatewayconfiguration.response;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 全局当前有效 Gateway 配置快照。 */
public final class GatewayPublicSnapshotResponseBody {

    /**
     * 快照协议版本。
     * <li>当前固定为 1，不表示数据库行版本或配置发布版本。</li>
     */
    private final int schemaVersion;

    /**
     * 当前 ACTIVE Target 集合。
     * <li>按目标编码稳定排序；已初始化目录全部停用时允许为空。</li>
     */
    private final List<GatewayTargetSnapshot> targets;

    /**
     * 当前有效 Route 及子配置。
     * <li>仅包含自身和所属 Target 均为 ACTIVE 的路由，按路由编码稳定排序。</li>
     * <li>空集合表示关闭全部公共路由，消费者必须接受该完整快照。</li>
     */
    private final List<GatewayRouteSnapshot> routes;

    /** 绑定快照字段，集合使用不可变副本，保留完整快照的值语义。 */
    @JsonCreator
    public GatewayPublicSnapshotResponseBody(@JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("targets") List<GatewayTargetSnapshot> targets,
        @JsonProperty("routes") List<GatewayRouteSnapshot> routes) {
        this.schemaVersion = schemaVersion;
        this.targets = List.copyOf(targets);
        this.routes = List.copyOf(routes);
    }

    /** 返回快照协议版本。 */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** 返回当前 ACTIVE Target 集合。 */
    public List<GatewayTargetSnapshot> getTargets() {
        return targets;
    }

    /** 返回当前有效 Route 及子配置。 */
    public List<GatewayRouteSnapshot> getRoutes() {
        return routes;
    }

    /** 比较完整快照字段，保持原契约的值相等语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GatewayPublicSnapshotResponseBody value)) {
            return false;
        }
        return schemaVersion == value.schemaVersion
            && Objects.equals(targets, value.targets)
            && Objects.equals(routes, value.routes);
    }

    /** 根据完整快照字段生成与值相等语义一致的哈希值。 */
    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, targets, routes);
    }
}
