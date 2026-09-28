package com.paytech.risk.test.decision;

import org.springframework.jdbc.core.JdbcTemplate;

/** 仅清理本用例创建的 RCO16/RCO17 命名空间字典，不触及运营目录。 */
public final class RiskDictionaryTestMapper {
    private final JdbcTemplate jdbc;
    /** 注入仅供本地测试证据使用的数据库访问入口。 */
    public RiskDictionaryTestMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** 仅清理带本轮测试前缀的精确编码，禁止删除运营字典。 */
    public void deleteOwned(String code) {
        if (code == null || !code.matches("RCO(?:16|17)_[A-Z0-9_]{1,50}")) throw new IllegalArgumentException("非本测试命名空间");
        jdbc.update("DELETE FROM t_rule_reject_reason WHERE reason_code = ?", code);
        jdbc.update("DELETE FROM t_rule_subject_tag WHERE tag_code = ?", code);
    }
    /** 按精确编码统计两类字典记录，供 HTTP 副作用断言。 */
    public long countOwned(String code) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_rule_reject_reason WHERE reason_code = ?", Long.class, code)
            + jdbc.queryForObject("SELECT COUNT(*) FROM t_rule_subject_tag WHERE tag_code = ?", Long.class, code);
    }
}
