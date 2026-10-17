package com.paytech.client.master.configuration;

import com.paytech.client.master.core.configuration.ClientFormCatalogProperties;
import java.time.Clock;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessClassificationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessProfileRepository;
import com.paytech.client.master.domainservice.repository.ClientProjectionRepository;
import com.paytech.client.master.domainservice.repository.ClientRepository;
import com.paytech.client.master.domainservice.service.ClientClassificationService;
import com.paytech.client.master.domainservice.service.ClientClassificationSourceService;
import com.paytech.client.master.domainservice.service.ClientReviewOutcomeService;
import com.paytech.client.master.domainservice.service.impl.ClientClassificationServiceImpl;
import com.paytech.client.master.domainservice.service.impl.ClientClassificationSourceServiceImpl;
import com.paytech.client.master.domainservice.repository.PartyProfileRepository;
import com.paytech.client.master.domainservice.service.impl.ClientReviewOutcomeServiceImpl;
import com.paytech.client.master.ext.mq.ClientReviewResultListener;
import com.paytech.client.master.integration.ClientReviewRegistrationClient;
import com.paytech.client.master.service.handler.ClientClassificationHandler;
import com.paytech.client.master.service.processor.ApplyClientReviewResultProcessor;
import com.paytech.client.master.service.processor.ConfirmClientClassificationProcessor;
import com.paytech.client.master.service.processor.GetClientClassificationProcessor;
import com.paytech.client.master.service.processor.GetClientReviewApplicationProcessor;
import com.paytech.client.master.service.processor.ReplaceClientClassificationProcessor;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;

/**
 * Operations 审核在 CCT 侧的装配：O03/O04 分类、I02 交付、审核结果消费与 Client 激活
 * <li>随草稿与补偿开关同时开启；结果消费要求本机 RabbitMQ。</li>
 * <li>CCT 声明自己的消费队列、死信交换机与死信队列；Risk 的 Topic Exchange 同参数幂等声明以便绑定。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "onboarding", name = {"draft-enabled", "recovery-enabled"}, havingValue = "true")
public class ClientReviewConfiguration {
    /** Risk 审核结果 Topic Exchange。 */
    static final String RESULT_EXCHANGE = "topic_risk_client_review";
    /** 审核结果路由键。 */
    static final String RESULT_ROUTING_KEY = "client_review.decided.v1";
    /** 死信交换机。 */
    static final String DEAD_LETTER_EXCHANGE = "dlx_cct_client_review";
    /** 死信队列。 */
    static final String DEAD_LETTER_QUEUE = "cg_cct_client_review_dlq";
    /** 投递次数上限，超过后进入死信队列。 */
    static final int DELIVERY_LIMIT = 5;

    /** 分类本地保存与交付读取。 */
    @Bean
    public ClientClassificationService clientClassificationService(PlatformTransactionManager manager, CompensationEventRegistrar events,
        ClientBusinessProfileRepository profiles, ClientBusinessClassificationRepository rows) {
        return new ClientClassificationServiceImpl(ClientSubmissionConfiguration.writes(manager), events, profiles, rows);
    }

    /** I02 交付补偿入口的本地读取；不依赖补偿登记器。 */
    @Bean
    public ClientClassificationSourceService clientClassificationSourceService(ClientBusinessProfileRepository profiles,
        ClientApplicationRepository applications, ClientBusinessClassificationRepository rows) {
        return new ClientClassificationSourceServiceImpl(profiles, applications, rows);
    }

    /** 审核结果本地落地。 */
    @Bean
    public ClientReviewOutcomeService clientReviewOutcomeService(ClientRepository clients, PlatformTransactionManager manager,
        CompensationEventRegistrar events, ClientProjectionRepository projections, ClientApplicationRepository applications,
        PartyProfileRepository profiles) {
        return new ClientReviewOutcomeServiceImpl(clients, ClientSubmissionConfiguration.writes(manager), events, projections, applications,
            profiles);
    }

    /** 分类读取。 */
    @Bean
    public GetClientClassificationProcessor getClientClassificationProcessor(ClientBusinessProfileRepository profiles,
        ClientApplicationRepository applications, ClientBusinessClassificationRepository rows) {
        return new GetClientClassificationProcessor(profiles, applications, rows);
    }

    /** O02 申请资料读取。 */
    @Bean
    public GetClientReviewApplicationProcessor getClientReviewApplicationProcessor(ClientApplicationRepository applications,
        ClientDraftService drafts, PlatformTransactionManager manager) {
        TransactionTemplate reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
        reads.setTimeout(5);
        return new GetClientReviewApplicationProcessor(applications, drafts, reads);
    }

    /** O03。 */
    @Bean
    public ReplaceClientClassificationProcessor replaceClientClassificationProcessor(Clock clock, IdGenerator ids,
        RedisResourceLockProperties locks, ClientFormCatalogProperties catalog, ClientBusinessProfileRepository profiles,
        ClientClassificationService classifications, ClientApplicationRepository applications, ClientBusinessClassificationRepository rows) {
        return new ReplaceClientClassificationProcessor(clock, ids, locks.getWaitTime(), locks.getLeaseTime(), catalog, profiles,
            classifications, applications, rows);
    }

    /** O04。 */
    @Bean
    public ConfirmClientClassificationProcessor confirmClientClassificationProcessor(Clock clock, RedisResourceLockProperties locks,
        ClientBusinessProfileRepository profiles, ClientClassificationService classifications, ClientApplicationRepository applications,
        ClientBusinessClassificationRepository rows) {
        return new ConfirmClientClassificationProcessor(clock, locks.getWaitTime(), locks.getLeaseTime(), profiles, classifications,
            applications, rows);
    }

    /** I02 交付补偿入口。 */
    @Bean
    public ClientClassificationHandler clientClassificationHandler(ClientReviewRegistrationClient risk,
        ClientClassificationSourceService classifications) {
        return new ClientClassificationHandler(risk, classifications);
    }

    /** 审核结果落地用例。 */
    @Bean
    public ApplyClientReviewResultProcessor applyClientReviewResultProcessor(Clock clock, IdGenerator ids,
        ClientReviewOutcomeService outcomes, ClientProjectionRepository projections, ClientApplicationRepository applications) {
        return new ApplyClientReviewResultProcessor(clock, ids, outcomes, projections, applications);
    }

    /** 审核结果监听器。 */
    @Bean
    public ClientReviewResultListener clientReviewResultListener(ApplyClientReviewResultProcessor processor) {
        return new ClientReviewResultListener(processor);
    }

    /** 手动确认、拒绝不重新入队的监听容器。 */
    @Bean
    public SimpleRabbitListenerContainerFactory clientReviewListenerFactory(ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        return factory;
    }

    /** Risk 发布的 Topic Exchange（同参数幂等声明）。 */
    @Bean
    public TopicExchange clientReviewResultExchange() {
        return new TopicExchange(RESULT_EXCHANGE, true, false);
    }

    /** 消费队列：Quorum、死信与投递次数上限。 */
    @Bean
    public Queue clientReviewResultQueue() {
        return QueueBuilder.durable(ClientReviewResultListener.QUEUE).quorum().deliveryLimit(DELIVERY_LIMIT)
            .deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey(DEAD_LETTER_QUEUE).build();
    }

    /** 消费队列绑定审核结果路由键。 */
    @Bean
    public Binding clientReviewResultBinding() {
        return BindingBuilder.bind(clientReviewResultQueue()).to(clientReviewResultExchange()).with(RESULT_ROUTING_KEY);
    }

    /** 死信交换机。 */
    @Bean
    public DirectExchange clientReviewDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /** 死信队列，保留待人工处理。 */
    @Bean
    public Queue clientReviewDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).quorum().build();
    }

    /** 死信绑定。 */
    @Bean
    public Binding clientReviewDeadLetterBinding() {
        return BindingBuilder.bind(clientReviewDeadLetterQueue()).to(clientReviewDeadLetterExchange()).with(DEAD_LETTER_QUEUE);
    }
}
