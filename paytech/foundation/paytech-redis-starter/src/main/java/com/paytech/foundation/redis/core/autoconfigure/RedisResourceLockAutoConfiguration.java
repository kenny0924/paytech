package com.paytech.foundation.redis.core.autoconfigure;

import org.redisson.api.RedissonClient;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import com.paytech.foundation.redis.core.lock.*;

import io.micrometer.core.instrument.MeterRegistry;

/** ResourceLock 扫描、代理、执行和启动校验基础设施。 */
@AutoConfiguration(after = {RedisKeyNamespaceAutoConfiguration.class, RedisAutoConfiguration.class})
@Import(RedisResourceLockAutoConfiguration.ResourceLockProxyRegistrar.class)
@EnableConfigurationProperties({RedisResourceLockProperties.class, RedisCoordinationProperties.class,
    RedisNamespaceProperties.class})
public class RedisResourceLockAutoConfiguration {

    /** 注册 Spring 原生 Advisor 自动代理，不要求消费者引入 AspectJ。 */
    public static final class ResourceLockProxyRegistrar implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
            BeanDefinitionRegistry registry) {
            AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
            if (!registry.containsBeanDefinition("resourceLockLateBeanValidator")) {
                RootBeanDefinition definition = new RootBeanDefinition(ResourceLockLateBeanValidator.class);
                definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
                registry.registerBeanDefinition("resourceLockLateBeanValidator", definition);
            }
        }
    }

    @Bean
    static ResourceLockBeanDefinitionValidator resourceLockBeanDefinitionValidator() {
        return new ResourceLockBeanDefinitionValidator();
    }

    @Bean
    ResourceLockMetadataCompiler resourceLockMetadataCompiler() {
        return new ResourceLockMetadataCompiler();
    }

    @Bean
    ResourceLockMetadataRegistry resourceLockMetadataRegistry() {
        return new ResourceLockMetadataRegistry();
    }

    @Bean
    ResourceLockKeyResolver resourceLockKeyResolver() {
        return new ResourceLockKeyResolver();
    }

    @Bean
    ResourceLockOperations resourceLockOperations(ObjectProvider<RedissonClient> redissonClients,
        RedisNamespaceProperties namespaceProperties, Environment environment) {
        return new RedissonResourceLockOperations(redissonClients, namespaceProperties, environment);
    }

    @Bean
    ResourceLockObservation resourceLockObservation(ObjectProvider<MeterRegistry> meterRegistries) {
        return ResourceLockObservation.deferred(meterRegistries::getIfUnique);
    }

    @Bean
    ResourceLockExecutor resourceLockExecutor(
        @Qualifier("resourceLockOperations") ResourceLockOperations operations,
        @Qualifier("resourceLockObservation") ResourceLockObservation observation,
        RedisResourceLockProperties properties) {
        return new ResourceLockExecutor(operations, observation, properties);
    }

    @Bean
    ResourceLockMethodInterceptor resourceLockMethodInterceptor(
        @Qualifier("resourceLockMetadataRegistry") ResourceLockMetadataRegistry registry,
        @Qualifier("resourceLockKeyResolver") ResourceLockKeyResolver keyResolver,
        @Qualifier("resourceLockExecutor") ResourceLockExecutor executor) {
        return new ResourceLockMethodInterceptor(registry, keyResolver, executor);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ResourceLockAdvisor resourceLockAdvisor(
        @Qualifier("resourceLockMethodInterceptor") ResourceLockMethodInterceptor interceptor) {
        return new ResourceLockAdvisor(interceptor);
    }

    @Bean
    ResourceLockStartupValidator resourceLockStartupValidator(ConfigurableListableBeanFactory beanFactory,
        @Qualifier("resourceLockMetadataCompiler") ResourceLockMetadataCompiler compiler,
        @Qualifier("resourceLockBeanDefinitionValidator") ResourceLockBeanDefinitionValidator beanDefinitionValidator,
        @Qualifier("resourceLockLateBeanValidator") ResourceLockLateBeanValidator lateBeanValidator,
        @Qualifier("resourceLockMetadataRegistry") ResourceLockMetadataRegistry registry,
        @Qualifier("resourceLockKeyResolver") ResourceLockKeyResolver keyResolver,
        @Qualifier("resourceLockOperations") ResourceLockOperations operations,
        @Qualifier("resourceLockObservation") ResourceLockObservation observation,
        @Qualifier("resourceLockExecutor") ResourceLockExecutor executor,
        @Qualifier("resourceLockMethodInterceptor") ResourceLockMethodInterceptor interceptor,
        @Qualifier("resourceLockAdvisor") ResourceLockAdvisor advisor,
        RedisCoordinationProperties coordinationProperties, RedisNamespaceProperties namespaceProperties,
        RedisResourceLockProperties lockProperties, Environment environment) {
        return new ResourceLockStartupValidator(beanFactory, compiler, beanDefinitionValidator, lateBeanValidator,
            registry, keyResolver, operations, observation, executor, interceptor, advisor, coordinationProperties,
            namespaceProperties, lockProperties, environment);
    }
}
