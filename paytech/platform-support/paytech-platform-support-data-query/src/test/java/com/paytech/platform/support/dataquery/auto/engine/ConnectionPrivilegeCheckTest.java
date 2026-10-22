package com.paytech.platform.support.dataquery.auto.engine;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;
import com.paytech.platform.support.dataquery.engine.DorisDialect;
import com.paytech.platform.support.dataquery.engine.JdbcConnectionSettings;
import com.paytech.platform.support.dataquery.engine.MysqlDialect;

import org.junit.jupiter.api.Assertions;

/**
 * 启用前的账号权限判定（technique 5.2）与驱动参数（4.3 连接层）：纯函数部分。
 * <li>MySQL：只允许 USAGE、SELECT、SHOW VIEW，范围可为 *.*；写、DDL、FILE、ALL PRIVILEGES、WITH GRANT OPTION 与任何角色授权都不通过。</li>
 * <li>Doris：只允许 Select_priv、Usage_priv。驱动参数只接受五项白名单，安全参数与方言参数不能被覆盖。</li>
 *
 * @see MysqlDialect#violations(List)
 * @see DorisDialect#violations(List)
 * @see JdbcConnectionSettings#driverProperties(DataSourceConnectionConfig)
 */
class ConnectionPrivilegeCheckTest {

    /** 只读授权（含整实例范围、列级 SELECT 与 SHOW VIEW）全部通过。 */
    @Test
    void test_mysqlReadOnlyGrantsPass() {
        List<String> violations = MysqlDialect.violations(List.of(
            "GRANT USAGE ON *.* TO `reader`@`%`",
            "GRANT SELECT ON *.* TO `reader`@`%`",
            "GRANT SELECT, SHOW VIEW ON `mor_data_query_fixture`.* TO `reader`@`%`",
            "GRANT SELECT (`refund_id`, `refund_no`) ON `mor_data_query_fixture`.`t_refund_fixture` TO `reader`@`%`"));
        assertEquals(List.of(), violations);
    }

    /** 写、DDL、FILE、PROCESS、ALL PRIVILEGES、WITH GRANT OPTION 与角色授权各自给出具体原因。 */
    @Test
    void test_mysqlUnsafeGrantsRejected() {
        List<String> violations = MysqlDialect.violations(List.of(
            "GRANT SELECT, INSERT, UPDATE ON `mor_data_query_fixture`.* TO `writer`@`%`",
            "GRANT FILE, PROCESS ON *.* TO `u`@`%`",
            "GRANT ALL PRIVILEGES ON `db`.* TO `u`@`%`",
            "GRANT SELECT ON `db`.* TO `u`@`%` WITH GRANT OPTION",
            "GRANT `report_reader`@`%` TO `u`@`%`"));
        assertAll(() -> assertTrue(contains(violations, "INSERT")), () -> assertTrue(contains(violations, "UPDATE")),
            () -> assertTrue(contains(violations, "FILE")), () -> assertTrue(contains(violations, "PROCESS")),
            () -> assertTrue(contains(violations, "ALL PRIVILEGES")),
            () -> assertTrue(contains(violations, "WITH GRANT OPTION")),
            () -> assertTrue(contains(violations, "角色授权")), () -> assertEquals(7, violations.size()));
    }

    /** Doris 只允许查询与使用权限。 */
    @Test
    void test_dorisPrivileges() {
        assertEquals(List.of(), DorisDialect.violations(List.of("GlobalPrivs: Select_priv,Usage_priv",
            "DatabasePrivs: internal.mor_data_service: Select_priv")));
        List<String> violations = DorisDialect.violations(List.of("GlobalPrivs: Select_priv,Load_priv,Alter_priv",
            "DatabasePrivs: internal.db: Create_priv,Drop_priv,Admin_priv,Grant_priv"));
        assertEquals(6, violations.size());
    }

    /** 安全参数与方言参数在白名单参数之后写入；白名单外参数直接拒绝。 */
    @Test
    void test_driverPropertiesForced() {
        DataSourceConnectionConfig config = new DataSourceConnectionConfig(DataSourceEngineType.MYSQL, "127.0.0.1",
            13306, null, "reader", Map.of("connectTimeout", "1500", "sslMode", "REQUIRED"));
        Map<String, String> properties = JdbcConnectionSettings.driverProperties(config);
        assertAll(() -> assertEquals("jdbc:mysql://127.0.0.1:13306/", JdbcConnectionSettings.url(config)),
            () -> assertEquals("1500", properties.get("connectTimeout")),
            () -> assertEquals("REQUIRED", properties.get("sslMode")),
            () -> assertEquals("false", properties.get("allowMultiQueries")),
            () -> assertEquals("false", properties.get("allowLoadLocalInfile")),
            () -> assertEquals("false", properties.get("allowUrlInLocalInfile")),
            () -> assertEquals("false", properties.get("autoDeserialize")),
            () -> assertEquals("true", properties.get("readOnlyPropagatesToServer")));
        Map<String, String> doris = JdbcConnectionSettings.driverProperties(new DataSourceConnectionConfig(
            DataSourceEngineType.DORIS, "127.0.0.1", 19030, "mor_data_service", "reader", null));
        assertAll(() -> assertEquals("false", doris.get("readOnlyPropagatesToServer")),
            () -> assertEquals("3000", doris.get("connectTimeout")));
        Assertions.assertThrows(InvalidParameterException.class, () -> new DataSourceConnectionConfig(
            DataSourceEngineType.MYSQL, "127.0.0.1", 13306, null, "reader", Map.of("allowMultiQueries", "true")));
    }

    /** 原因列表中是否有包含关键字的一项。 */
    static boolean contains(List<String> violations, String keyword) {
        return violations.stream().anyMatch(reason -> reason.contains(keyword));
    }
}
