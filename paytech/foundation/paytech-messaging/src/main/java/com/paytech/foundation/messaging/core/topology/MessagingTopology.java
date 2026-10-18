package com.paytech.foundation.messaging.core.topology;

/**
 * 交换机与队列命名（design/08 §3，按一致性哈希交换机的单队列路由语义修订）。
 * <li>发布方：{@code <producer>.events}，Durable Fanout，所有订阅服务各得一份。</li>
 * <li>订阅方：{@code <consumer>.<producer>.hash}（{@code x-consistent-hash}）绑定到发布方交换机，再按路由键
 * {@code aggregateId} 把消息分配给本服务的 {@code <consumer>.<producer>.p0..pN-1} 之一。</li>
 * <li>死信：{@code <consumer>.<producer>.dlx} → {@code <consumer>.<producer>.dlq}。</li>
 *
 * @author Claude
 */
public final class MessagingTopology {

    /** 一致性哈希交换机类型（插件 {@code rabbitmq_consistent_hash_exchange}）。 */
    public static final String CONSISTENT_HASH_TYPE = "x-consistent-hash";

    /** 每个分区队列的投递上限。 */
    public static final int DELIVERY_LIMIT = 5;

    /** 携带聚合标识的消息头。 */
    public static final String AGGREGATE_ID_HEADER = "x-aggregate-id";

    /** 分区队列绑定权重：所有分区相同。 */
    public static final String PARTITION_WEIGHT = "1";

    private MessagingTopology() {
    }

    /**
     * 发布方交换机名。
     *
     * @param producer 发布服务名
     * @return {@code <producer>.events}
     */
    public static String producerExchange(String producer) {
        return producer + ".events";
    }

    /**
     * 订阅方一致性哈希交换机名。
     *
     * @param consumer 订阅服务名
     * @param producer 发布服务名
     * @return {@code <consumer>.<producer>.hash}
     */
    public static String consumerHashExchange(String consumer, String producer) {
        return consumer + "." + producer + ".hash";
    }

    /**
     * 分区队列名。
     *
     * @param consumer 订阅服务名
     * @param producer 发布服务名
     * @param partition 分区序号
     * @return {@code <consumer>.<producer>.p<partition>}
     */
    public static String partitionQueue(String consumer, String producer, int partition) {
        return consumer + "." + producer + ".p" + partition;
    }

    /**
     * 死信交换机名。
     *
     * @param consumer 订阅服务名
     * @param producer 发布服务名
     * @return {@code <consumer>.<producer>.dlx}
     */
    public static String deadLetterExchange(String consumer, String producer) {
        return consumer + "." + producer + ".dlx";
    }

    /**
     * 死信队列名。
     *
     * @param consumer 订阅服务名
     * @param producer 发布服务名
     * @return {@code <consumer>.<producer>.dlq}
     */
    public static String deadLetterQueue(String consumer, String producer) {
        return consumer + "." + producer + ".dlq";
    }
}
