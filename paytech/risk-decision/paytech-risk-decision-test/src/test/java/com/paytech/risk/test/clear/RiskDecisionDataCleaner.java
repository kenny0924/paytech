package com.paytech.risk.test.clear;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * mor_risk_decision 全表清理器（design/10 §3.1、§4.2）：触碰的表集合等于本模块 init/ddl.sql 的表集合。
 * <li>各 Mocker 定向清理之后兜底删除本库全部测试数据；保留 risk-decision-initial-data.sql 的目录行（决策点、点版本、Action 目录与参数、
 * 系统变量与成员、自动承保规则 v1 与 USD 阈值、四张 TIER 名单），只删除目录表中非初始化主键的行。</li>
 * <li>{@link #assertBaseline()} 供全局 BeforeEach 断言空基线：非目录表为 0 行，目录表恰为初始化行数。</li>
 */
@TestComponent
public class RiskDecisionDataCleaner implements Clear {
    /** 目录表 → 主键列与初始化主键（与 risk-decision-initial-data.sql 一致）。 */
    static final Map<String, Catalog> CATALOG_TABLES = Map.of(
        "t_decision_point", new Catalog("decision_point_id", List.of(210001L, 210002L)),
        "t_decision_point_version", new Catalog("point_version_id", List.of(220001L, 220002L, 220003L)),
        "t_action_version", new Catalog("action_version_id", List.of(240001L, 240002L, 240003L, 240004L, 240005L,
            240006L, 240007L, 240008L)),
        "t_action_parameter_definition", new Catalog("parameter_definition_id",
            List.of(2400041L, 2400042L, 2400051L, 2400052L)),
        "t_risk_variable", new Catalog("variable_id", Stream.concat(range(900000000000091001L, 20),
            range(900000000000093001L, 16)).toList()),
        "t_risk_variable_member", new Catalog("member_id", Stream.concat(range(900000000000092002L, 8),
            range(900000000000094001L, 3)).toList()),
        "t_underwriting_rule_version", new Catalog("rule_version_id", List.of(290001L)),
        "t_underwriting_rule_threshold", new Catalog("threshold_id", List.of(290101L)),
        "t_risk_list", new Catalog("list_id", List.of(300001L, 300002L, 300003L, 300004L)));

    /** 只有测试数据、没有初始化行的表（含仅 Schema 的表与每库一份的基础设施表）。 */
    static final List<String> DATA_TABLES = List.of("t_rule_reject_reason", "t_rule_subject_tag",
        "t_decision_point_input_field", "t_decision_point_feature", "t_decision_point_rule_set",
        "t_decision_point_obligation", "t_feature_version", "t_feature_dependency", "t_velocity_feature_definition",
        "t_list_feature_definition", "t_rule_set", "t_rule_set_version", "t_risk_rule", "t_rule_node", "t_rule_operand",
        "t_rule_action", "t_rule_action_parameter", "t_decision_release", "t_release_audit_event",
        "t_release_shadow_run", "t_decision_flow", "t_decision_flow_version", "t_decision_flow_node",
        "t_decision_flow_edge", "t_decision_flow_end_output", "t_decision_evaluation", "t_evaluation_feature_result",
        "t_evaluation_rule_trace", "t_evaluation_flow_trace", "t_evaluation_action_intent", "t_action_effect",
        "t_checkout_input_snapshot", "t_transaction_risk_assessment", "t_settlement_risk_assessment",
        "t_risk_restriction", "t_risk_list_entry", "t_payment_attempt_fact", "t_velocity_event",
        "t_velocity_event_subject", "t_limit_version", "t_decision_point_limit_binding", "t_limit_position",
        "t_limit_reservation_group", "t_limit_reservation", "t_limit_group_state_change", "t_limit_movement",
        "t_client_underwriting", "t_client_limit_tier", "t_client_limit_tier_change", "t_client_allowed_daily_total",
        "t_compensation_event",
        "t_consumed_event");

    private final JdbcTemplate jdbc;

    public RiskDecisionDataCleaner(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 本库全部表（= init/ddl.sql 的 CREATE TABLE 集合）。 */
    public static List<String> tables() {
        return Stream.concat(DATA_TABLES.stream(), CATALOG_TABLES.keySet().stream()).sorted().toList();
    }

    @Override
    public void clear() {
        for (String table : DATA_TABLES) {
            jdbc.update("DELETE FROM " + table);
        }
        CATALOG_TABLES.forEach((table, catalog) -> jdbc.update("DELETE FROM " + table + " WHERE " + catalog.primaryKey()
            + " NOT IN (" + String.join(",", catalog.ids().stream().map(String::valueOf).toList()) + ")"));
    }

    /** 空基线：上一例已清理干净，目录行完整。 */
    public void assertBaseline() {
        Stream<Executable> data = DATA_TABLES.stream()
            .map(table -> () -> assertEquals(0L, count("SELECT COUNT(*) FROM " + table), "残留测试数据：" + table));
        Stream<Executable> catalogs = CATALOG_TABLES.entrySet().stream()
            .map(entry -> () -> assertEquals(entry.getValue().ids().size(), count("SELECT COUNT(*) FROM " + entry.getKey()),
                "目录表行数与初始化数据不一致：" + entry.getKey()));
        assertAll("mor_risk_decision 空基线", Stream.concat(data, catalogs));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private static Stream<Long> range(long first, int size) {
        return Stream.iterate(first, id -> id + 1).limit(size);
    }

    /** 目录表的主键列与初始化主键。 */
    record Catalog(String primaryKey, List<Long> ids) {
    }
}
