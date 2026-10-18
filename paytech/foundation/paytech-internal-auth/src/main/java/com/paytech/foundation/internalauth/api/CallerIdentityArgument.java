package com.paytech.foundation.internalauth.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 的 {@link CallerIdentity} 参数上，由 MVC Starter 从请求属性解析。
 * <li>{@link #required()} 为 true 且请求没有已识别调用方时返回 401。</li>
 *
 * @author Claude
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CallerIdentityArgument {

    /**
     * 是否必须已识别调用方。
     *
     * @return 默认 true
     */
    boolean required() default true;
}
