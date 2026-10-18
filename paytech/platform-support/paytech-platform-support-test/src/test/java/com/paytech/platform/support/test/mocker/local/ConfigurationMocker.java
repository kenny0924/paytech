package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.test.builder.GatewayRouteBuilder;
import com.paytech.platform.support.test.builder.OperationsCatalog;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.ConfigurationTestMapper;
import com.paytech.platform.support.test.dal.ConfigurationTestMapper.Route;

import lombok.RequiredArgsConstructor;

/**
 * 网关、控制台资源与 API Scope 的本地前置数据。
 *
 * <p>这些表由 {@link PlatformDataCleaner} 整表回收，本组件不保存可变状态。
 */
@TestComponent
@RequiredArgsConstructor
public class ConfigurationMocker {

    private final ConfigurationTestMapper mapper;
    private final OperationsMocker operations;

    /** 插入网关目标服务，返回标识。 */
    public long target(String code, String channel, String status) {
        long id = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertTarget(id, code, code, channel, status, operations.now()));
        return id;
    }

    /** Operations BFF 目标。 */
    public long operationsTarget() {
        return target("operations-console-bff", "OPERATIONS", "ACTIVE");
    }

    /** 插入网关路由及可选的 Header、Query 与 Guard。 */
    public Route route(GatewayRouteBuilder builder) {
        Route route = builder.build();
        assertEquals(1, mapper.insertRoute(route, operations.now()));
        return route;
    }

    /** 为路由追加 Header 白名单。 */
    public void header(long routeId, String name, String use) {
        assertEquals(1, mapper.insertRouteHeader(OperatorBuilder.nextId(), routeId, name, use));
    }

    /** 为路由追加 Query 白名单。 */
    public void query(long routeId, String name) {
        assertEquals(1, mapper.insertRouteQuery(OperatorBuilder.nextId(), routeId, name));
    }

    /** 为路由追加限流规则。 */
    public void rateLimit(long routeId, String dimension, long capacity, long refill, long periodMillis) {
        assertEquals(1, mapper.insertGuard(OperatorBuilder.nextId(), routeId, "RATE_LIMIT", dimension, capacity,
            refill, periodMillis, null));
    }

    /** 为路由追加并发规则。 */
    public void concurrency(long routeId, String dimension, long limit) {
        assertEquals(1, mapper.insertGuard(OperatorBuilder.nextId(), routeId, "CONCURRENCY", dimension, null, null,
            null, limit));
    }

    /**
     * 正式目录中每个权限都被 Operations 路由引用，管理员只能委派被引用的权限。
     * 本方法建立 Operations 目标及每个目录权限一条 HUMAN_PERMISSION 路由，返回目标标识。
     */
    public long permissionReferences() {
        long targetId = operationsTarget();
        for (var permission : OperationsCatalog.PERMISSIONS) {
            String code = permission.code().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            route(GatewayRouteBuilder.operations("ref-" + code, targetId, "/ref/" + code).permission(permission.id()));
        }
        return targetId;
    }

    /** 插入 Operations 控制台资源，绑定目录权限。 */
    public long resource(String code, String type, Long parentId, String permissionCode, int sort, String status) {
        long id = OperatorBuilder.nextId();
        boolean menu = "MENU".equals(type);
        assertEquals(1, mapper.insertResource(id, code, "OPERATIONS", type, parentId, code,
            menu ? "/" + code.toLowerCase(java.util.Locale.ROOT) : null, menu ? code : null,
            OperationsCatalog.permission(permissionCode).id(), sort, status, operations.now()));
        return id;
    }

    /** 插入 Client API Scope。 */
    public long apiScope(String code, String name) {
        long id = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertApiScope(id, code, name, null, operations.now()));
        return id;
    }
}
