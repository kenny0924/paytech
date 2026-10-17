package com.paytech.platform.support.ext.rest.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 仅用于 Controller 实现方法；参数定位不支持表达式或对象路径。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    String resourceType();
    String resourceIdParameter() default "";
    String fallbackResourceId() default "UNKNOWN";
    String[] alternateResourceTypes() default {};
    boolean recordSuccess() default true;
}
