package com.paytech.foundation.messaging.persistence.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * {@code t_consumed_event} 与 {@code t_consumed_aggregate_version} 的 MyBatis Mapper；SQL 位于
 * {@code META-INF/mybatis/messaging/ConsumedEventMapper.xml}。
 *
 * @author Claude
 */
public interface ConsumedEventMapper {

    /**
     * 登记已消费事件，事件 ID 已存在时忽略。
     *
     * @param event 待登记的事件
     * @return 新登记 1，已存在 0
     */
    int insertIfAbsent(@Param("event") ConsumedEvent event);

    /**
     * 查询聚合已处理的最大版本。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合标识
     * @return 最大版本；无记录为 null
     */
    Long selectLastVersion(@Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId);

    /**
     * 推进聚合版本，只增不减。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合标识
     * @param aggregateVersion 新版本
     * @return 受影响行数
     */
    int upsertLastVersion(@Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId,
        @Param("aggregateVersion") long aggregateVersion);
}
