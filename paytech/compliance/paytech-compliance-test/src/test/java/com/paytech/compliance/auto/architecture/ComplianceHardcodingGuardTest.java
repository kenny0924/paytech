package com.paytech.compliance.auto.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.paytech.compliance.test.mocker.ProgramPackMocker;

/**
 * 硬编码边界守门（technique S-17、design 10.0.1a）：辖区是数据不是分支
 * <li>从初始化 DML 收集辖区代码、程序编码、材料类型、材料要求代码与检查代码，compliance 生产源码（core、app 的 Java 与 Mapper XML）
 * 不得以字符串字面量出现这些目录值。</li>
 * <li>唯一白名单：ClientReviewRegistrationBuilder 的关系类型到依赖类型映射含 OWNERSHIP（client-master 关系类型种子五值之一，
 * 与材料要求代码同名但含义不同）；目录外的关系类型统一映射为 RELATED_PERSON_KYC，不需要改代码。</li>
 * <li>ZZ 国家包全链（ZzCountryPackAutoTest）是 AC-11 的主门禁，本测试防止目录值被写进代码分支。</li>
 */
class ComplianceHardcodingGuardTest {
    private static final Pattern VALUES = Pattern.compile("'([A-Z][A-Z0-9_-]*)'");

    /** DML 目录值不出现在生产源码字面量中。 */
    @Test
    void test_businessRuleViolation_noCatalogLiteralsInProduction() throws IOException {
        Set<String> catalog = catalogValues();
        assertTrue(catalog.containsAll(Set.of("AE", "SA", "EG", "TRADE_LICENSE", "WEBSITE_REVIEW")), catalog.toString());
        Set<String> violations = new TreeSet<>();
        Path root = Path.of(System.getProperty("compliance.reactor.root"));
        for (String module : List.of("paytech-compliance-core", "paytech-compliance-app")) {
            try (var files = Files.walk(root.resolve(module).resolve("src/main"))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".xml")).toList()) {
                    String source = Files.readString(file);
                    for (String value : catalog) {
                        boolean allowed = file.endsWith("ClientReviewRegistrationBuilder.java") && "OWNERSHIP".equals(value);
                        if (!allowed && (source.contains("\"" + value + "\"") || source.contains("'" + value + "'"))) {
                            violations.add(root.relativize(file) + ": " + value);
                        }
                    }
                }
            }
        }
        assertEquals(Set.of(), violations, "生产源码出现辖区目录值字面量");
    }

    /** 初始化 DML 中的辖区、程序编码、材料类型、要求与检查代码（不含通用枚举值）。 */
    static Set<String> catalogValues() {
        Set<String> generic = Set.of("ORGANIZATION", "INDIVIDUAL", "CLIENT", "RELATED_PERSON", "STANDARD", "PRIMARY", "MANUAL",
            "ACTIVE", "ORGANIZATION_KYB", "INDIVIDUAL_KYC", "RELATED_PERSON_KYC", "SCREENING_SEARCH", "SANCTIONS", "PEP", "ADVERSE_MEDIA",
            "BUSINESS", "LIST_LOOKUP", "SCREENING");
        Set<String> values = new TreeSet<>();
        for (String statement : ProgramPackMocker.initialDataStatements()) {
            Matcher matcher = VALUES.matcher(statement);
            while (matcher.find()) {
                if (!generic.contains(matcher.group(1))) {
                    values.add(matcher.group(1));
                }
            }
        }
        return values;
    }
}
