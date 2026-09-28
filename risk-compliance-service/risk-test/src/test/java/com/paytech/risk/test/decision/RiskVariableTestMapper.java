package com.paytech.risk.test.decision;

import java.util.Collection;
import org.springframework.jdbc.core.JdbcTemplate;

/** 只清理由当前测试登记的变量和成员；不修改初始化目录。 */
public final class RiskVariableTestMapper {
    private final JdbcTemplate jdbc;

    public RiskVariableTestMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void deleteOwned(Collection<String> codes) {
        for (String code : codes) {
            if (!code.matches("AUTO_VAR_[A-F0-9]{32}")) throw new IllegalArgumentException("只能清理独占测试编码");
            jdbc.update("DELETE FROM t_risk_variable_member WHERE variable_code=?", code);
        }
        for (String code : codes) jdbc.update("DELETE FROM t_risk_variable WHERE variable_code=?", code);
    }
}
