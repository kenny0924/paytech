package com.paytech.risk.test.mapper.control;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 限额档位的测试数据读取与前置数据插入
 * <li>档位、变更记录与 TIER 名单成员是测试数据，由公共数据清理器在每例结束删除；四张 TIER 名单是初始化目录行，不在此修改。</li>
 */
@TestComponent
public class LimitTierTestMapper {
    private final JdbcTemplate jdbc;

    public LimitTierTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 读取 Client 的档位行；没有时为空。 */
    public Map<String, Object> tier(long clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM t_client_limit_tier WHERE client_id=?", clientId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 读取 Client 的变更记录，申请时间与 ID 升序。 */
    public List<Map<String, Object>> changes(long clientId) {
        return jdbc.queryForList("SELECT * FROM t_client_limit_tier_change WHERE client_id=? ORDER BY requested_time, change_id",
            clientId);
    }

    /** 读取 Client 在四张 TIER 名单中的全部成员（名单编码、状态、来源、原因），按创建时间升序。 */
    public List<Map<String, Object>> tierEntries(long clientId) {
        return jdbc.queryForList("SELECT l.list_code, e.entry_id, e.status, e.active_slot, e.source_type, e.reason, "
            + "e.supersedes_entry_id, e.effective_to FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id=e.list_id "
            + "WHERE l.list_purpose='TIER' AND e.subject_type='CLIENT' AND e.subject_key=? ORDER BY e.create_time, e.entry_id",
            Long.toString(clientId));
    }

    /** 读取 Client 当前生效的 TIER 名单编码。 */
    public List<String> activeTierLists(long clientId) {
        return jdbc.queryForList("SELECT l.list_code FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id=e.list_id "
            + "WHERE l.list_purpose='TIER' AND e.subject_type='CLIENT' AND e.subject_key=? AND e.active_slot=1 "
            + "ORDER BY l.list_code", String.class, Long.toString(clientId));
    }

    /** 删除 Client 的档位行、变更记录与 TIER 名单成员（构造 REQ-04 上线前已批准、没有档位的存量 Client）。 */
    public void deleteTierOf(long clientId) {
        jdbc.update("DELETE e FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id=e.list_id "
            + "WHERE l.list_purpose='TIER' AND e.subject_type='CLIENT' AND e.subject_key=?", Long.toString(clientId));
        jdbc.update("DELETE FROM t_client_limit_tier_change WHERE client_id=?", clientId);
        jdbc.update("DELETE FROM t_client_limit_tier WHERE client_id=?", clientId);
    }

    /** 读取名单 ID。 */
    public long listId(String listCode) {
        Long id = jdbc.queryForObject("SELECT list_id FROM t_risk_list WHERE list_code=?", Long.class, listCode);
        return id == null ? 0L : id;
    }

    /**
     * 插入档位行、直接 APPROVED 的建档记录与对应 TIER 名单成员
     *
     * @param fixture 档位前置数据
     */
    public void insertTier(TierFixture fixture) {
        Timestamp now = utc(fixture.effectiveTime());
        jdbc.update("INSERT INTO t_client_limit_tier_change(change_id, client_id, change_type, from_tier, to_tier, status, "
            + "pending_slot, reason, observation_target_days, observation_target_transactions, requested_by, requested_time, "
            + "version, create_time, update_time) VALUES(?,?,'MIGRATION',NULL,?,'APPROVED',NULL,?,?,?,?,?,1,?,?)",
            fixture.changeId(), fixture.clientId(), fixture.tier(), fixture.reason(), fixture.targetDays(),
            fixture.targetTransactions(), 70999L, now, now, now);
        jdbc.update("INSERT INTO t_client_limit_tier(client_limit_tier_id, client_id, tier, previous_tier, client_display_name, "
            + "client_no, client_type, jurisdiction_code, source_type, source_change_id, effective_time, observation_started_time, "
            + "observation_target_days, observation_target_transactions, observation_started_by_change_id, created_by, version, "
            + "create_time, update_time) VALUES(?,?,?,?,?,?,'ORGANIZATION','AE','MIGRATION',?,?,?,?,?,?,70999,1,?,?)",
            fixture.changeId() + 1, fixture.clientId(), fixture.tier(), fixture.previousTier(), fixture.displayName(),
            fixture.clientNo(), fixture.changeId(), now, fixture.observationStartedTime() == null ? null
                : utc(fixture.observationStartedTime()), fixture.targetDays(), fixture.targetTransactions(),
            fixture.observationStartedTime() == null ? null : fixture.changeId(), now, now);
        jdbc.update("INSERT INTO t_risk_list_entry(entry_id, list_id, subject_type, subject_key, source_type, reason, status, "
            + "active_slot, effective_from, version, create_time, update_time) VALUES(?,?,'CLIENT',?,'TIER',?,'ACTIVE',1,?,1,?,?)",
            fixture.changeId() + 2, listId("TIER_" + fixture.tier()), Long.toString(fixture.clientId()),
            "change:" + fixture.changeId(), now, now, now);
    }

    /**
     * 插入一条 Client 日放行汇总（前置历史）
     *
     * @param id 汇总行 ID
     * @param clientId Client ID
     * @param eventCode 放行事件
     * @param currency 币种
     * @param eventDate UTC 日
     * @param amount 金额合计
     * @param count 笔数
     */
    public void insertDailyTotal(long id, long clientId, String eventCode, String currency, LocalDate eventDate,
        BigDecimal amount, int count) {
        Timestamp now = utc(Instant.now());
        jdbc.update("INSERT INTO t_client_allowed_daily_total(daily_total_id, client_id, event_code, currency, event_date, amount, "
            + "event_count, create_time, update_time) VALUES(?,?,?,?,?,?,?,?,?)", id, clientId, eventCode, currency, eventDate,
            amount, count, now, now);
    }

    /** 读取 Client 的日放行汇总行，按事件、币种、日期排序。 */
    public List<Map<String, Object>> dailyTotals(long clientId) {
        return jdbc.queryForList("SELECT event_code, currency, event_date, amount, event_count FROM t_client_allowed_daily_total "
            + "WHERE client_id=? ORDER BY event_code, currency, event_date", clientId);
    }

    /** 本用例档位前置数据的 UTC 时间。 */
    static Timestamp utc(Instant value) {
        return Timestamp.valueOf(value.atOffset(ZoneOffset.UTC).toLocalDateTime());
    }

    /**
     * 档位前置数据
     *
     * @param clientId Client ID
     * @param changeId 建档记录 ID；档位行与名单成员 ID 依次加一、加二
     * @param tier 档位 Code
     * @param previousTier 受限前档位 Code；可空
     * @param displayName Client 名称
     * @param clientNo Client 编号
     * @param reason 建档理由
     * @param effectiveTime 生效时间
     * @param observationStartedTime 观察期开始时间；非 OBSERVATION 为空
     * @param targetDays 观察期目标天数；可空
     * @param targetTransactions 观察期目标笔数；可空
     */
    public record TierFixture(long clientId, long changeId, String tier, String previousTier, String displayName,
        String clientNo, String reason, Instant effectiveTime, Instant observationStartedTime, Integer targetDays,
        Integer targetTransactions) { }
}
