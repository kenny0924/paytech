package com.paytech.foundation.redis.core.lock;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/** 在目标方法和其内部事务之外应用资源锁。 */
public final class ResourceLockMethodInterceptor implements MethodInterceptor {

    private final ResourceLockMetadataRegistry registry;
    private final ResourceLockKeyResolver keyResolver;
    private final ResourceLockExecutor executor;

    public ResourceLockMethodInterceptor(ResourceLockMetadataRegistry registry, ResourceLockKeyResolver keyResolver,
        ResourceLockExecutor executor) {
        this.registry = registry;
        this.keyResolver = keyResolver;
        this.executor = executor;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new FailException(RedisErrorCode.CONFIGURATION_INVALID, "Resource Lock必须在事务开始前进入");
        }
        Method method = invocation.getMethod();
        if (invocation.getThis() != null) {
            method = ResourceLockMethodIntrospector.canonical(method,
                org.springframework.aop.support.AopUtils.getTargetClass(invocation.getThis()));
        }
        ResourceLockMethodMetadata metadata = registry.required(method);
        var locks = keyResolver.resolve(metadata, invocation.getArguments());
        return executor.execute(locks, () -> requireSynchronousResult(invocation.proceed()));
    }

    boolean uses(ResourceLockMetadataRegistry expectedRegistry, ResourceLockKeyResolver expectedKeyResolver,
        ResourceLockExecutor expectedExecutor) {
        return registry == expectedRegistry && keyResolver == expectedKeyResolver && executor == expectedExecutor;
    }

    private Object requireSynchronousResult(Object result) {
        if (result != null && ResourceLockMetadataCompiler.isDelayedType(result.getClass())) {
            // 方法已经执行，无法撤销副作用；此处只是在释放锁前 fail-closed，启动签名校验仍是主保障。
            throw new FailException(RedisErrorCode.CONFIGURATION_INVALID,
                "ResourceLock方法运行时返回了异步结果，返回值必须同步完成");
        }
        return result;
    }
}
