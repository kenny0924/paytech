package com.paytech.platform.support.dataquery.engine;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;

/**
 * MySQL 方言：系统库 mysql、sys、performance_schema；会话只读由驱动 readOnlyPropagatesToServer 下发；
 * 账号权限按 SHOW GRANTS FOR CURRENT_USER() 检查。
 */
public final class MysqlDialect implements SqlDialect {

    /** 唯一实例；方言无状态。 */
    public static final MysqlDialect INSTANCE = new MysqlDialect();

    private static final Set<String> SYSTEM_SCHEMAS = Set.of("mysql", "sys", "performance_schema");

    /** 允许的权限：授权范围可以是 *.* 或具体库、表。 */
    private static final Set<String> ALLOWED_PRIVILEGES = Set.of("USAGE", "SELECT", "SHOW VIEW");

    private static final Pattern PRIVILEGE_GRANT = Pattern.compile("^GRANT (.+?) ON (.+?) TO .+$", Pattern.DOTALL);

    private MysqlDialect() {
    }

    /** MySQL。 */
    @Override
    public DataSourceEngineType engineType() {
        return DataSourceEngineType.MYSQL;
    }

    /** mysql 含账号与密码哈希，sys、performance_schema 暴露实例运行信息。 */
    @Override
    public boolean isSystemSchema(String schema) {
        return schema != null && SYSTEM_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT));
    }

    /** 连接 readOnly 时驱动发送 SET SESSION TRANSACTION READ ONLY。 */
    @Override
    public Map<String, String> sessionProperties() {
        return Map.of("readOnlyPropagatesToServer", "true");
    }

    /** 逐行检查 SHOW GRANTS FOR CURRENT_USER()。 */
    @Override
    public ConnectionCheckResult inspectGrants(Connection connection) throws SQLException {
        List<String> grants = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
            while (rows.next()) {
                grants.add(rows.getString(1));
            }
        }
        return new ConnectionCheckResult(true, grants, violations(grants));
    }

    /**
     * 授权原文中的违规项；纯函数，供连接检查与单元测试使用。
     * <li>只允许 USAGE、SELECT、SHOW VIEW；ALL PRIVILEGES、WITH GRANT OPTION、写与 DDL 权限、FILE、SUPER、PROCESS 等一律不通过。</li>
     * <li>角色授权（GRANT `role` TO …）无法逐项展开，一律不通过。</li>
     *
     * @param grants SHOW GRANTS 逐行结果
     * @return 违规原因；全部通过时为空
     */
    public static List<String> violations(List<String> grants) {
        List<String> violations = new ArrayList<>();
        for (String grant : grants) {
            String line = grant == null ? "" : grant.strip();
            Matcher matcher = PRIVILEGE_GRANT.matcher(line);
            if (!matcher.matches()) {
                violations.add("含角色授权，无法确认只读: " + line);
                continue;
            }
            if (line.toUpperCase(Locale.ROOT).endsWith("WITH GRANT OPTION")) {
                violations.add("含 WITH GRANT OPTION: " + line);
            }
            for (String privilege : privileges(matcher.group(1))) {
                if (!ALLOWED_PRIVILEGES.contains(privilege)) {
                    violations.add("含非只读权限 " + privilege + ": " + line);
                }
            }
        }
        return violations;
    }

    /**
     * 拆分权限列表，忽略列级权限的括号内容。
     *
     * @param list 如 "SELECT, INSERT (a, b), SHOW VIEW"
     * @return 大写权限名
     */
    static List<String> privileges(String list) {
        List<String> privileges = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char character : list.toCharArray()) {
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            } else if (character == ',' && depth == 0) {
                privileges.add(current.toString().strip().toUpperCase(Locale.ROOT));
                current.setLength(0);
            } else if (depth == 0) {
                current.append(character);
            }
        }
        privileges.add(current.toString().strip().toUpperCase(Locale.ROOT));
        return privileges;
    }
}
