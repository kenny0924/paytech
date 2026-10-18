package com.paytech.foundation.messaging.consume;

import java.util.Objects;
import java.util.OptionalLong;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.ConsumedEventRepository;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.observability.ConsumeOutcome;

/**
 * 消费幂等与顺序判定（design/08 §4 第 1–4 步），在宿主本地事务内执行。
 * <li>1：事件 ID 已登记 → {@link ConsumeOutcome#REPLAYED}。</li>
 * <li>2：版本不大于已处理版本 → {@link ConsumeOutcome#STALE}，回滚本次登记。</li>
 * <li>3：严格有序且版本大于已处理版本 + 1 → {@link ConsumeOutcome#GAP}，回滚本次登记。</li>
 * <li>4：调用 Handler，推进版本，与业务写入同一事务提交 → {@link ConsumeOutcome#HANDLED}。</li>
 * <li>聚合首次出现（无版本记录）时以本事件为基线直接处理。</li>
 * <li>Handler 声明 {@link ConsumeTransaction#HANDLER_MANAGED} 时，判定与登记分别在 Handler 前后的短事务中完成，Handler 在事务外调用。</li>
 *
 * @author Claude
 */
public final class IdempotentConsumeInterceptor {

    private final ConsumedEventRepository repository;
    private final TransactionTemplate transaction;

    /**
     * 创建拦截器。
     *
     * @param repository 消费记录仓储，与 Handler 使用同一 DataSource
     * @param transaction 宿主事务模板，传播行为必须为 REQUIRED
     */
    public IdempotentConsumeInterceptor(ConsumedEventRepository repository, TransactionTemplate transaction) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
    }

    /**
     * 完成判定与业务处理：默认在一个本地事务内；Handler 异常使事务回滚（或不登记）并原样抛出。
     *
     * @param event 已校验的信封
     * @param registration 该事件类型的 Handler 注册
     * @return 判定结果，{@link ConsumeOutcome#DEAD} 不会由本方法返回
     */
    public ConsumeOutcome consume(DomainEvent event, DomainEventHandlerRegistry.Registration registration) {
        if (registration.transaction() == ConsumeTransaction.HANDLER_MANAGED) {
            return consumeHandlerManaged(event, registration);
        }
        return transaction.execute(status -> {
            if (!repository.recordIfAbsent(event)) {
                return ConsumeOutcome.REPLAYED;
            }
            ConsumeOutcome skipped = skippedOutcome(event, registration);
            if (skipped != null) {
                status.setRollbackOnly();
                return skipped;
            }
            registration.consumer().consume(event);
            repository.advance(event.aggregateKey(), event.aggregateVersion());
            return ConsumeOutcome.HANDLED;
        });
    }

    /**
     * {@link ConsumeTransaction#HANDLER_MANAGED}：短事务判定（登记后回滚，只用于判定）→ 事务外调用 Handler → 短事务登记并推进版本。
     * <li>同一分区单活消费者串行处理，判定与登记之间不会有同一事件的并发消费。</li>
     * <li>Handler 成功、登记前中断时重投会再次调用 Handler，由其业务幂等返回重复结果后完成登记。</li>
     */
    private ConsumeOutcome consumeHandlerManaged(DomainEvent event, DomainEventHandlerRegistry.Registration registration) {
        ConsumeOutcome precheck = transaction.execute(status -> {
            if (!repository.recordIfAbsent(event)) {
                return ConsumeOutcome.REPLAYED;
            }
            status.setRollbackOnly();
            return skippedOutcome(event, registration);
        });
        if (precheck != null) {
            return precheck;
        }
        registration.consumer().consume(event);
        transaction.executeWithoutResult(status -> {
            repository.recordIfAbsent(event);
            repository.advance(event.aggregateKey(), event.aggregateVersion());
        });
        return ConsumeOutcome.HANDLED;
    }

    /** 旧版本或严格有序缺口时返回对应结果；可以处理时返回 null。 */
    private ConsumeOutcome skippedOutcome(DomainEvent event, DomainEventHandlerRegistry.Registration registration) {
        OptionalLong lastVersion = repository.lastVersion(event.aggregateKey());
        if (lastVersion.isEmpty()) {
            return null;
        }
        if (event.aggregateVersion() <= lastVersion.getAsLong()) {
            return ConsumeOutcome.STALE;
        }
        if (registration.strictOrder() && event.aggregateVersion() > lastVersion.getAsLong() + 1) {
            return ConsumeOutcome.GAP;
        }
        return null;
    }
}
