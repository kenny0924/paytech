package com.paytech.arch.ownership;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * 表归属白名单：{@code services.<service>.<database> = [tables]}，由 design/03 生成（见
 * {@code scripts/generate-table-ownership.mjs}）。
 * <p>
 * {@code transitionalSharedReads} 登记共库过渡期服务可只读引用的他方表（如 account 只读 ledger 的账户表），不计入物理表数。
 *
 * @author Claude
 */
public final class TableOwnership {

    /**
     * 打包在本 JAR 中的默认白名单资源。
     */
    public static final String DEFAULT_RESOURCE = "/table-ownership.yaml";

    private final Map<String, Map<String, Set<String>>> tablesByService;

    private final Set<String> sharedInfrastructureTables;

    private final Map<String, Map<String, Set<String>>> transitionalReadsByService;

    private TableOwnership(Map<String, Map<String, Set<String>>> tablesByService,
        Set<String> sharedInfrastructureTables, Map<String, Map<String, Set<String>>> transitionalReadsByService) {
        this.tablesByService = tablesByService;
        this.sharedInfrastructureTables = sharedInfrastructureTables;
        this.transitionalReadsByService = transitionalReadsByService;
    }

    /**
     * 读取本 JAR 内的默认白名单。
     *
     * @return 表归属
     */
    public static TableOwnership loadDefault() {
        try (InputStream input = TableOwnership.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("类路径缺少表归属白名单: " + DEFAULT_RESOURCE);
            }
            return parse(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("表归属白名单读取失败: " + DEFAULT_RESOURCE, exception);
        }
    }

    /**
     * 读取指定文件的白名单。
     *
     * @param file YAML 文件
     * @return 表归属
     */
    public static TableOwnership load(Path file) {
        try {
            return parse(Files.readString(file));
        } catch (IOException exception) {
            throw new UncheckedIOException("表归属白名单读取失败: " + file, exception);
        }
    }

    private static TableOwnership parse(String yaml) {
        JsonNode root = YAMLMapper.builder().build().readTree(yaml);
        Set<String> shared = new LinkedHashSet<>();
        root.path("sharedInfrastructureTables").values().forEach(table -> shared.add(table.asString()));
        return new TableOwnership(parseServices(root.path("services")), Set.copyOf(shared),
            parseServices(root.path("transitionalSharedReads")));
    }

    private static Map<String, Map<String, Set<String>>> parseServices(JsonNode node) {
        Map<String, Map<String, Set<String>>> services = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> service : node.properties()) {
            Map<String, Set<String>> databases = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> database : service.getValue().properties()) {
                Set<String> tables = new TreeSet<>();
                database.getValue().values().forEach(table -> tables.add(table.asString()));
                databases.put(database.getKey(), Set.copyOf(tables));
            }
            services.put(service.getKey(), Map.copyOf(databases));
        }
        return Map.copyOf(services);
    }

    /**
     * 服务允许在 Mapper XML 中引用的表：本服务各库的表、每库共有的基础设施表与共库过渡期登记的只读表。
     *
     * @param service 服务名
     * @return 允许引用的表；服务没有数据库时为空集合
     */
    public Set<String> allowedTables(String service) {
        Map<String, Set<String>> databases = tablesByService.get(service);
        if (databases == null) {
            return Set.of();
        }
        Set<String> allowed = new TreeSet<>(sharedInfrastructureTables);
        databases.values().forEach(allowed::addAll);
        transitionalReadsOf(service).values().forEach(allowed::addAll);
        return allowed;
    }

    /**
     * 共库过渡期服务可只读引用、但不归其所有的其他服务表（不计入物理表数）。
     *
     * @param service 服务名
     * @return {@code database -> tables}；未登记时为空
     */
    public Map<String, Set<String>> transitionalReadsOf(String service) {
        return transitionalReadsByService.getOrDefault(service, Map.of());
    }

    /**
     * 服务在各库拥有的表。
     *
     * @param service 服务名
     * @return {@code database -> tables}；未登记时为空
     */
    public Map<String, Set<String>> databasesOf(String service) {
        return tablesByService.getOrDefault(service, Map.of());
    }

    /**
     * 登记了归属的服务名。
     *
     * @return 服务名
     */
    public Set<String> services() {
        return tablesByService.keySet();
    }

    /**
     * 每库共有的基础设施表。
     *
     * @return 表名
     */
    public Set<String> sharedInfrastructureTables() {
        return sharedInfrastructureTables;
    }

    /**
     * 物理表总数（复制表与每库一份的表按库分别计数）。
     *
     * @return 表数
     */
    public int physicalTableCount() {
        return tablesByService.values().stream()
            .flatMap(databases -> databases.values().stream())
            .mapToInt(Set::size)
            .sum();
    }

    /**
     * 卡域受保护表：card 服务拥有的业务表（不含每库一份的基础设施表）。
     *
     * @return 表名
     */
    public List<String> cardTables() {
        Set<String> tables = new TreeSet<>();
        databasesOf("card").values().forEach(tables::addAll);
        tables.remove("t_compensation_event");
        tables.removeAll(sharedInfrastructureTables);
        return List.copyOf(tables);
    }

}
