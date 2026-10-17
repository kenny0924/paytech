package com.paytech.acquiring.configuration;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.acquiring.ext.mq.PaymentSucceededListener;

/**
 * Payment 支付结果消费拓扑
 * <li>acquiring 声明自己的消费队列、死信交换机与死信队列（design/10 §5.3 按服务名命名）；Payment 的 Topic Exchange 同参数幂等声明以便绑定。</li>
 * <li>监听容器手动确认、拒绝不重新入队，Prefetch 10。</li>
 */
@Configuration(proxyBeanMethods = false)
public class PaymentResultConsumerConfiguration {
    /** Payment 支付结果 Topic Exchange。 */
    static final String RESULT_EXCHANGE = "topic_payment_result";
    /** 支付成功路由键。 */
    static final String SUCCEEDED_ROUTING_KEY = "payment.succeeded.v1";
    /** 死信交换机。 */
    static final String DEAD_LETTER_EXCHANGE = "dlx_acquiring_payment_result";
    /** 死信队列。 */
    static final String DEAD_LETTER_QUEUE = "cg_acquiring_payment_result_dlq";
    /** 投递次数上限，超过后进入死信队列。 */
    static final int DELIVERY_LIMIT = 5;

    /** 手动确认、拒绝不重新入队的监听容器。 */
    @Bean
    public SimpleRabbitListenerContainerFactory paymentResultListenerFactory(ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        return factory;
    }

    /** Payment 发布的 Topic Exchange（同参数幂等声明）。 */
    @Bean
    public TopicExchange paymentResultExchange() {
        return new TopicExchange(RESULT_EXCHANGE, true, false);
    }

    /** 消费队列：Quorum、死信与投递次数上限。 */
    @Bean
    public Queue paymentResultQueue() {
        return QueueBuilder.durable(PaymentSucceededListener.QUEUE).quorum().deliveryLimit(DELIVERY_LIMIT)
            .deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey(DEAD_LETTER_QUEUE).build();
    }

    /** 消费队列绑定支付成功路由键。 */
    @Bean
    public Binding paymentSucceededBinding() {
        return BindingBuilder.bind(paymentResultQueue()).to(paymentResultExchange()).with(SUCCEEDED_ROUTING_KEY);
    }

    /** 死信交换机。 */
    @Bean
    public DirectExchange paymentResultDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /** 死信队列，保留待人工处理。 */
    @Bean
    public Queue paymentResultDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).quorum().build();
    }

    /** 死信绑定。 */
    @Bean
    public Binding paymentResultDeadLetterBinding() {
        return BindingBuilder.bind(paymentResultDeadLetterQueue()).to(paymentResultDeadLetterExchange())
            .with(DEAD_LETTER_QUEUE);
    }
}
