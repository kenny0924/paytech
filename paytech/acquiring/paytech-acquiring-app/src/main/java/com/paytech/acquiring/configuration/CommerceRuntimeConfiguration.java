package com.paytech.commerce.configuration;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.commerce.domain.checkout.CheckoutPublicOrigin;
import com.paytech.commerce.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.commerce.integration.id.FoundationCommerceIdentifierGenerator;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;

/** Commerce 进程级时间、公开站点与应用 ID 能力的装配。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CommerceCheckoutProperties.class, CommerceIdentifierProperties.class})
public class CommerceRuntimeConfiguration {

    /**
     * 为业务创建和恢复提供唯一 UTC 时间源。
     *
     * @return 进程级 UTC Clock
     */
    @Bean
    Clock commerceClock() {
        return Clock.systemUTC();
    }

    /**
     * 校验并暴露在 Checkout Session 创建时冻结的公开 Origin。
     *
     * @param properties Hosted Checkout 站点配置
     * @return 已校验的公开 Origin
     */
    @Bean
    CheckoutPublicOrigin checkoutPublicOrigin(CommerceCheckoutProperties properties) {
        return new CheckoutPublicOrigin(properties.getPublicOrigin());
    }

    /**
     * 根据实例节点配置创建唯一的 Foundation ID 生成器。
     *
     * @param properties 实例部署分配的 Snowflake 节点配置
     * @return 业务与补偿共用的 ID 生成器
     */
    @Bean
    IdGenerator commerceFoundationIdGenerator(CommerceIdentifierProperties properties) {
        return new SnowflakeIdGenerator(properties.getRegionId(), properties.getWorkerId());
    }

    /** 业务与补偿共用同一生成器，避免相同 Worker 的独立序列碰撞。 */
    @Bean
    CommerceIdentifierGenerator commerceIdentifierGenerator(IdGenerator idGenerator) {
        return new FoundationCommerceIdentifierGenerator(idGenerator);
    }
}
