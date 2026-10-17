package com.paytech.foundation.messaging.consume;

import java.util.Objects;
import java.util.OptionalLong;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.messaging.api.ConsumedEventStore;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.observability.ConsumeOutcome;

/**
 * 消费幂等与顺序判定（design/08 §4 第 1–4 步），在宿主本地事务内执行。
 * <li>1：事件 ID 已登记 → {@link ConsumeOutcome#REPLAYED}。</li>
 * <li>2：版本不大于已处理版本 → {@link ConsumeOutcome#STALE}，回滚本次登记。</li>
 * <li>3：严格有序且版本大于已处理版本 + 1 → {@link ConsumeOutcome#GAP}，回滚本次登记。</li>
 * <li>4：调用 Handler，推进版本，与业务写入同一事务提交 → {@link ConsumeOutcome#HANDLED}。</li>
 * <li>聚合首次出现（无版本记录）时以本事件为基线直接处理。</li>
 *
 * @author Claude
 */
public final class IdempotentConsumeInterceptor {

    private final ConsumedEventStore store;
    private final TransactionTemplate transaction;

    /**
     * 创建拦截器。
     *
     * @param store 消费存储，与 Handler 使用同一 DataSource
     * @param transaction 宿主事务模板，传播行为必须为 REQUIRED
     */
    public IdempotentConsumeInterceptor(ConsumedEventStore store, TransactionTemplate transaction) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
    }

    /**
     * 在一个本地事务内完成判定与业务处理；Handler 异常使事务回滚并原样抛出。
     *
     * @param event 已校验的信封
     * @param registration 该事件类型的 Handler 注册
     * @return 判定结果，{@link ConsumeOutcome#DEAD} 不会由本方法返回
     */
    public ConsumeOutcome consume(DomainEvent event, DomainEventHandlerRegistry.Registration registration) {
        return transaction.execute(status -> {
            if (!store.recordIfAbsent(event)) {
                return ConsumeOutcome.REPLAYED;
            }
            OptionalLong lastVersion = store.lastVersion(event.aggregateKey());
            if (lastVersion.isPresent() && event.aggregateVersion() <= lastVersion.getAsLong()) {
                status.setRollbackOnly();
                return ConsumeOutcome.STALE;
            }
            if (registration.strictOrder() && lastVersion.isPresent()
                && event.aggregateVersion() > lastVersion.getAsLong() + 1) {
                status.setRollbackOnly();
                return ConsumeOutcome.GAP;
            }
            registration.consumer().consume(event);
            store.advance(event.aggregateKey(), event.aggregateVersion());
            return ConsumeOutcome.HANDLED;
        });
    }
}
