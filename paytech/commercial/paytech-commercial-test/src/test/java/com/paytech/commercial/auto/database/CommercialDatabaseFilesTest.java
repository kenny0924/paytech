package com.paytech.commercial.auto.database;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.test.clear.CommercialDataCleaner;
import com.paytech.commercial.test.mocker.local.catalog.ProductCatalogFixture;

import tools.jackson.databind.json.JsonMapper;

/**
 * 人工执行的 SQL 文件与测试基座的一致性（纯文件检查，不连接数据库）：
 * <li>测试 DDL init/ddl.sql 的每张商务表逐字等于 docs/database/sql/commercial-schema.sql，另含每库一份的 t_consumed_event；</li>
 * <li>CommercialDataCleaner 的表集合等于测试 DDL 的表集合；</li>
 * <li>初始化 DML commercial-initial-data.sql 的目录行与测试固件 product-catalog.json 逐行逐列一致。</li>
 *
 * @see ProductCatalogFixture#load()
 */
class CommercialDatabaseFilesTest {
    private static final Pattern CREATE_TABLE =
        Pattern.compile("CREATE TABLE `?(\\w+)`? \\((.*?)\\n\\)[^;]*;", Pattern.DOTALL);

    private static final Pattern INSERT =
        Pattern.compile("INSERT INTO `(\\w+)` \\(([^)]*)\\) VALUES\\n(.*?)\\nON DUPLICATE KEY UPDATE", Pattern.DOTALL);

    private static final Pattern TUPLE_VALUE = Pattern.compile("'([^']*)'|(UTC_TIMESTAMP\\(6\\))|(NULL)|(-?\\d+)");

    /** 测试 DDL 与生成 Schema 逐表一致，清理器覆盖全部表。 */
    @Test
    void test_testDdlMatchesSchemaAndCleaner() {
        Map<String, String> schema = tables(read(root().resolve("docs/database/sql/commercial-schema.sql")));
        Map<String, String> ddl = tables(resource("/init/ddl.sql"));

        Set<String> businessTables = new LinkedHashSet<>(ddl.keySet());
        businessTables.remove("t_consumed_event");
        assertAll(() -> assertEquals(20, schema.size()), () -> assertEquals(schema.keySet(), businessTables),
            () -> schema.forEach((table, definition) -> assertEquals(definition, ddl.get(table), table + " 与生成 Schema 不一致")),
            () -> assertEquals(ddl.keySet(), new LinkedHashSet<>(CommercialDataCleaner.TABLES)));
    }

    /** 初始化 DML 与测试固件的目录行一致（时间列除外）。 */
    @Test
    @SuppressWarnings("unchecked")
    void test_initialDataMatchesCatalogFixture() throws IOException {
        Map<String, List<Map<String, Object>>> fixture;
        try (InputStream input = getClass().getResourceAsStream(ProductCatalogFixture.RESOURCE)) {
            fixture = JsonMapper.builder().build().readValue(input, Map.class);
        }
        Map<String, List<Map<String, String>>> dml = inserts(read(root().resolve("docs/database/sql/commercial-initial-data.sql")));

        assertAll(() -> assertEquals(List.of("t_product", "t_product_capability", "t_product_fee_event"), List.copyOf(dml.keySet())),
            () -> assertEquals(7, dml.get("t_product").size()), () -> assertEquals(11, dml.get("t_product_capability").size()),
            () -> assertEquals(21, dml.get("t_product_fee_event").size()),
            () -> assertEquals(normalize(fixture.get("product")), dml.get("t_product")),
            () -> assertEquals(normalize(fixture.get("product_capability")), dml.get("t_product_capability")),
            () -> assertEquals(normalize(fixture.get("product_fee_event")), dml.get("t_product_fee_event")),
            () -> assertEquals(7, ProductCatalogFixture.load().products().size()));
    }

    /** 每张表的 CREATE TABLE 原文，按出现顺序。 */
    private static Map<String, String> tables(String sql) {
        Map<String, String> tables = new LinkedHashMap<>();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            tables.put(matcher.group(1), matcher.group());
        }
        return tables;
    }

    /** 每张表 INSERT 的行，列名到原值文本；时间列与 NULL 不纳入比较。 */
    private static Map<String, List<Map<String, String>>> inserts(String sql) {
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        Matcher matcher = INSERT.matcher(sql);
        while (matcher.find()) {
            List<String> columns = List.of(matcher.group(2).replace("`", "").split(",\\s*"));
            List<Map<String, String>> rows = new ArrayList<>();
            for (String line : matcher.group(3).split("\\n")) {
                List<String> values = new ArrayList<>();
                Matcher value = TUPLE_VALUE.matcher(line);
                while (value.find()) {
                    values.add(value.group(1) != null ? value.group(1) : value.group(4) != null ? value.group(4)
                        : value.group(3) != null ? null : "UTC_TIMESTAMP(6)");
                }
                assertEquals(columns.size(), values.size(), "初始化 DML 列数不符: " + line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    if (values.get(i) != null && !columns.get(i).endsWith("_time")) {
                        row.put(columns.get(i), values.get(i));
                    }
                }
                rows.add(row);
            }
            result.put(matcher.group(1), rows);
        }
        return result;
    }

    /** 固件行转为与 DML 相同的文本形式；null 不纳入比较。 */
    private static List<Map<String, String>> normalize(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, String> text = new LinkedHashMap<>();
            row.forEach((column, value) -> {
                if (value != null) {
                    text.put(column, value.toString());
                }
            });
            return text;
        }).toList();
    }

    /** commercial 服务根目录，由 surefire 注入。 */
    private static Path root() {
        Path root = Path.of(System.getProperty("paytech.commercial.root", "..")).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(root.resolve("docs/database/sql/commercial-schema.sql")), "commercial 根目录不正确: " + root);
        return root;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("无法读取 " + path, exception);
        }
    }

    private String resource(String name) {
        try (InputStream input = getClass().getResourceAsStream(name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("无法读取 " + name, exception);
        }
    }
}
