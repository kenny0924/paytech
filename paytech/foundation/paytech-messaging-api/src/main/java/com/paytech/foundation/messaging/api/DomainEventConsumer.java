package com.paytech.foundation.messaging.api;

/**
 * 领域事件的业务消费者，位于 {@code -core}，实现类必须标注 {@link DomainEventHandler}。
 * <li>由消费拦截器在宿主本地事务内调用：只做本服务数据库读写，不调用外部服务、Redis 或 MQ。</li>
 * <li>拦截器已完成事件 ID 去重与聚合版本判定；Handler 仍按业务幂等键做业务层防重。</li>
 * <li>抛出异常时事务回滚、消息重新投递，超过投递上限进入 DLQ。</li>
 *
 * @author Claude
 */
public interface DomainEventConsumer {

    /**
     * 在本地事务内处理一个事件。
     *
     * @param event 已通过信封与哈希校验的事件
     */
    void consume(DomainEvent event);
}
