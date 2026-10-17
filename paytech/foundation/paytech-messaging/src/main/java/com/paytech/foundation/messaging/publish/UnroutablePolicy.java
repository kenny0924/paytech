package com.paytech.foundation.messaging.publish;

/**
 * 发布端对“Broker 已确认但未路由到任何队列”（mandatory 退回）的处理策略，配置键 {@code messaging.publisher.unroutable-policy}。
 *
 * @author Claude
 */
public enum UnroutablePolicy {

    /** 默认：抛出 {@code DomainEventPublishException}，由补偿任务重试。 */
    FAIL,

    /**
     * 过渡期：记 WARN 与 {@code messaging.published{outcome=unroutable}} 后视为发布成功。
     * <li>只用于发布方交换机尚无订阅方的过渡阶段（如重构第 08 步接入订阅者之前），订阅者接入后必须改回 FAIL。</li>
     */
    ACK_AND_WARN
}
