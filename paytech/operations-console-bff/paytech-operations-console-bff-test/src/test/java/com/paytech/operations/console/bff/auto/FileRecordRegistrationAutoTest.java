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
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.paytech.operations.console.bff.ext.rest.FileStorageAdminController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Platform 初始化数据块 134（文件记录与存储管理新增路由）及块 130 路径修改与 BFF 存储管理入口一致
 * <li>块 134 行数：权限 2、角色授权 2、菜单 1 与按钮 1、路由 12、路由 Header 56、Query 白名单 11、Guard 72，ID 全部在
 * 900000000000134001—134999。</li>
 * <li>块 130 与块 134 的路由合起来恰好覆盖 FileStorageAdminController 的每个入口一次：外部路径为 /api/operations 加同一下游路径，
 * 绑定合同中的权限；Body 上限与 Query 白名单按入口声明。写入路由编辑与删除的路径带 /{accessLevel}。</li>
 * <li>块 134：权限 ACTIVE、管理员授权 REVOKED、菜单、按钮与路由 DISABLED；Header 与 Guard 按块 130 的读写形态。</li>
 *
 * @see FileStorageAdminController
 * @author Claude
 */
class FileRecordRegistrationAutoTest {
    private static final Path INITIAL_DATA = Path.of("")
        .resolve("../../platform-support/docs/database/sql/platform-support-initial-data.sql").toAbsolutePath().normalize();
    private static final String BLOCK_130_START = "-- Operations file storage console (2026-10-22";
    private static final String BLOCK_134_START = "-- Operations file records and file storage public buckets (2026-10-22";
    private static final long ID_FIRST = 900000000000134001L;
    private static final long ID_LAST = 900000000000134999L;
    private static final String STORAGE_READ = "900000000000130101";
    private static final String STORAGE_MANAGE = "900000000000130102";
    private static final String RECORD_READ = "900000000000134101";
    private static final String RECORD_DOWNLOAD = "900000000000134102";
    private static final Pattern INSERT = Pattern.compile("INSERT INTO (t_\\w+) \\([^)]*\\) VALUES\\n(.*?);\\n", Pattern.DOTALL);
    private static final Pattern VALUE = Pattern.compile("'([^']*)'|([^,\\s][^,]*)");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 块 134 每个路由绑定的权限（tasks/common.md 第 3 节）。 */
    private static final Map<String, String> BLOCK_134_PERMISSIONS = Map.ofEntries(
        Map.entry("operations-file-storage-bucket-update", STORAGE_MANAGE),
        Map.entry("operations-file-scan-policy-list", STORAGE_READ),
        Map.entry("operations-file-scan-policy-detail", STORAGE_READ),
        Map.entry("operations-file-scan-policy-create", STORAGE_MANAGE),
        Map.entry("operations-file-scan-policy-revision-create", STORAGE_MANAGE),
        Map.entry("operations-file-scan-policy-revision-update", STORAGE_MANAGE),
        Map.entry("operations-file-scan-policy-revision-discard", STORAGE_MANAGE),
        Map.entry("operations-file-scan-policy-activate", STORAGE_MANAGE),
        Map.entry("operations-file-scan-policy-retire", STORAGE_MANAGE),
        Map.entry("operations-file-record-list", RECORD_READ),
        Map.entry("operations-file-record-detail", RECORD_READ),
        Map.entry("operations-file-record-download", RECORD_DOWNLOAD));

    private static Map<String, List<List<String>>> block130;
    private static Map<String, List<List<String>>> block134;

    @BeforeAll
    static void readBlocks() throws IOException {
        assertTrue(Files.isRegularFile(INITIAL_DATA), "找不到初始化数据: " + INITIAL_DATA);
        String sql = Files.readString(INITIAL_DATA, StandardCharsets.UTF_8);
        block130 = tables(sql, BLOCK_130_START);
        block134 = tables(sql, BLOCK_134_START);
    }

    /** 块 134 行数与 ID 号段。 */
    @Test
    void test_rowCountsAndIdBlock() {
        assertEquals(Map.of("t_permission", 2, "t_role_permission", 2, "t_console_resource", 2, "t_gateway_route", 12,
            "t_gateway_route_header", 56, "t_gateway_route_query_param", 11, "t_gateway_guard_rule", 72),
            block134.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size())));
        var ids = new HashSet<Long>();
        block134.values().forEach(rows -> rows.forEach(row -> {
            long id = Long.parseLong(row.getFirst());
            assertTrue(id >= ID_FIRST && id <= ID_LAST, "ID 超出块 134: " + id);
            assertTrue(ids.add(id), "ID 重复: " + id);
        }));
    }

    /** 权限 ACTIVE，管理员授权 REVOKED，菜单挂在平台配置下、紧随存储管理，下载按钮挂在菜单下，均 DISABLED。 */
    @Test
    void test_permissionsGrantsAndMenu() {
        assertEquals(List.of(
            List.of(RECORD_READ, "FILE_RECORD_READ", "FILE_RECORD", "READ", "LOW", "ACTIVE"),
            List.of(RECORD_DOWNLOAD, "FILE_RECORD_DOWNLOAD", "FILE_RECORD", "DOWNLOAD", "HIGH", "ACTIVE")),
            block134.get("t_permission").stream().map(row -> row.subList(0, 6)).toList());
        assertEquals(List.of(RECORD_READ, RECORD_DOWNLOAD),
            block134.get("t_role_permission").stream().map(row -> row.get(2)).toList());
        assertTrue(block134.get("t_role_permission").stream().allMatch(row -> row.get(1).equals("@file_record_console_role_id")
            && row.get(3).equals("REVOKED")));
        assertEquals(List.of(
            List.of("OPERATIONS_FILE_RECORDS", "MENU", "900000000000000562", "文件记录", "/file-records", "OPERATIONS_FILE_RECORDS",
                RECORD_READ, "85", "DISABLED"),
            List.of("OPERATIONS_FILE_RECORD_DOWNLOAD", "BUTTON", "900000000000134301", "下载", "NULL", "NULL", RECORD_DOWNLOAD, "10",
                "DISABLED")),
            block134.get("t_console_resource").stream().map(row -> List.of(row.get(1), row.get(3), row.get(4), row.get(5),
                row.get(6), row.get(7), row.get(8), row.get(9), row.get(10))).toList());
    }

    /** 块 130 与块 134 的路由合起来恰好覆盖每个 BFF 入口一次：同路径、合同权限、Body 上限与 Query 白名单。 */
    @Test
    void test_routesMatchBffEntries() {
        Map<String, Method> entries = bffEntries();
        var routes = Stream.concat(block130.get("t_gateway_route").stream(), block134.get("t_gateway_route").stream()).toList();
        assertEquals(entries.keySet(), routes.stream().map(row -> row.get(3) + " " + row.get(5))
            .collect(Collectors.toCollection(TreeSet::new)));
        assertEquals(entries.size(), routes.size(), "每个入口只能有一条路由");
        for (var route : block134.get("t_gateway_route")) {
            String code = route.get(1);
            Method entry = entries.get(route.get(3) + " " + route.get(5));
            boolean body = Arrays.stream(entry.getParameters()).anyMatch(p -> p.isAnnotationPresent(RequestBody.class));
            assertAll(code,
                () -> assertEquals(BLOCK_134_PERMISSIONS.get(code), route.get(9)),
                () -> assertEquals("/api/operations" + route.get(5), route.get(4)),
                () -> assertEquals(List.of("OPERATIONS", "@file_record_console_target_id", "OPERATIONS_JWT", "HUMAN_PERMISSION"),
                    List.of(route.get(2), route.get(6), route.get(7), route.get(8))),
                () -> assertEquals(List.of("NONE", body ? "65536" : "0", "5000", "10000", "DISABLED"), route.subList(13, 18)),
                () -> assertEquals(queryParameters(entry), whitelist(route.getFirst())));
        }
        assertEquals(BLOCK_134_PERMISSIONS.keySet(), block134.get("t_gateway_route").stream().map(row -> row.get(1))
            .collect(Collectors.toSet()));
        var routeEdits = block130.get("t_gateway_route").stream().filter(row -> row.get(1).startsWith("operations-file-storage-route-")
            && !row.get(3).equals("GET") && !row.get(3).equals("POST")).map(row -> row.get(3) + " " + row.get(4)).toList();
        assertEquals(List.of("PUT /api/operations/storage-admin/v1/routes/{sourceSystem}/{accessLevel}",
            "DELETE /api/operations/storage-admin/v1/routes/{sourceSystem}/{accessLevel}"), routeEdits);
    }

    /** Header 按块 130：GET 只有 authorization 与 origin；其余加 content-type 与 idempotency-key（下载无 Body 也声明 JSON）。 */
    @Test
    void test_routeHeadersAndGuards() {
        for (var route : block134.get("t_gateway_route")) {
            boolean read = route.get(3).equals("GET");
            var expected = new TreeSet<>(Set.of("authorization:CORS_REQUEST_ALLOWED", "origin:FORWARD_REQUEST"));
            if (!read) {
                expected.addAll(Set.of("content-type:CORS_REQUEST_ALLOWED", "content-type:FORWARD_REQUEST",
                    "idempotency-key:CORS_REQUEST_ALLOWED", "idempotency-key:FORWARD_REQUEST"));
            }
            assertEquals(expected, block134.get("t_gateway_route_header").stream()
                .filter(row -> row.get(1).equals(route.getFirst())).map(row -> row.get(2) + ":" + row.get(3))
                .collect(Collectors.toCollection(TreeSet::new)), route.get(1));
            assertEquals(List.of("RATE_LIMIT SHARED_ROUTE 300", "CONCURRENCY SHARED_ROUTE 50", "RATE_LIMIT CLIENT_IP 30",
                "CONCURRENCY CLIENT_IP 3", "RATE_LIMIT AUTHENTICATED_PRINCIPAL " + (read ? 30 : 10), "CONCURRENCY AUTHENTICATED_PRINCIPAL 2"),
                block134.get("t_gateway_guard_rule").stream().filter(row -> row.get(1).equals(route.getFirst()))
                    .map(row -> row.get(2) + " " + row.get(3) + " " + (row.get(2).equals("RATE_LIMIT") ? row.get(4) : row.get(7)))
                    .toList(), route.get(1));
        }
    }

    /** 边界清单的每个浏览器请求都带着块 130 或块 134 中同一方法、匹配路径的路由 Code。 */
    @Test
    void test_boundaryRouteCodesRegistered() throws IOException {
        Map<String, List<String>> routes = Stream.concat(block130.get("t_gateway_route").stream(),
            block134.get("t_gateway_route").stream()).collect(Collectors.toMap(row -> row.get(1), row -> row));
        try (var input = getClass().getResourceAsStream("/file-storage-boundary.json")) {
            JsonNode rows = JSON.readTree(input);
            assertEquals(24, rows.size());
            for (JsonNode row : rows) {
                var route = routes.get(row.path("routeCode").asString());
                assertTrue(route != null, row.path("id").asString());
                assertEquals(route.get(3), row.path("method").asString(), row.path("id").asString());
                assertTrue(row.path("path").asString().matches(route.get(5).replace("*", "\\*").replaceAll("\\{[A-Za-z]+}", "[^/]+")),
                    row.path("id").asString());
            }
        }
    }

    /** 某一注册块中各表的行：从块注释起到第一个行首 COMMIT（块头注释中的激活示例不算）。 */
    private static Map<String, List<List<String>>> tables(String sql, String blockStart) {
        int start = sql.indexOf(blockStart);
        assertTrue(start > 0, "缺少注册块: " + blockStart);
        String block = sql.substring(start, sql.indexOf("\nCOMMIT;", start));
        var result = new LinkedHashMap<String, List<List<String>>>();
        Matcher insert = INSERT.matcher(block);
        while (insert.find()) {
            var rows = result.computeIfAbsent(insert.group(1), key -> new ArrayList<>());
            for (String line : insert.group(2).split(",\\n")) {
                rows.add(values(line.trim().replaceFirst("^\\(", "").replaceFirst("\\)$", "")));
            }
        }
        return result;
    }

    /** BFF 存储管理入口：方法 + 完整路径模板到处理方法。 */
    private static Map<String, Method> bffEntries() {
        var entries = new TreeMap<String, Method>();
        String base = AnnotatedElementUtils.findMergedAnnotation(FileStorageAdminController.class, RequestMapping.class).path()[0];
        for (Method method : FileStorageAdminController.class.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping != null) {
                String path = mapping.path().length == 0 ? "" : mapping.path()[0];
                entries.put(mapping.method()[0].name() + " " + base + path, method);
            }
        }
        return entries;
    }

    /** 入口声明的 Query 参数名。 */
    private static Set<String> queryParameters(Method entry) {
        return Arrays.stream(entry.getParameters()).filter(p -> p.isAnnotationPresent(RequestParam.class))
            .map(p -> p.getAnnotation(RequestParam.class).value()).collect(Collectors.toCollection(TreeSet::new));
    }

    /** 块 134 路由的 Query 白名单。 */
    private static Set<String> whitelist(String routeId) {
        return block134.getOrDefault("t_gateway_route_query_param", List.of()).stream()
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
