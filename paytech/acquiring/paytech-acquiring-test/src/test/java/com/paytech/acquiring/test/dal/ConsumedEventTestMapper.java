package com.paytech.acquiring.test.dal;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** paytech-messaging 消费登记两表的只读检查与按聚合清理；只触碰测试发布过的支付尝试。 */
@Mapper
public interface ConsumedEventTestMapper {
    /** 两表存在（缺表时抛出，提示先执行测试 DDL）。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_consumed_event WHERE 1=0) + (SELECT COUNT(*) FROM t_consumed_aggregate_version WHERE 1=0)")
    int checkTables();

    /** 该支付尝试的消费登记行数。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE aggregate_type='PAYMENT_ATTEMPT' AND aggregate_id=#{attemptId}")
    int consumedEvents(@Param("attemptId") String attemptId);

    /** 该支付尝试已处理的最大版本；未处理为 null。 */
    @Select("SELECT last_version FROM t_consumed_aggregate_version WHERE aggregate_type='PAYMENT_ATTEMPT' AND aggregate_id=#{attemptId}")
    Long lastVersion(@Param("attemptId") String attemptId);

    @Delete("DELETE FROM t_consumed_event WHERE aggregate_type='PAYMENT_ATTEMPT' AND aggregate_id=#{attemptId}")
    int deleteConsumedEvents(@Param("attemptId") String attemptId);

    @Delete("DELETE FROM t_consumed_aggregate_version WHERE aggregate_type='PAYMENT_ATTEMPT' AND aggregate_id=#{attemptId}")
    int deleteVersion(@Param("attemptId") String attemptId);
}
