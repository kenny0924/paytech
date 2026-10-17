package com.paytech.foundation.redis.core.lock;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.core.Ordered;

/** 在业务事务 Advisor 外层应用资源锁。 */
public final class ResourceLockAdvisor extends AbstractPointcutAdvisor {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final Pointcut pointcut = new ResourceLockPointcut();
    private final Advice advice;

    public ResourceLockAdvisor(ResourceLockMethodInterceptor interceptor) {
        advice = interceptor;
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
