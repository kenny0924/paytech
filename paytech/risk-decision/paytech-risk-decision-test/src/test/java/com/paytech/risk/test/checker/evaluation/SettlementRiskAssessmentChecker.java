package com.paytech.risk.test.checker.evaluation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.risk.test.mapper.evaluation.SettlementRiskAssessmentTestMapper;

/**
 * Manual Payout 资格评估的数据库 Checker
 * <li>从真实库确认一次请求是否留下评估或资格快照；只观察，不写入也不清理。</li>
 *
 * @author Kenny
 */
@TestComponent
public class SettlementRiskAssessmentChecker {

    private final SettlementRiskAssessmentTestMapper mapper;

    public SettlementRiskAssessmentChecker(SettlementRiskAssessmentTestMapper mapper) {
        this.mapper = mapper;
    }

    /** Fail-Closed 或被拒绝的请求不得冻结资格快照，也不得产生决策评估。 */
    public void assertNotRecorded(String inputHash) {
        assertAll("Manual Payout 评估不得落库：" + inputHash,
            () -> assertEquals(0, mapper.countAssessmentsByInputHash(inputHash), "存在结算风险评估快照"),
            () -> assertEquals(0, mapper.countEvaluationsByInputHash(inputHash), "存在决策评估记录"));
    }
}
