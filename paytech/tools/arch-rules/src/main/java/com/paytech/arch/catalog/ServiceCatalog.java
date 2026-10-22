package com.paytech.arch.catalog;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 目标架构 V2 的逻辑服务清单（design/03 §1.2）及其层次（design/02 R3）。
 *
 * <p>
 * Maven artifactId 以 {@code paytech-<service>} 开头的模块归属该服务；匹配时取最长服务名，避免前缀相近的服务互相吞并。
 *
 * @author Claude
 */
public final class ServiceCatalog {

    private static final Map<String, ServiceLayer> SERVICES = new LinkedHashMap<>();

    static {
        for (String service : new String[] {"client-master", "commercial", "counterparty", "card", "risk-decision",
            "compliance", "channel-connector", "billing", "account", "ledger", "reconciliation", "tax"}) {
            SERVICES.put(service, ServiceLayer.DOMAIN);
        }
        for (String service : new String[] {"acquiring", "payment", "payout", "dispute", "fx"}) {
            SERVICES.put(service, ServiceLayer.PROCESS);
        }
        for (String service : new String[] {"platform-support", "api-gateway",
            "client-console-bff", "operations-console-bff", "partner-console-bff", "vault"}) {
            SERVICES.put(service, ServiceLayer.SUPPORT);
        }
    }

    private ServiceCatalog() {
    }

    /**
     * 返回全部逻辑服务名。
     *
     * @return 按登记顺序排列的服务名
     */
    public static Set<String> serviceNames() {
        return SERVICES.keySet();
    }

    /**
     * 判断服务名是否已登记。
     *
     * @param serviceName 服务名
     * @return 已登记返回 true
     */
    public static boolean isKnownService(String serviceName) {
        return SERVICES.containsKey(serviceName);
    }

    /**
     * 返回服务层次。
     *
     * @param serviceName 已登记的服务名
     * @return 服务层次
     */
    public static ServiceLayer layerOf(String serviceName) {
        ServiceLayer layer = SERVICES.get(serviceName);
        if (layer == null) {
            throw new IllegalArgumentException("未登记的逻辑服务: " + serviceName);
        }
        return layer;
    }

    /**
     * 按 artifactId 前缀解析所属服务。
     *
     * @param artifactId Maven artifactId
     * @return 所属服务；不属于任何服务时为空
     */
    public static Optional<String> serviceOfArtifact(String artifactId) {
        return SERVICES.keySet().stream()
            .filter(service -> artifactId.equals("paytech-" + service)
                || artifactId.startsWith("paytech-" + service + "-"))
            .max(Comparator.comparingInt(String::length));
    }
}
