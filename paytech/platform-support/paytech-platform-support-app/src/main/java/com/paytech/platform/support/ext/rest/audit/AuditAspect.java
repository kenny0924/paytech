package com.paytech.platform.support.ext.rest.audit;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.platform.support.core.audit.AuditContext;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.CallOutcome;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Invocation;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;

/** 只围住自身采集，业务委托执行一次且原异常原样抛出。 */
@Aspect
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${operations.authentication.enabled:false} || ${client.authentication.enabled:false}")
public class AuditAspect implements SmartInitializingSingleton {
    private static final Set<String> SAFE_CODES = Set.of("BUSINESS_CHECK_FAILED", "INVALID_REQUEST",
        "CLIENT_AUTHENTICATION_FAILED", "CLIENT_SESSION_INVALID", "CLIENT_EMAIL_LINK_INVALID",
        "CONCURRENT_REQUEST", "OPERATIONS_AUTHENTICATION_FAILED", "OPERATIONS_SESSION_INVALID",
        "OPERATIONS_PERMISSION_DENIED", "GATEWAY_CONFIGURATION_NOT_FOUND", "NOT_FOUND", "SERVICE_UNAVAILABLE",
        "CLIENT_INVITATION_INVALID", "CLIENT_INVITATION_CLOSED", "CLIENT_INVITATION_EMAIL_MISMATCH",
        "CLIENT_INVITATION_DUPLICATE", "CLIENT_INVITATION_LIMIT_REACHED", "CLIENT_MEMBER_EXISTS", "CLIENT_MEMBER_PROTECTED");
    private static final Set<Class<?>> SCALARS = Set.of(String.class, byte.class, Byte.class, short.class,
        Short.class, int.class, Integer.class, long.class, Long.class);
    private final Clock clock;
    private final AuditContext context;
    private final ApplicationContext application;

    public AuditAspect(Clock clock, AuditContext context, ApplicationContext application) {
        this.clock = clock;
        this.context = context;
        this.application = application;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (Object controller : application.getBeansWithAnnotation(RestController.class).values()) {
            for (Method method : AopUtils.getTargetClass(controller).getDeclaredMethods()) {
                Audit audit = method.getAnnotation(Audit.class);
                if (audit != null) validate(method, audit);
            }
        }
    }

    private void validate(Method method, Audit audit) {
        metadata(audit, audit.fallbackResourceId(), "UNKNOWN", Instant.EPOCH);
        if (!audit.resourceIdParameter().isEmpty() && parameterIndex(method, audit.resourceIdParameter()) < 0) {
            throw new IllegalStateException("审计资源绑定必须精确匹配安全标量参数");
        }
        if (!safeIdentifier(audit.fallbackResourceId())) {
            throw new IllegalStateException("审计资源 fallback 必须为安全标量");
        }
    }

    @Around("@annotation(com.paytech.platform.support.ext.rest.audit.Audit)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        Invocation invocation = null;
        try {
            Method signature = ((MethodSignature) joinPoint.getSignature()).getMethod();
            Method method = AopUtils.getMostSpecificMethod(signature, joinPoint.getTarget().getClass());
            Audit audit = method.getAnnotation(Audit.class);
            invocation = metadata(audit, resourceId(method, audit, joinPoint.getArgs()), requestId(), clock.instant());
        } catch (RuntimeException ignored) { /* open(null) 建立 no-op 作用域并固定计数。 */ }
        try (AuditContext.Scope scope = context.open(invocation)) {
            try {
                Object result = joinPoint.proceed();
                context.complete(CallOutcome.RETURNED, null);
                return result;
            } catch (FailException failure) {
                context.complete(CallOutcome.DENIED, safeCode(failure));
                throw failure;
            } catch (Throwable failure) {
                context.complete(CallOutcome.FAILED, "AUDIT_SYSTEM_FAILURE");
                throw failure;
            }
        }
    }

    private Invocation metadata(Audit audit, String resourceId, String requestId, Instant now) {
        Set<String> types = new HashSet<>(Set.of(audit.alternateResourceTypes()));
        types.add(audit.resourceType());
        return new Invocation(audit.action(), new Target(audit.resourceType(), resourceId),
            audit.recordSuccess(), requestId, now, types);
    }

    private String safeCode(FailException failure) {
        try {
            String code = failure.getCode();
            return code != null && SAFE_CODES.contains(code) ? code : "AUDIT_BUSINESS_DENIED";
        } catch (RuntimeException ignored) { return "AUDIT_BUSINESS_DENIED"; }
    }

    private String resourceId(Method method, Audit audit, Object[] arguments) {
        if (audit.resourceIdParameter().isEmpty()) return audit.fallbackResourceId();
        int index = parameterIndex(method, audit.resourceIdParameter());
        if (index < 0) throw new IllegalStateException("审计资源绑定无效");
        Object value = arguments[index];
        String scalar = value == null ? null : value instanceof String text ? text : String.valueOf(value);
        String pattern = Set.of("GATEWAY_TARGET", "GATEWAY_ROUTE").contains(audit.resourceType())
            ? "[a-z][a-z0-9-]{2,127}" : "[1-9][0-9]{0,18}";
        return scalar != null && scalar.length() <= 128 && scalar.matches(pattern) ? scalar : audit.fallbackResourceId();
    }

    private int parameterIndex(Method method, String name) {
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            if (parameter.isNamePresent() && name.equals(parameter.getName()) && SCALARS.contains(parameter.getType())) {
                return index;
            }
        }
        return -1;
    }

    private boolean safeIdentifier(String value) {
        return value != null && value.length() <= 128 && value.matches("[A-Za-z0-9._:+-]+");
    }

    private String requestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object value = attributes.getRequest().getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
            if (value instanceof String text) return text;
        }
        return "UNKNOWN";
    }
}
