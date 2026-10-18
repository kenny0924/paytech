package com.paytech.foundation.messaging.core.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.paytech.foundation.messaging.ordering.AggregateKeySequencer;
import com.paytech.foundation.messaging.ordering.AggregateSerialExecutor;

/**
 * 发布侧按聚合串行（design/08 §3）：{@code messaging.producer.sequencer.enabled=true} 且宿主引入
 * {@code paytech-compensation-event} 时，提供 {@link AggregateKeySequencer} 作为补偿 Dispatcher 的执行编排器。
 * <li>补偿组件不依赖本模块，只通过其公开端口接收该 Bean；扫描、Claim/Lease 与分片不变。</li>
 *
 * @author Claude
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.paytech.foundation.compensation.api.CompensationExecutionSequencer")
@ConditionalOnProperty(prefix = "messaging.producer.sequencer", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingSequencerAutoConfiguration {

    private static final int MAX_LANES = 256;

    /**
     * 按聚合串行的执行编排器；关闭时停止全部线程。
     *
     * @param properties 配置
     * @return 编排器
     */
    @Bean(destroyMethod = "close")
    public AggregateKeySequencer aggregateKeySequencer(MessagingProperties properties) {
        MessagingProperties.Sequencer sequencer = properties.getProducer().getSequencer();
        if (sequencer.getLanes() < 1 || sequencer.getLanes() > MAX_LANES) {
            throw new IllegalStateException("messaging.producer.sequencer.lanes 必须在 1.." + MAX_LANES + " 之间");
        }
        if (sequencer.getQueueCapacity() < 1) {
            throw new IllegalStateException("messaging.producer.sequencer.queue-capacity 必须为正");
        }
        return new AggregateKeySequencer(new AggregateSerialExecutor(sequencer.getLanes(), sequencer.getQueueCapacity(),
            "messaging-sequencer-"));
    }
}
