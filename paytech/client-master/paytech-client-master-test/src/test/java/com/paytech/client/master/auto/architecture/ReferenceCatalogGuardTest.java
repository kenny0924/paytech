package com.paytech.client.master.auto.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.domain.onboarding.pack.ReferenceCatalogCodes;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;

/**
 * 参考数据守卫（需求 R7、AC-06）：Platform 参考数据（国家、币种、一级行政区、行业）不得回到 t_catalog_item
 * <li>读取正式初始化 DML 与测试夹具中 t_catalog_item 的全部 INSERT：catalog_code 不得为 INDUSTRY、CURRENCY、STATE_OR_PROVINCE，
 * 也不得以保留前缀 REF_ 开头（REF_ 码只能出现在 Schema 字段与地址组件上，由代码识别为来自 Platform）。</li>
 * <li>业务信息补采：commercial 外部目录码（COMMERCIAL_ 开头，如 COMMERCIAL_PAYMENT_METHOD）同样不得进入 t_catalog_item；
 * 本库自有目录 FULFILLMENT_TYPE 恰为 4 项。</li>
 */
class ReferenceCatalogGuardTest {
    /** 迁出 t_catalog_item 的旧目录码。 */
    private static final Set<String> RETIRED = Set.of("INDUSTRY", "CURRENCY", "STATE_OR_PROVINCE");
    private static final String INSERT = "INSERT INTO `t_catalog_item`";
    private static final Pattern ROW = Pattern.compile("\\n\\s*\\(\\s*[0-9]+\\s*,\\s*'([^']*)'");

    /** 初始化 DML 与夹具零命中。 */
    @Test
    void test_noReferenceCatalogInCatalogItems() throws IOException {
        Path root = Path.of(System.getProperty("paytech.client-master.root", "..")).toAbsolutePath().normalize();
        String initialData = Files.readString(root.resolve("docs/database/sql/client-master-initial-data.sql"), StandardCharsets.UTF_8);
        String zzPack;
        try (InputStream in = ReferenceCatalogGuardTest.class.getResourceAsStream("/fixtures/client-master/zz-pack.sql")) {
            zzPack = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<String> initialCodes = catalogCodes(initialData);
        List<String> zzCodes = catalogCodes(zzPack);

        assertTrue(initialCodes.size() > 50 && initialCodes.contains("REGISTRATION_AUTHORITY"), "初始化 DML 目录解析不完整: " + initialCodes);
        assertTrue(zzCodes.contains("ZZ_LICENSE_CLASS"), "ZZ 夹具目录解析不完整: " + zzCodes);
        assertEquals(List.of(), violations("client-master-initial-data.sql", initialCodes), "t_catalog_item 出现参考数据目录");
        assertEquals(List.of(), violations("zz-pack.sql", zzCodes), "t_catalog_item 出现参考数据目录");
        assertTrue(initialData.contains("'" + ReferenceCatalogCodes.REF_CURRENCY + "'")
            && initialData.contains("'" + ReferenceCatalogCodes.REF_COUNTRY_SUBDIVISION + "'"), "Schema 应以 REF_ 码引用 Platform 参考数据");
        assertTrue(initialData.contains("'" + ReferenceCatalogCodes.COMMERCIAL_PAYMENT_METHOD + "'"), "v3 Schema 应以外部目录码引用 commercial 支付方式");
        assertEquals(4, initialCodes.stream().filter(OnboardingFixtures.FULFILLMENT_TYPE_CATALOG::equals).count(), "交付方式目录应为 4 项");
    }

    /** 某文件中 t_catalog_item 全部 INSERT 行的 catalog_code。 */
    static List<String> catalogCodes(String sql) {
        List<String> codes = new ArrayList<>();
        for (int start = sql.indexOf(INSERT); start >= 0; start = sql.indexOf(INSERT, start + INSERT.length())) {
            int end = sql.indexOf(";\n", start);
            Matcher matcher = ROW.matcher(sql.substring(start, end < 0 ? sql.length() : end + 1));
            while (matcher.find()) {
                codes.add(matcher.group(1));
            }
        }
        return codes;
    }

    /** 违反守卫的目录码。 */
    static List<String> violations(String file, List<String> codes) {
        return codes.stream().filter(code -> RETIRED.contains(code) || ReferenceCatalogCodes.isReference(code)
            || code.startsWith(ReferenceCatalogCodes.COMMERCIAL_PREFIX)).distinct()
            .map(code -> file + ": " + code).toList();
    }
}
