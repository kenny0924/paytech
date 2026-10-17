package com.paytech.foundation.messaging.api;

/**
 * 领域事件发布端口；{@code -core} 的补偿 Handler 通过它把信封可靠送到 Broker（design/08 §1）。
 * <li>同步调用：Broker 确认接收且消息已路由到至少一个队列才返回。</li>
 * <li>只能在数据库事务之外调用（事务公理）；失败由补偿事件重试。</li>
 *
 * @author Claude
 */
public interface DomainEventPublisher {

    /**
     * 发布一个领域事件，等待 Publisher Confirm 并检查未被退回。
     *
     * @param event 已构造的信封
     * @throws DomainEventPublishException 序列化失败、超出大小上限、Broker 拒绝、退回或确认超时
     */
    void publish(DomainEvent event);
}
