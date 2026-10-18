package com.paytech.risk.decision.service.processor;

import java.time.Duration;
import java.util.List;

/**
 * 决策配置写入口共用的资源锁参数
 * <li>等待与租约沿用原服务级注解锁配置 500ms/30s，由 LockCommand 显式提供，不读取注解锁配置。</li>
 * <li>各资源 Key 保持原 decision:* 互斥域，滚动发布期间新旧实例仍对同一资源互斥。</li>
 */
final class DecisionResourceLocks {
    /** 获取资源锁的最长等待时间，超时返回并发冲突。 */
    static final Duration WAIT_TIME = Duration.ofMillis(500);

    /** 资源锁的有限租约，覆盖单次本地事务的 5 秒超时。 */
    static final Duration LEASE_TIME = Duration.ofSeconds(30);

    /** 仅提供锁参数。 */
    private DecisionResourceLocks() { }

    /** 变量根的唯一锁 Key；新建、更新、删除和成员替换必须共用。 */
    static List<String> variableKeys(String variableCode) {
        return List.of("decision:variable:" + variableCode);
    }
}
