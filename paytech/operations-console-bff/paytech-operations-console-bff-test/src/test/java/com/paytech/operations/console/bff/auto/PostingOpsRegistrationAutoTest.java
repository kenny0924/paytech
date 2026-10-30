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
import java.util.TreeMap;
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

import com.paytech.operations.console.bff.ext.rest.FinancePostingOpsController;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Platform 初始化数据块 148（明细账运维）与 BFF 入口一致
 * <li>行数：权限 1、角色授权 1、菜单 1 与按钮 2、路由 4、路由 Header 16、Query 白名单 2、Guard 24，ID 全部在 900000000000148001—148999。</li>
 * <li>4 条路由恰好覆盖 FinancePostingOpsController 的每个入口一次：外部路径为 /api/operations 加同一下游路径；查看绑定
 * FINANCE_ACCOUNTING_READ，追平与重建绑定新权限 FINANCE_SUBLEDGER_MANAGE；Body 上限与 Query 白名单按入口声明。</li>
 * <li>权限 ACTIVE、管理员授权 REVOKED、菜单、按钮与路由 DISABLED；写路由的单账号限流收紧为 10 / 分钟、并发 1。</li>
 *
 * @see FinancePostingOpsController
 * @author Claude
 */
class PostingOpsRegistrationAutoTest {
    private static final Path INITIAL_DATA = Path.of("")
        .resolve("../../platform-support/docs/database/sql/platform-support-initial-data.sql").toAbsolutePath().normalize();
    private static final Path CAPABILITIES = Path.of("").resolve(
        "../../platform-support/paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json")
        .toAbsolutePath().normalize();
    private static final String BLOCK_START = "-- Operations finance posting operations console (2026-10-30";
    private static final long ID_FIRST = 900000000000148001L;
    private static final long ID_LAST = 900000000000148999L;
    private static final String READ = PlatformOperationsSimulator.FINANCE_READ_PERMISSION_ID;
    private static final String MANAGE = PlatformOperationsSimulator.FINANCE_SUBLEDGER_MANAGE_PERMISSION_ID;
    private static final Pattern INSERT = Pattern.compile("INSERT INTO (t_\\w+) \\([^)]*\\) VALUES\\n(.*?);\\n", Pattern.DOTALL);
    private static final Pattern VALUE = Pattern.compile("'([^']*)'|([^,\\s][^,]*)");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Map<String, List<List<String>>> block;

    @BeforeAll
    static void readBlock() throws IOException {
        assertTrue(Files.isRegularFile(INITIAL_DATA), "找不到初始化数据: " + INITIAL_DATA);
        block = tables(Files.readString(INITIAL_DATA, StandardCharsets.UTF_8));
    }

    /** 行数与 ID 号段。 */
    @Test
    void test_rowCountsAndIdBlock() {
        assertEquals(Map.of("t_permission", 1, "t_role_permission", 1, "t_console_resource", 3, "t_gateway_route", 4,
            "t_gateway_route_header", 16, "t_gateway_route_query_param", 2, "t_gateway_guard_rule", 24),
            block.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size())));
        var ids = new HashSet<Long>();
        block.values().forEach(rows -> rows.forEach(row -> {
            long id = Long.parseLong(row.getFirst());
            assertTrue(id >= ID_FIRST && id <= ID_LAST, "ID 超出块 148: " + id);
            assertTrue(ids.add(id), "ID 重复: " + id);
        }));
    }

    /** 权限 ACTIVE，管理员授权 REVOKED；菜单挂在财务下排在明细账查询之后，两个按钮挂在菜单下，均 DISABLED。 */
    @Test
    void test_permissionGrantAndResources() {
        assertEquals(List.of(MANAGE, "FINANCE_SUBLEDGER_MANAGE", "FINANCE_SUBLEDGER", "MANAGE", "HIGH", "ACTIVE"),
            block.get("t_permission").getFirst().subList(0, 6));
        var grant = block.get("t_role_permission").getFirst();
        assertEquals(List.of("@b148_role_id", MANAGE, "REVOKED"), List.of(grant.get(1), grant.get(2), grant.get(3)));
        assertEquals(List.of(
            List.of("OPERATIONS_FINANCE_POSTING_OPS", "MENU", "@b148_finance_menu_id", "明细账运维", "/finance/posting-ops",
                "OPERATIONS_FINANCE_POSTING_OPS", READ, "45", "DISABLED"),
            List.of("OPERATIONS_FINANCE_POSTING_OPS_CATCH_UP", "BUTTON", "900000000000148301", "追平", "NULL", "NULL", MANAGE, "10",
                "DISABLED"),
            List.of("OPERATIONS_FINANCE_POSTING_OPS_REBUILD", "BUTTON", "900000000000148301", "重建余额", "NULL", "NULL", MANAGE, "20",
                "DISABLED")),
            block.get("t_console_resource").stream().map(row -> List.of(row.get(1), row.get(3), row.get(4), row.get(5),
                row.get(6), row.get(7), row.get(8), row.get(9), row.get(10))).toList());
    }

    /** 能力清单登记了菜单与两个按钮动作，与资源编码一致。 */
    @Test
    void test_capabilitiesRegistered() throws IOException {
        var matches = new ArrayList<JsonNode>();
        JSON.readTree(Files.readString(CAPABILITIES, StandardCharsets.UTF_8)).forEach(item -> {
            if ("OPERATIONS_FINANCE_POSTING_OPS".equals(item.path("componentKey").asString())) {
                matches.add(item);
            }
        });
        assertEquals(1, matches.size(), "能力清单应恰好登记一次 OPERATIONS_FINANCE_POSTING_OPS");
        JsonNode entry = matches.getFirst();
        var actions = new ArrayList<String>();
        entry.path("actions").forEach(action -> actions.add(action.path("resourceCode").asString()));
        assertAll(() -> assertEquals("/finance/posting-ops", entry.path("frontendRoute").asString()),
            () -> assertEquals(List.of("OPERATIONS_FINANCE_POSTING_OPS_CATCH_UP", "OPERATIONS_FINANCE_POSTING_OPS_REBUILD"), actions));
    }

    /** 路由恰好覆盖每个 BFF 入口一次：同路径、权限、Body 上限、Query 白名单，且与 Platform Simulator 的路由权限一致。 */
    @Test
    void test_routesMatchBffEntries() {
        Map<String, Method> entries = bffEntries();
        var routes = block.get("t_gateway_route");
        assertEquals(entries.keySet(), routes.stream().map(row -> row.get(3) + " " + row.get(5))
            .collect(Collectors.toCollection(TreeSet::new)));
        assertEquals(entries.size(), routes.size(), "每个入口只能有一条路由");
        for (var route : routes) {
            String code = route.get(1);
            Method entry = entries.get(route.get(3) + " " + route.get(5));
            boolean body = Arrays.stream(entry.getParameters()).anyMatch(p -> p.isAnnotationPresent(RequestBody.class));
            boolean read = route.get(3).equals("GET");
            assertAll(code,
                () -> assertEquals(read ? READ : MANAGE, route.get(9)),
                () -> assertEquals(PlatformOperationsSimulator.FINANCE_ROUTE_PERMISSIONS.get(code), route.get(9)),
                () -> assertEquals("/api/operations" + route.get(5), route.get(4)),
                () -> assertEquals(List.of("OPERATIONS", "@b148_target_id", "OPERATIONS_JWT", "HUMAN_PERMISSION"),
                    List.of(route.get(2), route.get(6), route.get(7), route.get(8))),
                () -> assertEquals(List.of("NONE", body ? "1024" : "0", "5000", "10000", "DISABLED"), route.subList(13, 18)),
                () -> assertEquals(queryParameters(entry), whitelist(route.getFirst())));
        }
    }

    /** Header：GET 只有 authorization 与 origin，写路由再加 content-type 与 idempotency-key；Guard：写路由单账号 10 / 分钟、并发 1。 */
    @Test
    void test_routeHeadersAndGuards() {
        for (var route : block.get("t_gateway_route")) {
            boolean read = route.get(3).equals("GET");
            var expected = new TreeSet<>(Set.of("authorization:CORS_REQUEST_ALLOWED", "origin:FORWARD_REQUEST"));
            if (!read) {
                expected.addAll(Set.of("content-type:CORS_REQUEST_ALLOWED", "content-type:FORWARD_REQUEST",
                    "idempotency-key:CORS_REQUEST_ALLOWED", "idempotency-key:FORWARD_REQUEST"));
            }
            assertEquals(expected, block.get("t_gateway_route_header").stream()
                .filter(row -> row.get(1).equals(route.getFirst())).map(row -> row.get(2) + ":" + row.get(3))
                .collect(Collectors.toCollection(TreeSet::new)), route.get(1));
            assertEquals(List.of("RATE_LIMIT SHARED_ROUTE 300", "CONCURRENCY SHARED_ROUTE 50", "RATE_LIMIT CLIENT_IP 30",
                "CONCURRENCY CLIENT_IP 3", "RATE_LIMIT AUTHENTICATED_PRINCIPAL " + (read ? 30 : 10),
                "CONCURRENCY AUTHENTICATED_PRINCIPAL " + (read ? 2 : 1)),
                block.get("t_gateway_guard_rule").stream().filter(row -> row.get(1).equals(route.getFirst()))
                    .map(row -> row.get(2) + " " + row.get(3) + " " + (row.get(2).equals("RATE_LIMIT") ? row.get(4) : row.get(7)))
                    .toList(), route.get(1));
        }
    }

    /** 边界清单的每个浏览器请求都带着块 148 中同一方法、匹配路径的路由 Code。 */
    @Test
    void test_boundaryRouteCodesRegistered() throws IOException {
        Map<String, List<String>> routes = block.get("t_gateway_route").stream()
            .collect(Collectors.toMap(row -> row.get(1), row -> row));
        try (var input = getClass().getResourceAsStream("/finance-posting-ops-boundary.json")) {
            JsonNode rows = JSON.readTree(input);
            assertEquals(4, rows.size());
            for (JsonNode row : rows) {
                var route = routes.get(row.path("routeCode").asString());
                assertTrue(route != null, row.path("id").asString());
                assertEquals(route.get(3), row.path("method").asString(), row.path("id").asString());
                assertTrue(row.path("path").asString().matches(route.get(5).replaceAll("\\{[A-Za-z]+}", "[^/]+")),
                    row.path("id").asString());
            }
        }
    }

    /** 块 148 中各表的行：从块注释起到第一个行首 COMMIT。 */
    private static Map<String, List<List<String>>> tables(String sql) {
        int start = sql.indexOf(BLOCK_START);
        assertTrue(start > 0, "缺少注册块: " + BLOCK_START);
        String text = sql.substring(start, sql.indexOf("\nCOMMIT;", start));
        var result = new LinkedHashMap<String, List<List<String>>>();
        Matcher insert = INSERT.matcher(text);
        while (insert.find()) {
            var rows = result.computeIfAbsent(insert.group(1), key -> new ArrayList<>());
            for (String line : insert.group(2).split(",\\n")) {
                rows.add(values(line.trim().replaceFirst("^\\(", "").replaceFirst("\\)$", "")));
            }
        }
        return result;
    }

    /** BFF 明细账运维入口：方法 + 完整路径模板到处理方法。 */
    private static Map<String, Method> bffEntries() {
        var entries = new TreeMap<String, Method>();
        String base = AnnotatedElementUtils.findMergedAnnotation(FinancePostingOpsController.class, RequestMapping.class).path()[0];
        for (Method method : FinancePostingOpsController.class.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping != null) {
                entries.put(mapping.method()[0].name() + " " + base + mapping.path()[0], method);
            }
        }
        return entries;
    }

    /** 入口声明的 Query 参数名。 */
    private static Set<String> queryParameters(Method entry) {
        return Arrays.stream(entry.getParameters()).filter(p -> p.isAnnotationPresent(RequestParam.class))
            .map(p -> p.getAnnotation(RequestParam.class).value()).collect(Collectors.toCollection(TreeSet::new));
    }

    /** 路由的 Query 白名单。 */
    private static Set<String> whitelist(String routeId) {
        return block.getOrDefault("t_gateway_route_query_param", List.of()).stream()
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
