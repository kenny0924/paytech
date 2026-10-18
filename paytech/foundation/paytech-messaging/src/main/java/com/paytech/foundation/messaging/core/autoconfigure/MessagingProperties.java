package com.paytech.foundation.messaging.core.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code messaging.*} 配置。
 *
 * @author Claude
 */
@ConfigurationProperties(prefix = "messaging", ignoreUnknownFields = false)
public class MessagingProperties {

    /**
     * 是否启用消息组件。
     * <li>默认 true；false 时不创建任何 Bean。</li>
     */
    private boolean enabled = true;

    /** 发布端配置。 */
    private final Producer producer = new Producer();

    /** 发布结果处理配置。 */
    private final Publisher publisher = new Publisher();

    /** 消费端配置。 */
    private final Consumer consumer = new Consumer();

    /**
     * 按发布服务名覆盖的订阅参数。
     * <li>键为发布服务名，必须是本服务 Handler 实际订阅的发布方；未列出的发布方使用默认值。</li>
     */
    private final Map<String, Subscription> consumers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Producer getProducer() {
        return producer;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public Map<String, Subscription> getConsumers() {
        return consumers;
    }

    /** 发布端配置。 */
    public static class Producer {

        /**
         * 本服务作为发布方的服务名（01 §3）。
         * <li>设置后声明 {@code <service>.events} 并创建 {@code DomainEventPublisher}；未设置则不发布。</li>
         */
        private String service;

        /**
         * Publisher Confirm 最长等待时间。
         * <li>默认 10 秒；超时按发布失败处理，由补偿任务重试。</li>
         */
        private Duration confirmTimeout = Duration.ofSeconds(10);

        /** 补偿事件发布任务的按聚合串行。 */
        private final Sequencer sequencer = new Sequencer();

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public Duration getConfirmTimeout() {
            return confirmTimeout;
        }

        public void setConfirmTimeout(Duration confirmTimeout) {
            this.confirmTimeout = confirmTimeout;
        }

        public Sequencer getSequencer() {
            return sequencer;
        }
    }

    /** 补偿事件发布任务的按聚合串行（design/08 §3）。 */
    public static class Sequencer {

        /**
         * 是否以按聚合串行的编排器替换补偿 Dispatcher 的默认直接执行。
         * <li>默认 false；需要宿主同时引入 {@code paytech-compensation-event}。同一聚合的任务按 Claim 顺序在固定线程执行。</li>
         */
        private boolean enabled;

        /**
         * 串行线程数。
         * <li>默认 4，范围 1..256；同时也是本实例补偿任务的最大并行聚合数。</li>
         */
        private int lanes = 4;

        /**
         * 每条线程的排队上限。
         * <li>默认 256；队列满时任务按可重试失败释放，由扫描兜底。排队期间任务持有 Lease。</li>
         */
        private int queueCapacity = 256;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLanes() {
            return lanes;
        }

        public void setLanes(int lanes) {
            this.lanes = lanes;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    /** 发布结果处理配置。 */
    public static class Publisher {

        /**
         * 未路由（mandatory 退回）时的处理策略。
         * <li>默认 FAIL：抛出发布异常，由补偿任务重试；ACK_AND_WARN 仅用于订阅方尚未接入的过渡期，记 WARN 与
         * {@code messaging.published{outcome=unroutable}} 后视为成功。</li>
         */
        private com.paytech.foundation.messaging.publish.UnroutablePolicy unroutablePolicy =
            com.paytech.foundation.messaging.publish.UnroutablePolicy.FAIL;

        public com.paytech.foundation.messaging.publish.UnroutablePolicy getUnroutablePolicy() {
            return unroutablePolicy;
        }

        public void setUnroutablePolicy(com.paytech.foundation.messaging.publish.UnroutablePolicy unroutablePolicy) {
            this.unroutablePolicy = unroutablePolicy;
        }
    }

    /** 消费端配置。 */
    public static class Consumer {

        /**
         * 本服务作为订阅方的服务名（01 §3），用于队列命名并须登记在事件目录 consumers 中。
         * <li>设置后启用消费端：声明分区拓扑、创建监听容器、登记 {@code t_consumed_event}。</li>
         */
        private String service;

        /**
         * 缺口或 Handler 失败时重新投递前的退避基数。
         * <li>默认 1 秒；第 n 次重投前等待 n 倍，期间该分区暂停消费。</li>
         */
        private Duration retryBackoff = Duration.ofSeconds(1);

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }
    }

    /** 对一个发布方的订阅参数。 */
    public static class Subscription {

        /**
         * 分区队列数。
         * <li>默认 4，范围 1..64；同一聚合恒落同一分区。上线后修改会改变聚合到分区的映射，需排空队列后再调整。</li>
         */
        private int partitions = 4;

        public int getPartitions() {
            return partitions;
        }

        public void setPartitions(int partitions) {
            this.partitions = partitions;
        }
    }
}
