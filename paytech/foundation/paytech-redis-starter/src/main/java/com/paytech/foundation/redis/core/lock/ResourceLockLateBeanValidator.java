package com.paytech.foundation.redis.core.lock;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.aop.support.AopUtils;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/** 阻止定义期无法看到的迟创建实现绕过启动期元数据冻结。 */
public final class ResourceLockLateBeanValidator implements BeanPostProcessor, BeanFactoryAware,
    AopInfrastructureBean, PriorityOrdered {

    private static final String REGISTRY_BEAN_NAME = "resourceLockMetadataRegistry";

    private ConfigurableListableBeanFactory beanFactory;
    private final Set<String> initializedResourceLockBeans = ConcurrentHashMap.newKeySet();
    private final ResourceLockBeanDefinitionValidator beanDefinitionValidator =
        new ResourceLockBeanDefinitionValidator();

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (ConfigurableListableBeanFactory)beanFactory;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        if (!ResourceLockMethodIntrospector.hasResourceLocks(targetClass)) {
            return bean;
        }
        initializedResourceLockBeans.add(beanName);
        beanDefinitionValidator.validateBeanLifecycle(beanFactory, beanName, targetClass);
        beanDefinitionValidator.validateTargetType(targetClass);
        if (beanFactory.containsSingleton(REGISTRY_BEAN_NAME)
            && beanFactory.getBean(REGISTRY_BEAN_NAME, ResourceLockMetadataRegistry.class).isFrozen()) {
            Method method = ResourceLockMethodIntrospector.resourceLockMethods(targetClass).stream().findFirst()
                .orElseThrow();
            throw new FailException(RedisErrorCode.CONFIGURATION_INVALID,
                "resource-lock bean=" + targetClass.getName() + " method=" + method.toGenericString()
                    + " annotation[1] lazy/scoped或迟注册目标无法进入启动期元数据，必须暴露可预测具体类型或接口合同");
        }
        return bean;
    }

    boolean wasResourceLockTargetInitialized(String beanName) {
        return initializedResourceLockBeans.contains(beanName);
    }
}
