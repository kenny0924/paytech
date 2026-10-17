package com.paytech.foundation.web.internalauth;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.CallerIdentityArgument;
import com.paytech.foundation.internalauth.api.CallerIdentityHolder;
import com.paytech.foundation.internalauth.api.InternalTokenInvalidException;

/**
 * 解析 {@code @CallerIdentityArgument CallerIdentity} 参数；必填而请求未识别调用方时抛出内部凭证无效（401）。
 *
 * @author Claude
 */
public final class CallerIdentityArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 只支持带注解的 {@link CallerIdentity} 参数。
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CallerIdentityArgument.class)
            && CallerIdentity.class.equals(parameter.getParameterType());
    }

    /**
     * 从请求属性读取已识别的调用方。
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object identity = webRequest.getAttribute(CallerIdentityHolder.REQUEST_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST);
        CallerIdentityArgument annotation = parameter.getParameterAnnotation(CallerIdentityArgument.class);
        if (identity == null && annotation != null && annotation.required()) {
            throw new InternalTokenInvalidException("caller identity is required");
        }
        return identity;
    }
}
