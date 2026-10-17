package com.paytech.foundation.testsupport.core.autoconfigure;

import java.util.HashSet;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.util.Assert;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;
import com.paytech.foundation.testsupport.api.MockHttpServer;

/** 使用 Spring 原生扫描和内部 Bean 机制，统一装配测试服务及其生命周期。 */
public final class MockHttpServerRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {
    private BeanFactory beanFactory;

    /** 动态属性注册器从当前测试容器取得服务，不使用静态实例。 */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /** Controller 不注册到宿主的顶层 Bean 集合，依赖和销毁仍由 Spring 管理。 */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        var settings = metadata.getAnnotations().get(EnableMockHttpServer.class).synthesize();
        var controllers = scanComponents(settings.basePackages(), registry);
        Assert.notEmpty(controllers, "Mock HTTP 扫描范围中没有标注 TestComponent 的 Controller");

        var server = new RootBeanDefinition(MockHttpServer.class);
        server.getConstructorArgumentValues().addIndexedArgumentValue(0, controllers);
        server.setDestroyMethodName("close");
        registry.registerBeanDefinition("mockHttpServer", server);

        var properties = new RootBeanDefinition(DynamicPropertyRegistrar.class, () -> {
            var instance = beanFactory.getBean("mockHttpServer", MockHttpServer.class);
            return dynamic -> dynamic.add(settings.baseUrlProperty(), instance::baseUrl);
        });
        registry.registerBeanDefinition("mockHttpServerEndpointProperties", properties);
    }

    /** 只发现显式标记的测试组件，重复包去重，配置类必须由宿主明确导入。 */
    private ManagedList<BeanDefinition> scanComponents(String[] packages, BeanDefinitionRegistry registry) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TestComponent.class));
        scanner.addExcludeFilter(new AnnotationTypeFilter(Configuration.class));
        var controllers = new ManagedList<BeanDefinition>();
        var discovered = new HashSet<String>();
        for (var basePackage : packages) {
            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                if (!discovered.add(candidate.getBeanClassName())) {
                    continue;
                }
                var annotated = (AnnotatedBeanDefinition) candidate;
                AnnotationConfigUtils.processCommonDefinitionAnnotations(annotated);
                var annotations = annotated.getMetadata().getAnnotations();
                if (annotations.isPresent(Controller.class)) {
                    ((AbstractBeanDefinition) candidate).setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
                    controllers.add(candidate);
                } else {
                    registry.registerBeanDefinition(
                        AnnotationBeanNameGenerator.INSTANCE.generateBeanName(candidate, registry), candidate);
                }
            }
        }
        return controllers;
    }
}
