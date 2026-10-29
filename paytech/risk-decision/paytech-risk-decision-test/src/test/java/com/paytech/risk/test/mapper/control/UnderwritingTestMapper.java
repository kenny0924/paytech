package com.paytech.risk.test.mapper.control;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 承保版本与自动承保规则的测试数据读取、准备与清理
 * <li>规则初始化行（290001 / 290101）是保留数据：只在本用例内改状态，由 {@code UnderwritingRuleMocker} 恢复。</li>
 *
 * @author Claude
 */
@TestComponent
public class UnderwritingTestMapper {
    /** 初始化 ACTIVE 规则版本 ID。 */
    public static final long INITIAL_RULE_VERSION_ID = 290001L;

    /** 初始化 USD 阈值 ID。 */
    public static final long INITIAL_THRESHOLD_ID = 290101L;

    private final JdbcTemplate jdbc;

    public UnderwritingTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** TINYINT(1) 列的布尔值：驱动按 BIT 返回 Boolean，其他整数按 1 为真。 */
    public static boolean flag(Object value) {
        return value instanceof Boolean bool ? bool : value != null && ((Number) value).intValue() == 1;
    }

    /** 读取一个承保版本行。 */
    public Map<String, Object> underwriting(long underwritingId) {
        return jdbc.queryForMap("SELECT * FROM t_client_underwriting WHERE underwriting_id=?", underwritingId);
    }

    /** 读取 Client 的全部版本行，版本号升序。 */
    public List<Map<String, Object>> underwritingsOf(long clientId) {
        return jdbc.queryForList("SELECT * FROM t_client_underwriting WHERE client_id=? ORDER BY version_no", clientId);
    }

    /** 初始化规则退役：释放 ACTIVE 占位。 */
    public void retireInitialRule() {
        jdbc.update("UPDATE t_underwriting_rule_version SET status='RETIRED', active_slot=NULL WHERE rule_version_id=?",
            INITIAL_RULE_VERSION_ID);
    }

    /** 恢复初始化规则为 ACTIVE。 */
    public void restoreInitialRule() {
        jdbc.update("UPDATE t_underwriting_rule_version SET status='ACTIVE', active_slot=1 WHERE rule_version_id=?",
            INITIAL_RULE_VERSION_ID);
    }

    /** 插入一个 ACTIVE 测试规则版本及其单个币种阈值。 */
    public void insertActiveRule(long ruleVersionId, int versionNo, String excludedMccCodes, long thresholdId,
        String currency, String limit) {
        jdbc.update("INSERT INTO t_underwriting_rule_version(rule_version_id, version_no, status, active_slot, "
            + "excluded_mcc_codes, activated_time, create_time, update_time) VALUES(?,?,'ACTIVE',1,?,UTC_TIMESTAMP(6),"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleVersionId, versionNo, excludedMccCodes);
        jdbc.update("INSERT INTO t_underwriting_rule_threshold(threshold_id, rule_version_id, currency, monthly_volume_limit, "
            + "create_time, update_time) VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", thresholdId, ruleVersionId,
            currency, new java.math.BigDecimal(limit));
    }

    /** 删除非初始化的规则版本与阈值。 */
    public void deleteTestRules() {
        jdbc.update("DELETE FROM t_underwriting_rule_threshold WHERE threshold_id<>?", INITIAL_THRESHOLD_ID);
        jdbc.update("DELETE FROM t_underwriting_rule_version WHERE rule_version_id<>?", INITIAL_RULE_VERSION_ID);
    }

    /** 删除全部承保版本行（测试专用库）。 */
    public void deleteUnderwritings() {
        jdbc.update("DELETE FROM t_client_underwriting");
    }

    /**
     * 只读执行 GOLIVE_UNDERWRITING_APPROVED 模板正文（#{clientId} 换为绑定参数）
     *
     * @param templateSql 交付的模板正文
     * @param clientId Client ID
     * @return 模板返回的全部行
     */
    public List<Map<String, Object>> runTemplate(String templateSql, long clientId) {
        String sql = templateSql.replace("#{clientId}", "?");
        int parameters = sql.length() - sql.replace("?", "").length();
        Object[] arguments = new Object[parameters];
        java.util.Arrays.fill(arguments, clientId);
        return jdbc.queryForList(sql, arguments);
    }
}
