package com.paytech.foundation.messaging.test.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * messaging 测试的数据准备、清理与只读断言；只操作 messaging 自有的三张表。
 */
@Mapper
public interface TestMapper {

    @Insert("""
        INSERT INTO t_test_handled_event(event_id, event_type, aggregate_id, aggregate_version)
        VALUES (#{eventId}, #{eventType}, #{aggregateId}, #{aggregateVersion})
        """)
    int insertHandledEvent(@Param("eventId") String eventId, @Param("eventType") String eventType,
        @Param("aggregateId") String aggregateId, @Param("aggregateVersion") long aggregateVersion);

    @Select("""
        SELECT aggregate_version FROM t_test_handled_event
        WHERE aggregate_id = #{aggregateId}
        ORDER BY id
        """)
    List<Long> findHandledVersions(@Param("aggregateId") String aggregateId);

    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE event_id = #{eventId}")
    int countConsumedEvent(@Param("eventId") String eventId);

    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE aggregate_id = #{aggregateId}")
    int countConsumedEventsOfAggregate(@Param("aggregateId") String aggregateId);

    @Select("""
        SELECT last_version FROM t_consumed_aggregate_version
        WHERE aggregate_type = #{aggregateType} AND aggregate_id = #{aggregateId}
        """)
    Long findLastVersion(@Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId);

    @Delete("DELETE FROM t_test_handled_event")
    int deleteHandledEvents();

    @Delete("DELETE FROM t_consumed_event")
    int deleteConsumedEvents();

    @Delete("DELETE FROM t_consumed_aggregate_version")
    int deleteConsumedAggregateVersions();
}
