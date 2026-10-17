package com.paytech.foundation.compensation.test.mapper;

import org.apache.ibatis.annotations.*;

import com.paytech.foundation.compensation.api.CompensationRecoveryAudit;

/**
 * 补偿组件自动测试共用的数据准备、清理与查询 Mapper。
 *
 * @author Codex
 */
@Mapper
public interface TestMapper {

    @Delete("DELETE FROM t_compensation_recovery_audit")
    int deleteAllRecoveryAudits();

    @Delete("DELETE FROM t_compensation_event")
    int deleteAllCompensationEvents();

    @Delete("DELETE FROM t_test_business_source")
    int deleteAllBusinessSources();

    @Insert("""
        INSERT INTO t_test_business_source(event_key, sub_event_type, business_value, processed_count)
        VALUES (#{eventKey}, #{subEventType}, #{businessValue}, 0)
        """)
    int insertBusinessSource(@Param("eventKey") String eventKey, @Param("subEventType") String subEventType,
        @Param("businessValue") String businessValue);

    @Select("""
        SELECT business_value
        FROM t_test_business_source
        WHERE event_key = #{eventKey}
          AND sub_event_type = #{subEventType}
        """)
    String findBusinessValue(@Param("eventKey") String eventKey, @Param("subEventType") String subEventType);

    @Update("""
        UPDATE t_test_business_source
        SET processed_count = 1
        WHERE event_key = #{eventKey}
          AND sub_event_type = #{subEventType}
          AND processed_count = 0
        """)
    int markBusinessSourceProcessed(@Param("eventKey") String eventKey, @Param("subEventType") String subEventType);

    @Select("""
        SELECT processed_count
        FROM t_test_business_source
        WHERE event_key = #{eventKey}
          AND sub_event_type = #{subEventType}
        """)
    int findProcessedCount(@Param("eventKey") String eventKey, @Param("subEventType") String subEventType);

    @Insert("""
        INSERT INTO t_compensation_event(id, event_key, event_type, sub_event_type)
        VALUES (#{eventId}, #{eventKey}, #{eventType}, #{subEventType})
        """)
    int insertCompensationEvent(@Param("eventId") long eventId, @Param("eventKey") String eventKey,
        @Param("eventType") String eventType, @Param("subEventType") String subEventType);

    @Insert("""
        INSERT INTO t_compensation_event(
            id, event_key, event_type, sub_event_type, payload_json, execute_status, execute_count,
            next_execute_at, lock_owner, lock_until, last_error_code, last_error_summary,
            lock_version, create_time, update_time
        ) VALUES (
            #{eventId}, #{eventKey}, #{eventType}, #{subEventType}, NULL, 'FAILED', #{executeCount},
            UTC_TIMESTAMP(6), NULL, NULL, #{lastErrorCode}, #{lastErrorSummary},
            #{lockVersion}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
        )
        """)
    int insertFailedCompensationEvent(@Param("eventId") long eventId, @Param("eventKey") String eventKey,
        @Param("eventType") String eventType, @Param("subEventType") String subEventType,
        @Param("executeCount") int executeCount, @Param("lastErrorCode") String lastErrorCode,
        @Param("lastErrorSummary") String lastErrorSummary, @Param("lockVersion") long lockVersion);

    @Select("SELECT COUNT(*) FROM t_compensation_event")
    int countCompensationEvents();

    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE id = #{eventId}")
    int countCompensationEventById(@Param("eventId") long eventId);

    @Select("SELECT execute_status FROM t_compensation_event WHERE id = #{eventId}")
    String findCompensationEventStatus(@Param("eventId") long eventId);

    @Select("SELECT execute_count FROM t_compensation_event WHERE id = #{eventId}")
    int findCompensationEventExecuteCount(@Param("eventId") long eventId);

    @Select("SELECT last_error_code FROM t_compensation_event WHERE id = #{eventId}")
    String findCompensationEventErrorCode(@Param("eventId") long eventId);

    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE id = #{eventId} AND payload_json IS NULL")
    int countCompensationEventWithoutPayload(@Param("eventId") long eventId);

    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE id IN (#{firstEventId}, #{secondEventId})")
    int countCompensationEventsByIds(@Param("firstEventId") long firstEventId,
        @Param("secondEventId") long secondEventId);

    @Insert("""
        INSERT INTO t_compensation_recovery_audit(
            event_id, event_key, event_type, sub_event_type, actor, reason, ticket, previous_error_code,
            previous_error_summary, previous_execute_count, previous_lock_version, new_lock_version, create_time
        ) VALUES (
            #{audit.eventId}, #{audit.eventKey}, #{audit.eventType}, #{audit.subEventType}, #{audit.actor},
            #{audit.reason}, #{audit.ticket}, #{audit.previousErrorCode}, #{audit.previousErrorSummary},
            #{audit.previousExecuteCount}, #{audit.previousLockVersion}, #{audit.newLockVersion}, UTC_TIMESTAMP(6)
        )
        """)
    int insertRecoveryAudit(@Param("audit") CompensationRecoveryAudit audit);

    @Select("""
        SELECT COUNT(*)
        FROM t_compensation_recovery_audit
        WHERE event_id = #{eventId}
          AND event_key = #{eventKey}
        """)
    int countRecoveryAudits(@Param("eventId") long eventId, @Param("eventKey") String eventKey);
}
