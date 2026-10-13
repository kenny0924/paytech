package com.paytech.risk.test.mapper.evaluation;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Manual Payout 资格评估相关表的测试只读查询
 * <li>按请求 inputHash 定位，仅统计本用例输入可能产生的记录，不读取或依赖其他测试的数据。</li>
 *
 * @author Kenny
 */
@TestComponent
public class SettlementRiskAssessmentTestMapper {

    private final JdbcTemplate jdbc;

    public SettlementRiskAssessmentTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 统计以该输入摘要冻结的结算风险评估快照。 */
    public int countAssessmentsByInputHash(String inputHash) {
        return count("SELECT COUNT(*) FROM t_settlement_risk_assessment WHERE input_hash=?", inputHash);
    }

    /** 统计以该输入摘要执行的决策评估。 */
    public int countEvaluationsByInputHash(String inputHash) {
        return count("SELECT COUNT(*) FROM t_decision_evaluation WHERE input_snapshot_hash=?", inputHash);
    }

    private int count(String sql, String inputHash) {
        Integer count = jdbc.queryForObject(sql, Integer.class, inputHash);
        return count == null ? 0 : count;
    }
}
