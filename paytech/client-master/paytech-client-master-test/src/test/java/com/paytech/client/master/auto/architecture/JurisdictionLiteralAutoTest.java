package com.paytech.client.master.auto.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 国家差异只来自数据（需求 AC-11 / 技术方案第 3 节）：生产源码不得以字符串字面量引用国家包中的辖区、组织类型、标识类型、扩展属性
 * 或辖区专属目录项代码；新增国家只改 DML。
 * <li>代码集合从正式初始化 DML 解析，国家包变化时守卫随之更新；规则代码、校验位算法名与控件代码属于平台注册表，不在此列。</li>
 */
class JurisdictionLiteralAutoTest {
    private static final List<String> MODULES = List.of("paytech-client-master-api", "paytech-client-master-core",
        "paytech-client-master-app");
    private static final Pattern ROW = Pattern.compile("\\n\\s*\\((.*)\\)[,;]");
    private static final Pattern LITERAL = Pattern.compile("\"([A-Z][A-Z0-9_]*)\"");

    /** 生产源码零命中。 */
    @Test
    void test_noJurisdictionLiterals() throws IOException {
        Path root = Path.of(System.getProperty("paytech.client-master.root", "..")).toAbsolutePath().normalize();
        String dml = Files.readString(root.resolve("docs/database/sql/client-master-initial-data.sql"), StandardCharsets.UTF_8);
        Set<String> codes = packCodes(dml);
        assertTrue(codes.containsAll(Set.of("AE", "SA", "EG", "AE_EMIRATES_ID", "SA_NATIONAL_ID", "PASSPORT", "FZ_LLC", "AE_TRN")),
            "国家包解析不完整: " + codes);

        List<String> hits = new ArrayList<>();
        for (String module : MODULES) {
            try (Stream<Path> files = Files.walk(root.resolve(module).resolve("src/main/java"))) {
                files.filter(file -> file.toString().endsWith(".java")).forEach(file -> scan(file, codes, hits));
            }
        }

        assertEquals(List.of(), hits, "生产源码引用了国家包代码");
    }

    /** 某源文件中的命中。 */
    static void scan(Path file, Set<String> codes, List<String> hits) {
        try {
            Matcher matcher = LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                if (codes.contains(matcher.group(1))) {
                    hits.add(file.getFileName() + ": " + matcher.group(1));
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** 辖区、标识类型、组织类型、属性与辖区专属目录项代码。 */
    static Set<String> packCodes(String dml) {
        Set<String> codes = new TreeSet<>();
        values(dml, "t_jurisdiction").forEach(row -> codes.add(row.get(0)));
        values(dml, "t_identifier_type").forEach(row -> codes.add(row.get(1)));
        values(dml, "t_organization_type").forEach(row -> codes.add(row.get(2)));
        values(dml, "t_attribute_definition").forEach(row -> codes.add(row.get(0)));
        values(dml, "t_catalog_item").stream().filter(row -> !"*".equals(row.get(3))).forEach(row -> codes.add(row.get(2)));
        return codes;
    }

    /** 一张表 INSERT 的全部行，按逗号拆分并去掉引号（国家包取值不含逗号）。 */
    static List<List<String>> values(String dml, String table) {
        int start = dml.indexOf("INSERT INTO `" + table + "`");
        assertTrue(start >= 0, "初始化 DML 缺少 " + table);
        int end = dml.indexOf(";\n", start);
        List<List<String>> rows = new ArrayList<>();
        Matcher matcher = ROW.matcher(dml.substring(start, end + 1));
        while (matcher.find()) {
            rows.add(Stream.of(matcher.group(1).split(",")).map(value -> value.strip().replace("'", "")).toList());
        }
        return rows;
    }
}
