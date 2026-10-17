package com.paytech.foundation.messaging.persistence.repository;

import java.util.Objects;
import java.util.OptionalLong;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.ConsumedEventRepository;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.persistence.mapper.ConsumedEvent;
import com.paytech.foundation.messaging.persistence.mapper.ConsumedEventMapper;

/**
 * 基于宿主 DataSource 与 MyBatis 的 {@link ConsumedEventRepository}；只能加入宿主已开启的本地事务。
 *
 * @author Claude
 */
public final class MyBatisConsumedEventRepository implements ConsumedEventRepository {

    private final ConsumedEventMapper mapper;

    /**
     * 创建仓储。
     *
     * @param mapper 已加载 XML 的 Mapper
     */
    public MyBatisConsumedEventRepository(ConsumedEventMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * 以事件 ID 主键登记；主键冲突视为重放。
     */
    @Override
    public boolean recordIfAbsent(DomainEvent event) {
        requireTransaction();
        ConsumedEvent row = new ConsumedEvent(event.eventId(), event.eventType(), event.aggregateType(),
            event.aggregateId(), event.aggregateVersion());
        return mapper.insertIfAbsent(row) == 1;
    }

    /**
     * 读取聚合当前已处理版本。
     */
    @Override
    public OptionalLong lastVersion(AggregateKey aggregateKey) {
        requireTransaction();
        Long version = mapper.selectLastVersion(aggregateKey.aggregateType(), aggregateKey.aggregateId());
        return version == null ? OptionalLong.empty() : OptionalLong.of(version);
    }

    /**
     * 以 {@code GREATEST} 推进，保证版本只增不减。
     */
    @Override
    public void advance(AggregateKey aggregateKey, long aggregateVersion) {
        requireTransaction();
        mapper.upsertLastVersion(aggregateKey.aggregateType(), aggregateKey.aggregateId(), aggregateVersion);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("ConsumedEventRepository requires an active local transaction");
        }
    }
}
