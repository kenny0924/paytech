package com.paytech.foundation.internalauth.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 覆盖 {@code @HttpExchange} 接口方法（或整个接口）出站 Token 的 Purpose；未标注时为 {@code GENERAL}。
 *
 * @author Claude
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalPurpose {

    /** 出站请求属性名：携带本次调用的 Purpose。 */
    String REQUEST_ATTRIBUTE = "com.paytech.foundation.internalauth.purpose";

    /**
     * Purpose Code。
     *
     * @return 大写 Code，例如 {@code CHANNEL_CARD_MATERIAL_REVEAL}
     */
    String value();
}
