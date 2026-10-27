package com.paytech.compliance.test.clear;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * mor_compliance 全表清理器（design/10 §3.1、§4.2）：触碰的表集合等于本模块 init/ddl.sql 的表集合（含结算控制 3 表与 t_consumed_event）。
 * <li>各 Mocker 定向清理之后兜底删除本库全部行；测试库不执行初始化 DML，程序由 ProgramPackMocker 按用例插入。</li>
 * <li>{@link #assertBaseline()} 供全局 BeforeEach 断言空基线。</li>
 */
@TestComponent
public class ComplianceDataCleaner implements Clear {
    /** 本库全部表（= init/ddl.sql 的 CREATE TABLE 集合）。 */
    public static final List<String> TABLES = List.of("t_dd_program", "t_dd_program_version", "t_dd_program_requirement",
        "t_dd_program_requirement_doc_type", "t_dd_program_check", "t_dd_program_screening", "t_due_diligence_case",
        "t_dd_case_subject", "t_dd_case_classification", "t_dd_case_dependency", "t_dd_requirement", "t_dd_evidence_ref",
        "t_dd_case_state_change", "t_dd_party_snapshot", "t_dd_check", "t_screening_run", "t_screening_hit",
        "t_screening_hit_list", "t_screening_resolution", "t_dd_risk_factor", "t_dd_return_error", "t_qualification_decision",
        "t_role_qualification", "t_qualification_state_change", "t_settlement_control", "t_settlement_control_scope",
        "t_settle_control_state_change", "t_compensation_event", "t_consumed_event");

    private final JdbcTemplate jdbc;

    public ComplianceDataCleaner(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void clear() {
        for (String table : TABLES) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    /** 空基线：上一例已清理干净。 */
    public void assertBaseline() {
        assertAll("mor_compliance 空基线", TABLES.stream().<Executable>map(table -> () -> assertEquals(0L,
            jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class), "残留测试数据：" + table)));
    }
}
