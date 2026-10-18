package com.paytech.foundation.redis.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个由固定前缀和受限参数表达式标识的排他资源锁。
 * 只能直接声明在可预测、非 lazy、singleton Spring Bean 的具体 public 同步方法上；
 * override 必须重复声明，未 override 的具体父类方法可以继承执行。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(ResourceLocks.class)
public @interface ResourceLock {

    /** 资源类型的固定逻辑前缀。 */
    String prefix();

    /** 空串表示固定资源；其他值只允许 {@code #param} 或 {@code #param.field}。 */
    String key() default "";
}
