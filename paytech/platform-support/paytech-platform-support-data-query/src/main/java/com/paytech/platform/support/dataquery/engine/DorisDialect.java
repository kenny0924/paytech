package com.paytech.platform.support.dataquery.engine;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;

/**
 * Doris 方言：系统库 mysql 与 __internal_schema；不向服务端下发 SET SESSION TRANSACTION READ ONLY（Doris 不支持），
 * 只读由账号权限与只读校验保证；账号权限按 SHOW GRANTS 的各 *Privs 列检查。
 */
public final class DorisDialect implements SqlDialect {

    /** 唯一实例；方言无状态。 */
    public static final DorisDialect INSTANCE = new DorisDialect();

    private static final Set<String> SYSTEM_SCHEMAS = Set.of("mysql", "__internal_schema");

    private static final Set<String> ALLOWED_PRIVILEGES = Set.of("SELECT_PRIV", "USAGE_PRIV");

    private static final Pattern PRIVILEGE = Pattern.compile("[A-Za-z_]+_priv", Pattern.CASE_INSENSITIVE);

    private DorisDialect() {
    }

    /** Doris。 */
    @Override
    public DataSourceEngineType engineType() {
        return DataSourceEngineType.DORIS;
    }

    /** mysql 兼容库与 __internal_schema 内部库。 */
    @Override
    public boolean isSystemSchema(String schema) {
        return schema != null && SYSTEM_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT));
    }

    /** 不下发只读事务语句。 */
    @Override
    public Map<String, String> sessionProperties() {
        return Map.of("readOnlyPropagatesToServer", "false");
    }

    /** 汇总 SHOW GRANTS 中所有以 Privs 结尾的列。 */
    @Override
    public ConnectionCheckResult inspectGrants(Connection connection) throws SQLException {
        List<String> grants = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SHOW GRANTS")) {
            ResultSetMetaData metaData = rows.getMetaData();
            while (rows.next()) {
                for (int column = 1; column <= metaData.getColumnCount(); column++) {
                    String label = metaData.getColumnLabel(column);
                    String value = rows.getString(column);
                    if (label.endsWith("Privs") && value != null && !value.isBlank()) {
                        grants.add(label + ": " + value);
                    }
                }
            }
        }
        return new ConnectionCheckResult(true, grants, violations(grants));
    }

    /**
     * 只能有 Select_priv 与 Usage_priv；Load、Alter、Create、Drop、Admin、Grant 等一律不通过。
     *
     * @param grants 授权原文
     * @return 违规原因
     */
    public static List<String> violations(List<String> grants) {
        Set<String> violations = new LinkedHashSet<>();
        for (String grant : grants) {
            Matcher matcher = PRIVILEGE.matcher(grant);
            while (matcher.find()) {
                String privilege = matcher.group().toUpperCase(Locale.ROOT);
                if (!ALLOWED_PRIVILEGES.contains(privilege)) {
                    violations.add("含非只读权限 " + matcher.group() + ": " + grant);
                }
            }
        }
        return List.copyOf(violations);
    }
}
