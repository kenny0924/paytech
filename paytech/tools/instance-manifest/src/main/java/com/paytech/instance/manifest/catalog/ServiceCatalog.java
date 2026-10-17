package com.paytech.instance.manifest.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * 打包在本 JAR 的服务清单 {@value #RESOURCE}。
 *
 * @author Claude
 */
public final class ServiceCatalog {

    /**
     * 服务清单资源。
     */
    public static final String RESOURCE = "/catalog/services.yaml";

    private final Map<String, ServiceDefinition> services;

    private ServiceCatalog(Map<String, ServiceDefinition> services) {
        this.services = services;
    }

    /**
     * 读取默认服务清单。
     *
     * @return 服务清单
     */
    public static ServiceCatalog loadDefault() {
        try (InputStream input = ServiceCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("类路径缺少服务清单: " + RESOURCE);
            }
            JsonNode root = YAMLMapper.builder().build().readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, ServiceDefinition> services = new LinkedHashMap<>();
            for (JsonNode node : root.path("services").values()) {
                JsonNode database = node.path("database");
                ServiceDefinition definition = new ServiceDefinition(node.path("name").asString(),
                    node.path("applicationName").asString(), node.path("port").asInt(),
                    database.isNull() || database.isMissingNode() ? null : database.asString(),
                    node.path("source").asString());
                services.put(definition.name(), definition);
            }
            return new ServiceCatalog(services);
        } catch (IOException exception) {
            throw new UncheckedIOException("服务清单读取失败: " + RESOURCE, exception);
        }
    }

    /**
     * 按名称查找服务。
     *
     * @param name 服务名
     * @return 服务定义
     */
    public Optional<ServiceDefinition> find(String name) {
        return Optional.ofNullable(services.get(name));
    }

    /**
     * 全部服务名。
     *
     * @return 服务名
     */
    public Set<String> names() {
        return services.keySet();
    }

    /**
     * 全部服务定义。
     *
     * @return 按登记顺序排列
     */
    public List<ServiceDefinition> all() {
        return List.copyOf(services.values());
    }
}
