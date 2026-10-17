package com.paytech.foundation.messaging.api;

import java.util.OptionalLong;

/**
 * 消费端幂等与聚合版本的本地存储（{@code t_consumed_event}、{@code t_consumed_aggregate_version}，design/08 §4）。
 * <li>所有方法必须在宿主业务事务内调用，与业务写入同一事务提交或回滚。</li>
 *
 * @author Claude
 */
public interface ConsumedEventStore {

    /**
     * 登记已消费事件；事件 ID 已存在时不写入。
     *
     * @param event 正在消费的事件
     * @return 本次新登记返回 true；已存在（重放）返回 false
     */
    boolean recordIfAbsent(DomainEvent event);

    /**
     * 查询该聚合已消费的最大版本。
     *
     * @param aggregateKey 聚合身份
     * @return 已消费的最大版本；从未消费时为空
     */
    OptionalLong lastVersion(AggregateKey aggregateKey);

    /**
     * 把该聚合的已消费版本推进到给定版本；只增不减。
     *
     * @param aggregateKey 聚合身份
     * @param aggregateVersion 本次已处理的版本
     */
    void advance(AggregateKey aggregateKey, long aggregateVersion);
}
