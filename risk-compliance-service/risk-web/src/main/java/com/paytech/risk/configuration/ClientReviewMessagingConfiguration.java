package com.paytech.risk.configuration;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.risk.ext.mq.ClientReviewResultRabbitPublisher;

/** Risk 发布的审核结果 Exchange；消费队列与绑定由消费方 CCT 声明。 */
@Configuration(proxyBeanMethods = false)
public class ClientReviewMessagingConfiguration {

    @Bean
    TopicExchange clientReviewExchange() {
        return new TopicExchange(ClientReviewResultRabbitPublisher.EXCHANGE, true, false);
    }
}
