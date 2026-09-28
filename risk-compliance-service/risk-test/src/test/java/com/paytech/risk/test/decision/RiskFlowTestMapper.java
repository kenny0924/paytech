package com.paytech.risk.test.decision;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/** 只观察和清理每个测试显式拥有的流程，不迁移结构或修改初始化目录。 */
public final class RiskFlowTestMapper {
    private final JdbcTemplate jdbc;

    public RiskFlowTestMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> root(String code) {
        owned(code);
        return jdbc.queryForMap("SELECT * FROM t_decision_flow WHERE flow_code=?", code);
    }

    public long versionCount(String code) {
        owned(code);
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_decision_flow_version v JOIN t_decision_flow f ON f.flow_id=v.flow_id WHERE f.flow_code=?", Long.class, code);
    }

    public long rootCount(String code) {
        owned(code);
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_decision_flow WHERE flow_code=?", Long.class, code);
    }

    public void clean(String code) {
        owned(code);
        List<Long> ids = jdbc.queryForList("SELECT v.flow_version_id FROM t_decision_flow_version v JOIN t_decision_flow f ON f.flow_id=v.flow_id WHERE f.flow_code=?", Long.class, code);
        for (Long id : ids) {
            jdbc.update("DELETE FROM t_decision_flow_edge WHERE flow_version_id=?", id);
            jdbc.update("DELETE FROM t_decision_flow_node WHERE flow_version_id=?", id);
            jdbc.update("DELETE FROM t_decision_flow_version WHERE flow_version_id=?", id);
        }
        jdbc.update("DELETE FROM t_decision_flow WHERE flow_code=?", code);
    }

    private static void owned(String code) {
        if (code == null || !code.matches("AUTO_[A-F0-9]{32}")) throw new IllegalArgumentException("必须为本测试明确拥有的编码");
    }
}
