package com.paytech.foundation.httpclient.core.transport;

import java.lang.reflect.Method;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.service.invoker.HttpRequestValues;

import com.paytech.foundation.internalauth.api.InternalPurpose;

/**
 * 把接口方法（或接口）上的 {@link InternalPurpose} 写入出站请求属性，供 {@link InternalTokenExchangeInterceptor} 读取。
 *
 * @author Claude
 */
public final class InternalPurposeRequestValuesProcessor implements HttpRequestValues.Processor {

    /**
     * 方法注解优先于接口注解；都没有时不写属性。
     */
    @Override
    public void process(Method method, MethodParameter[] parameters, Object[] arguments,
        HttpRequestValues.Builder builder) {
        InternalPurpose purpose = AnnotatedElementUtils.findMergedAnnotation(method, InternalPurpose.class);
        if (purpose == null) {
            purpose = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), InternalPurpose.class);
        }
        if (purpose != null) {
            builder.addAttribute(InternalPurpose.REQUEST_ATTRIBUTE, purpose.value());
        }
    }
}
