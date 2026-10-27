package com.paytech.client.master.test.mocker.onboarding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * 国家包夹具：每例按正式初始化 DML 装入 AE / SA / EG 包，需要时再装入只用数据定义的测试辖区 ZZ
 * <li>正式包直接读取 docs/database/sql/client-master-initial-data.sql，测试与上线使用同一份数据。</li>
 * <li>包表由 ClientMasterDataCleaner 每例清空；本类不保留状态。</li>
 */
@TestComponent
public class JurisdictionPackMocker implements Clear {
    /** 国家包表，装入前先清空。 */
    public static final List<String> PACK_TABLES = List.of("t_jurisdiction", "t_rule_setting", "t_organization_type",
        "t_identifier_type", "t_relation_type", "t_address_format_component", "t_catalog_item", "t_attribute_definition",
        "t_onboarding_schema", "t_onboarding_schema_field");

    private static List<String> standard;
    private static List<String> zz;

    private final JdbcTemplate jdbc;

    /** 使用被测应用的数据源。 */
    public JurisdictionPackMocker(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** 清空包表后装入 AE / SA / EG 正式包。 */
    public void loadStandardPacks() {
        PACK_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
        standardStatements().forEach(jdbc::update);
    }

    /** 追加测试辖区 ZZ（依赖正式包中的全局目录、地址格式与关系类型）。 */
    public void loadZzPack() {
        zzStatements().forEach(jdbc::update);
    }

    /** 把某辖区的准入策略设为指定值（覆盖或新增 JURISDICTION 规则）。 */
    public void acceptancePolicy(String jurisdictionCode, String policy) {
        int updated = jdbc.update("UPDATE t_rule_setting SET rule_value=? WHERE scope_type='JURISDICTION' AND scope_code=? "
            + "AND rule_code='ACCEPTANCE_POLICY'", policy, jurisdictionCode);
        if (updated == 0) {
            jdbc.update("INSERT INTO t_rule_setting(rule_setting_id,scope_type,scope_code,rule_code,rule_value,status,create_time,"
                + "update_time) VALUES(9399000000199999,'JURISDICTION',?,'ACCEPTANCE_POLICY',?,'ACTIVE',UTC_TIMESTAMP(6),"
                + "UTC_TIMESTAMP(6))", jurisdictionCode, policy);
        }
    }

    /** 包数据由全量清理器删除。 */
    @Override
    public void clear() {
    }

    /** 正式初始化 DML 中的 INSERT 语句。 */
    static synchronized List<String> standardStatements() {
        if (standard == null) {
            String root = System.getProperty("paytech.client-master.root", "..");
            try {
                standard = statements(Files.readString(Path.of(root, "docs/database/sql/client-master-initial-data.sql"),
                    StandardCharsets.UTF_8));
            } catch (IOException missing) {
                throw new IllegalStateException("无法读取 client-master 初始化 DML，请在 client-master 模块目录运行测试", missing);
            }
        }
        return standard;
    }

    /** 测试辖区 ZZ 的 INSERT 语句。 */
    static synchronized List<String> zzStatements() {
        if (zz == null) {
            try (InputStream in = JurisdictionPackMocker.class.getResourceAsStream("/fixtures/client-master/zz-pack.sql")) {
                zz = statements(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException | NullPointerException missing) {
                throw new IllegalStateException("缺少测试辖区夹具 zz-pack.sql", missing);
            }
        }
        return zz;
    }

    /** 去掉整行注释，按语句末尾分号拆分，只保留 INSERT。 */
    static List<String> statements(String sql) {
        var body = new StringBuilder();
        sql.lines().filter(line -> !line.stripLeading().startsWith("--")).forEach(line -> body.append(line).append('\n'));
        var result = new ArrayList<String>();
        for (String statement : body.toString().split(";\\s*\n")) {
            String trimmed = statement.strip();
            if (trimmed.regionMatches(true, 0, "INSERT", 0, 6)) {
                result.add(trimmed);
            }
        }
        return List.copyOf(result);
    }
}
