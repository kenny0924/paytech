package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.paytech.operations.console.bff.ext.rest.DataQueryExecutionController;
import com.paytech.operations.console.bff.ext.rest.DataQueryManagementController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Platform 初始化数据块 131（数据查询控制台）与 BFF 已部署入口一致
 * <li>行数：权限 4、角色授权 4、菜单 3 与按钮 2、路由 18、路由 Header 82、Query 白名单 13、Guard 108，ID 全部在 900000000000131001—131999。</li>
 * <li>每个 BFF 数据查询入口恰好一条路由：外部路径为 /api/operations 加同一下游路径，绑定合同中的权限；Query 白名单等于入口声明的
 * Query 参数；Header 与 Guard 按块 125 的读写形态；路由 Code 与边界清单一致。</li>
 * <li>权限 ACTIVE、管理员授权 REVOKED、菜单与路由 DISABLED。</li>
 *
 * @see DataQueryManagementController
 * @see DataQueryExecutionController
 * @author Claude
 */
class DataQueryRegistrationAutoTest {
    private static final Path INITIAL_DATA = Path.of("")
        .resolve("../../platform-support/docs/database/sql/platform-support-initial-data.sql").toAbsolutePath().normalize();
    private static final String BLOCK_START = "-- Operations data query console (2026-10-22";
    private static final long ID_FIRST = 900000000000131001L;
    private static final long ID_LAST = 900000000000131999L;
    private static final String READ = "900000000000131101";
    private static final String SOURCE_MANAGE = "900000000000131102";
    private static final String TEMPLATE_MANAGE = "900000000000131103";
    private static final String EXECUTE = "900000000000131104";
    private static final Pattern INSERT = Pattern.compile("INSERT INTO (t_\\w+) \\([^)]*\\) VALUES\\n(.*?);\\n", Pattern.DOTALL);
    private static final Pattern VALUE = Pattern.compile("'([^']*)'|([^,\\s][^,]*)");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 合同中每个路由绑定的权限（technique.md 第 7 节）。 */
    private static final Map<String, String> ROUTE_PERMISSIONS = Map.ofEntries(
        Map.entry("operations-data-source-list", READ),
        Map.entry("operations-data-source-detail", READ),
        Map.entry("operations-data-source-create", SOURCE_MANAGE),
        Map.entry("operations-data-source-update", SOURCE_MANAGE),
        Map.entry("operations-data-source-connection-test", SOURCE_MANAGE),
        Map.entry("operations-data-source-status", SOURCE_MANAGE),
        Map.entry("operations-data-source-delete", SOURCE_MANAGE),
        Map.entry("operations-query-template-list", READ),
        Map.entry("operations-query-template-detail", READ),
        Map.entry("operations-query-template-create", TEMPLATE_MANAGE),
        Map.entry("operations-query-template-update", TEMPLATE_MANAGE),
        Map.entry("operations-query-template-status", TEMPLATE_MANAGE),
        Map.entry("operations-query-template-version-list", READ),
        Map.entry("operations-query-template-version-detail", READ),
        Map.entry("operations-query-template-restore", TEMPLATE_MANAGE),
        Map.entry("operations-query-template-validate", TEMPLATE_MANAGE),
        Map.entry("operations-query-template-trial", TEMPLATE_MANAGE),
        Map.entry("operations-data-query-execute", EXECUTE));

    private static Map<String, List<List<String>>> tables;

    @BeforeAll
    static void readBlock() throws IOException {
        assertTrue(Files.isRegularFile(INITIAL_DATA), "找不到初始化数据: " + INITIAL_DATA);
        String sql = Files.readString(INITIAL_DATA, StandardCharsets.UTF_8);
        int start = sql.indexOf(BLOCK_START);
        assertTrue(start > 0, "缺少块 131");
        String block = sql.substring(start, sql.indexOf("COMMIT;", start));
        tables = new LinkedHashMap<>();
        Matcher insert = INSERT.matcher(block);
        while (insert.find()) {
            var rows = tables.computeIfAbsent(insert.group(1), key -> new ArrayList<>());
            for (String line : insert.group(2).split(",\\n")) {
                rows.add(values(line.trim().replaceFirst("^\\(", "").replaceFirst("\\)$", "")));
            }
        }
    }

    /** 行数与 ID 号段。 */
    @Test
    void test_rowCountsAndIdBlock() {
        assertEquals(Map.of("t_permission", 4, "t_role_permission", 4, "t_console_resource", 5, "t_gateway_route", 18,
            "t_gateway_route_header", 82, "t_gateway_route_query_param", 13, "t_gateway_guard_rule", 108),
            tables.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size())));
        var ids = new HashSet<Long>();
        tables.values().forEach(rows -> rows.forEach(row -> {
            long id = Long.parseLong(row.getFirst());
            assertTrue(id >= ID_FIRST && id <= ID_LAST, "ID 超出块 131: " + id);
            assertTrue(ids.add(id), "ID 重复: " + id);
        }));
    }

    /** 权限 ACTIVE，管理员授权 REVOKED，菜单与维护按钮 DISABLED，按钮挂在对应菜单下并绑定维护权限。 */
    @Test
    void test_permissionsGrantsAndMenus() {
        assertEquals(List.of(
            List.of(READ, "DATA_QUERY_READ", "DATA_QUERY", "READ", "LOW", "ACTIVE"),
            List.of(SOURCE_MANAGE, "DATA_SOURCE_MANAGE", "DATA_SOURCE", "MANAGE", "HIGH", "ACTIVE"),
            List.of(TEMPLATE_MANAGE, "DATA_QUERY_TEMPLATE_MANAGE", "DATA_QUERY_TEMPLATE", "MANAGE", "HIGH", "ACTIVE"),
            List.of(EXECUTE, "DATA_QUERY_EXECUTE", "DATA_QUERY", "EXECUTE", "LOW", "ACTIVE")),
            tables.get("t_permission").stream().map(row -> row.subList(0, 6)).toList());
        assertEquals(List.of(READ, SOURCE_MANAGE, TEMPLATE_MANAGE, EXECUTE),
            tables.get("t_role_permission").stream().map(row -> row.get(2)).toList());
        assertTrue(tables.get("t_role_permission").stream().allMatch(row -> row.get(1).equals("@data_query_console_role_id")
            && row.get(3).equals("REVOKED")));
        assertEquals(List.of(
            List.of("OPERATIONS_DATA_QUERY", "MENU", "NULL", "数据查询", "NULL", "NULL", READ, "DISABLED"),
            List.of("OPERATIONS_DATA_SOURCES", "MENU", "900000000000131301", "数据源", "/data-query/data-sources",
                "OPERATIONS_DATA_SOURCES", READ, "DISABLED"),
            List.of("OPERATIONS_QUERY_TEMPLATES", "MENU", "900000000000131301", "查询模板", "/data-query/templates",
                "OPERATIONS_QUERY_TEMPLATES", READ, "DISABLED"),
            List.of("OPERATIONS_DATA_SOURCE_MANAGE", "BUTTON", "900000000000131302", "维护", "NULL", "NULL",
                SOURCE_MANAGE, "DISABLED"),
            List.of("OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE", "BUTTON", "900000000000131303", "维护", "NULL", "NULL",
                TEMPLATE_MANAGE, "DISABLED")),
            tables.get("t_console_resource").stream().map(row -> List.of(row.get(1), row.get(3), row.get(4), row.get(5),
                row.get(6), row.get(7), row.get(8), row.get(10))).toList());
    }

    /** 每个 BFF 入口恰好一条 DISABLED 路由：同路径、合同权限、读写对应的 Body 上限与 Query 白名单。 */
    @Test
    void test_routesMatchBffEntries() {
        Map<String, Method> entries = bffEntries();
        var routes = tables.get("t_gateway_route");
        assertEquals(entries.keySet(), routes.stream().map(row -> row.get(3) + " " + row.get(5))
            .collect(Collectors.toCollection(TreeSet::new)));
        for (var route : routes) {
            String code = route.get(1);
            Method entry = entries.get(route.get(3) + " " + route.get(5));
            boolean body = Arrays.stream(entry.getParameters()).anyMatch(p -> p.isAnnotationPresent(RequestBody.class));
            assertAll(code,
                () -> assertEquals(ROUTE_PERMISSIONS.get(code), route.get(9)),
                () -> assertEquals("/api/operations" + route.get(5), route.get(4)),
                () -> assertEquals(List.of("OPERATIONS", "@data_query_console_target_id", "OPERATIONS_JWT",
                    "HUMAN_PERMISSION"), List.of(route.get(2), route.get(6), route.get(7), route.get(8))),
                () -> assertEquals(List.of("NONE", body ? "65536" : "0", "5000", "10000", "DISABLED"),
                    route.subList(13, 18)),
                () -> assertEquals(queryParameters(entry), whitelist(route.getFirst())));
        }
        assertEquals(ROUTE_PERMISSIONS.keySet(), routes.stream().map(row -> row.get(1)).collect(Collectors.toSet()));
    }

    /** Header 按块 125：GET 只有 authorization 与 origin；有 Body 加 content-type；非 GET 加 idempotency-key。 */
    @Test
    void test_routeHeadersAndGuards() {
        for (var route : tables.get("t_gateway_route")) {
            String method = route.get(3);
            var expected = new TreeSet<>(Set.of("authorization:CORS_REQUEST_ALLOWED", "origin:FORWARD_REQUEST"));
            if (method.equals("POST") || method.equals("PUT")) {
                expected.addAll(Set.of("content-type:CORS_REQUEST_ALLOWED", "content-type:FORWARD_REQUEST"));
            }
            if (!method.equals("GET")) {
                expected.addAll(Set.of("idempotency-key:CORS_REQUEST_ALLOWED", "idempotency-key:FORWARD_REQUEST"));
            }
            assertEquals(expected, tables.get("t_gateway_route_header").stream()
                .filter(row -> row.get(1).equals(route.getFirst())).map(row -> row.get(2) + ":" + row.get(3))
                .collect(Collectors.toCollection(TreeSet::new)), route.get(1));
            assertEquals(List.of("RATE_LIMIT SHARED_ROUTE", "CONCURRENCY SHARED_ROUTE", "RATE_LIMIT CLIENT_IP",
                "CONCURRENCY CLIENT_IP", "RATE_LIMIT AUTHENTICATED_PRINCIPAL", "CONCURRENCY AUTHENTICATED_PRINCIPAL"),
                tables.get("t_gateway_guard_rule").stream().filter(row -> row.get(1).equals(route.getFirst()))
                    .map(row -> row.get(2) + " " + row.get(3)).toList(), route.get(1));
        }
    }

    /** 边界清单的每个浏览器请求都带着本块中同一方法、匹配路径的路由 Code。 */
    @Test
    void test_boundaryRouteCodesRegistered() throws IOException {
        Map<String, List<String>> routes = tables.get("t_gateway_route").stream()
            .collect(Collectors.toMap(row -> row.get(1), row -> row));
        try (var input = getClass().getResourceAsStream("/data-query-boundary.json")) {
            JsonNode rows = JSON.readTree(input);
            assertEquals(18, rows.size());
            for (JsonNode row : rows) {
                var route = routes.get(row.path("routeCode").asString());
                assertTrue(route != null, row.path("id").asString());
                assertEquals(route.get(3), row.path("method").asString(), row.path("id").asString());
                assertTrue(row.path("path").asString().matches(route.get(5).replaceAll("\\{[A-Za-z]+}", "[^/]+")),
                    row.path("id").asString());
            }
        }
    }

    /** BFF 数据查询入口：方法 + 完整路径模板到处理方法。 */
    private static Map<String, Method> bffEntries() {
        var entries = new LinkedHashMap<String, Method>();
        for (Class<?> controller : List.of(DataQueryManagementController.class, DataQueryExecutionController.class)) {
            String base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class).path()[0];
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping != null) {
                    String path = mapping.path().length == 0 ? "" : mapping.path()[0];
                    entries.put(mapping.method()[0].name() + " " + base + path, method);
                }
            }
        }
        return new java.util.TreeMap<>(entries);
    }

    /** 入口声明的 Query 参数名。 */
    private static Set<String> queryParameters(Method entry) {
        return Arrays.stream(entry.getParameters()).filter(p -> p.isAnnotationPresent(RequestParam.class))
            .map(p -> p.getAnnotation(RequestParam.class).value()).collect(Collectors.toCollection(TreeSet::new));
    }

    /** 路由的 Query 白名单。 */
    private static Set<String> whitelist(String routeId) {
        return tables.getOrDefault("t_gateway_route_query_param", List.of()).stream()
            .filter(row -> row.get(1).equals(routeId)).map(row -> row.get(2))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 一行 VALUES 的字段：引号内取原文，其余去除空白。 */
    private static List<String> values(String row) {
        var result = new ArrayList<String>();
        Matcher value = VALUE.matcher(row);
        while (value.find()) {
            result.add(value.group(1) != null ? value.group(1) : value.group(2).trim());
        }
        return result;
    }
}
