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

import com.paytech.operations.console.bff.ext.rest.FileUploadController;

/**
 * Platform 初始化数据块 136（运营控制台文件上传入口）与 BFF 文件上传入口一致
 * <li>行数：权限 2、角色授权 2、菜单 1 与按钮 1、路由 5、路由 Header 18、Query 白名单 5、Guard 30，ID 全部在
 * 900000000000136001—136999。</li>
 * <li>路由恰好覆盖 FileUploadController 的每个入口一次：外部路径为 /api/operations 加同一下游路径；GET 绑定 FILE_UPLOAD_READ，
 * POST 绑定 FILE_UPLOAD_CREATE；Body 上限与 Query 白名单按入口声明；Route Code 与浏览器测试使用的一致。</li>
 * <li>权限 ACTIVE、管理员授权 REVOKED、菜单、按钮与路由 DISABLED；Header 与 Guard 按块 134 的读写形态，均不超过 Gateway 编译器上限
 * （600/min、并发 50）。</li>
 *
 * @see FileUploadController
 * @author Claude
 */
class FileUploadRegistrationAutoTest {
    private static final Path INITIAL_DATA = Path.of("")
        .resolve("../../platform-support/docs/database/sql/platform-support-initial-data.sql").toAbsolutePath().normalize();
    private static final String BLOCK_136_START = "-- Operations file upload entry (2026-10-24";
    private static final long ID_FIRST = 900000000000136001L;
    private static final long ID_LAST = 900000000000136999L;
    private static final String UPLOAD_READ = "900000000000136101";
    private static final String UPLOAD_CREATE = "900000000000136102";
    private static final Pattern INSERT = Pattern.compile("INSERT INTO (t_\\w+) \\([^)]*\\) VALUES\\n(.*?);\\n", Pattern.DOTALL);
    private static final Pattern VALUE = Pattern.compile("'([^']*)'|([^,\\s][^,]*)");

    /** 每个入口的 Route Code（与 FileUploadBrowserAutoTest 发送的 X-Gateway-Route-Code 相同）。 */
    private static final Map<String, String> ROUTE_CODES = Map.of(
        "GET /file-upload/v1/constraints", "operations-file-upload-constraints",
        "GET /file-upload/v1/files", "operations-file-upload-list",
        "GET /file-upload/v1/files/{fileId}", "operations-file-upload-detail",
        "POST /file-upload/v1/uploads", "operations-file-upload-start",
        "POST /file-upload/v1/uploads/{uploadId}/completion", "operations-file-upload-complete");

    private static Map<String, List<List<String>>> block136;

    @BeforeAll
    static void readBlock() throws IOException {
        assertTrue(Files.isRegularFile(INITIAL_DATA), "找不到初始化数据: " + INITIAL_DATA);
        block136 = tables(Files.readString(INITIAL_DATA, StandardCharsets.UTF_8), BLOCK_136_START);
    }

    /** 块 136 行数与 ID 号段。 */
    @Test
    void test_rowCountsAndIdBlock() {
        assertEquals(Map.of("t_permission", 2, "t_role_permission", 2, "t_console_resource", 2, "t_gateway_route", 5,
            "t_gateway_route_header", 18, "t_gateway_route_query_param", 5, "t_gateway_guard_rule", 30),
            block136.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size())));
        var ids = new HashSet<Long>();
        block136.values().forEach(rows -> rows.forEach(row -> {
            long id = Long.parseLong(row.getFirst());
            assertTrue(id >= ID_FIRST && id <= ID_LAST, "ID 超出块 136: " + id);
            assertTrue(ids.add(id), "ID 重复: " + id);
        }));
    }

    /** 权限 ACTIVE，管理员授权 REVOKED，菜单挂在平台配置下、紧随文件记录（85），上传按钮挂在菜单下，均 DISABLED。 */
    @Test
    void test_permissionsGrantsAndMenu() {
        assertEquals(List.of(
            List.of(UPLOAD_READ, "FILE_UPLOAD_READ", "FILE_UPLOAD", "READ", "LOW", "ACTIVE"),
            List.of(UPLOAD_CREATE, "FILE_UPLOAD_CREATE", "FILE_UPLOAD", "CREATE", "HIGH", "ACTIVE")),
            block136.get("t_permission").stream().map(row -> row.subList(0, 6)).toList());
        assertEquals(List.of(UPLOAD_READ, UPLOAD_CREATE),
            block136.get("t_role_permission").stream().map(row -> row.get(2)).toList());
        assertTrue(block136.get("t_role_permission").stream().allMatch(row -> row.get(1).equals("@file_upload_console_role_id")
            && row.get(3).equals("REVOKED")));
        assertEquals(List.of(
            List.of("OPERATIONS_FILE_UPLOAD", "MENU", "900000000000000562", "文件上传", "/file-upload", "OPERATIONS_FILE_UPLOAD",
                UPLOAD_READ, "86", "DISABLED"),
            List.of("OPERATIONS_FILE_UPLOAD_CREATE", "BUTTON", "900000000000136301", "上传", "NULL", "NULL", UPLOAD_CREATE, "10",
                "DISABLED")),
            block136.get("t_console_resource").stream().map(row -> List.of(row.get(1), row.get(3), row.get(4), row.get(5),
                row.get(6), row.get(7), row.get(8), row.get(9), row.get(10))).toList());
    }

    /** 路由恰好覆盖每个 BFF 入口一次：同路径、按方法绑定权限、Body 上限与 Query 白名单、Route Code。 */
    @Test
    void test_routesMatchBffEntries() {
        Map<String, Method> entries = bffEntries();
        var routes = block136.get("t_gateway_route");
        assertEquals(entries.keySet(), routes.stream().map(row -> row.get(3) + " " + row.get(5))
            .collect(Collectors.toCollection(TreeSet::new)));
        assertEquals(entries.size(), routes.size(), "每个入口只能有一条路由");
        for (var route : routes) {
            String endpoint = route.get(3) + " " + route.get(5);
            Method entry = entries.get(endpoint);
            boolean read = route.get(3).equals("GET");
            boolean body = Arrays.stream(entry.getParameters()).anyMatch(p -> p.isAnnotationPresent(RequestBody.class));
            assertAll(endpoint,
                () -> assertEquals(ROUTE_CODES.get(endpoint), route.get(1)),
                () -> assertEquals(read ? UPLOAD_READ : UPLOAD_CREATE, route.get(9)),
                () -> assertEquals("/api/operations" + route.get(5), route.get(4)),
                () -> assertEquals(List.of("OPERATIONS", "@file_upload_console_target_id", "OPERATIONS_JWT", "HUMAN_PERMISSION"),
                    List.of(route.get(2), route.get(6), route.get(7), route.get(8))),
                () -> assertEquals(List.of("NONE", body ? "65536" : "0", "5000", "10000", "DISABLED"), route.subList(13, 18)),
                () -> assertEquals(queryParameters(entry), whitelist(route.getFirst())));
        }
    }

    /** Header 按块 134：GET 只有 authorization 与 origin；POST 加 content-type 与 idempotency-key（完成上传无 Body 也声明 JSON）。 */
    @Test
    void test_routeHeadersAndGuards() {
        for (var route : block136.get("t_gateway_route")) {
            boolean read = route.get(3).equals("GET");
            var expected = new TreeSet<>(Set.of("authorization:CORS_REQUEST_ALLOWED", "origin:FORWARD_REQUEST"));
            if (!read) {
                expected.addAll(Set.of("content-type:CORS_REQUEST_ALLOWED", "content-type:FORWARD_REQUEST",
                    "idempotency-key:CORS_REQUEST_ALLOWED", "idempotency-key:FORWARD_REQUEST"));
            }
            assertEquals(expected, block136.get("t_gateway_route_header").stream()
                .filter(row -> row.get(1).equals(route.getFirst())).map(row -> row.get(2) + ":" + row.get(3))
                .collect(Collectors.toCollection(TreeSet::new)), route.get(1));
            assertEquals(List.of("RATE_LIMIT SHARED_ROUTE 300", "CONCURRENCY SHARED_ROUTE 50", "RATE_LIMIT CLIENT_IP 30",
                "CONCURRENCY CLIENT_IP 3", "RATE_LIMIT AUTHENTICATED_PRINCIPAL " + (read ? 30 : 10), "CONCURRENCY AUTHENTICATED_PRINCIPAL 2"),
                block136.get("t_gateway_guard_rule").stream().filter(row -> row.get(1).equals(route.getFirst()))
                    .map(row -> row.get(2) + " " + row.get(3) + " " + (row.get(2).equals("RATE_LIMIT") ? row.get(4) : row.get(7)))
                    .toList(), route.get(1));
        }
        for (var guard : block136.get("t_gateway_guard_rule")) {
            boolean rate = guard.get(2).equals("RATE_LIMIT");
            assertTrue(rate ? Long.parseLong(guard.get(4)) <= 600 && guard.get(4).equals(guard.get(5)) && guard.get(6).equals("60000")
                : Long.parseLong(guard.get(7)) <= 50, "Guard 超出 Gateway 编译器上限: " + guard.getFirst());
        }
    }

    /** 某一注册块中各表的行：从块注释起到第一个行首 COMMIT。 */
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

    /** BFF 文件上传入口：方法 + 完整路径模板到处理方法。 */
    private static Map<String, Method> bffEntries() {
        var entries = new TreeMap<String, Method>();
        String base = AnnotatedElementUtils.findMergedAnnotation(FileUploadController.class, RequestMapping.class).path()[0];
        for (Method method : FileUploadController.class.getDeclaredMethods()) {
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

    /** 块 136 路由的 Query 白名单。 */
    private static Set<String> whitelist(String routeId) {
        return block136.getOrDefault("t_gateway_route_query_param", List.of()).stream()
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
