package com.paytech.consumer.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.consumer.api.enums.CustomerStatus;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;

/** consumer 唯一持久化装配（mor_consumer）：一个 DataSource / 事务管理器（Spring Boot 自动配置）与一个 @MapperScan。 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.consumer.core.dal.mapper")
public class ConsumerPersistenceConfiguration {

    /**
     * 主键由应用分配，Instant 一律按 UTC 保存；登记本服务封闭枚举的 TypeHandler。
     *
     * @return consumer 的 MyBatis 定制
     */
    @Bean
    ConfigurationCustomizer consumerMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.getTypeHandlerRegistry().register(InstantTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(CustomerStatus.class,
                new CodeEnumTypeHandler<>(CustomerStatus.class));
            // Foundation 补偿 XML 显式引用通用 Handler 类，按类缓存会复用最后注册的枚举实例；保持与 acquiring 相同的装配顺序约束。
            configuration.getTypeHandlerRegistry().register(CompensationEventStatus.class,
                new CodeEnumTypeHandler<>(CompensationEventStatus.class));
        };
    }
}
