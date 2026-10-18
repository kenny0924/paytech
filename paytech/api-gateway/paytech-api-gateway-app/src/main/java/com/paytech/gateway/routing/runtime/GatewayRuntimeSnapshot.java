package com.paytech.gateway.routing.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 一次完整编译后的不可变目标索引及路由目录。 */
public record GatewayRuntimeSnapshot(int schemaVersion, Map<String, GatewayRuntimeTarget> targets,
    GatewayRuntimeCatalog catalog) {

    /** 从已绑定目标的路由构造最小目录，保留纯 Java 场景的构造入口。 */
    public GatewayRuntimeSnapshot(int schemaVersion, GatewayRuntimeCatalog catalog) {
        this(schemaVersion, catalog.all().stream().map(GatewayRuntimeRoute::target).distinct()
            .collect(Collectors.toMap(GatewayRuntimeTarget::targetServiceCode, Function.identity())), catalog);
    }

    /** 防御性复制目标索引，使刷新后的请求无法修改已固定的目标。 */
    public GatewayRuntimeSnapshot {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Snapshot Schema Version 必须为正");
        }
        targets = Map.copyOf(Objects.requireNonNull(targets, "Snapshot Target 索引不可为空"));
        catalog = Objects.requireNonNull(catalog, "运行时 Route 目录不可为空");
        for (var entry : targets.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().targetServiceCode())) {
                throw new IllegalArgumentException("Snapshot Target 索引与目标标识不一致");
            }
        }
        for (GatewayRuntimeRoute route : catalog.all()) {
            if (!route.target().equals(targets.get(route.targetServiceCode()))) {
                throw new IllegalArgumentException("Snapshot Route 必须绑定本快照内的目标");
            }
        }
    }

    /** 由目标索引派生诊断总数，避免重复维护统计事实。 */
    public int targetCount() {
        return targets.size();
    }

    /** 返回各渠道的目标数量，只使用已编译的有限渠道维度。 */
    public Map<String, Integer> targetCountsByChannel() {
        return targets.values().stream()
            .collect(Collectors.toUnmodifiableMap(target -> target.channel().getCode(), ignored -> 1, Integer::sum));
    }
}
