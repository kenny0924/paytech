package com.paytech.platform.support.service.processor;

import java.time.Duration;
import java.util.List;

/**
 * Gateway 配置写入口共用的资源锁参数
 * <li>等待与租约沿用本服务注解锁配置 500ms / 30s，由 LockCommand 显式提供，不读取注解锁配置。</li>
 * <li>Key 保持原注解锁的完整逻辑 Key {@code gateway-configuration:catalog}：
 * Target、Route 的创建、更新与启停共用整个 Catalog 互斥域，滚动发布期间新旧实例仍互斥。</li>
 */
final class GatewayConfigurationLocks {

    /** 获取资源锁的最长等待时间，超时返回并发冲突。 */
    static final Duration WAIT_TIME = Duration.ofMillis(500);

    /** 资源锁的有限租约，覆盖一次 SERIALIZABLE 配置事务。 */
    static final Duration LEASE_TIME = Duration.ofSeconds(30);

    /** Catalog 全部写入口共用的唯一锁 Key。 */
    static final List<String> CATALOG_KEYS = List.of("gateway-configuration:catalog");

    /** 仅提供锁参数。 */
    private GatewayConfigurationLocks() { }
}
