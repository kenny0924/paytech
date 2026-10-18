package com.paytech.client.console.bff.ext.rest;

import java.util.Collections;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.paytech.client.console.bff.domain.enums.ClientContextType;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 解析 Gateway 唯一重建的 CUSTOMER / CLIENT 身份，供入站拦截器校验与 Controller 参数注入
 * <li>Realm、Channel、Actor 类型和上下文类型必须是单值合同值，账号与会话 ID 必须是规范正整数，否则会话无效。</li>
 * <li>只做格式判断；账号、会话与成员是否仍有效由 Platform 实时核验。</li>
 */
@Component
public class ClientActorResolver implements HandlerMethodArgumentResolver {
    private static final String ID = "[1-9][0-9]{0,18}";

    /** 校验并解析当前请求的可信身份。 */
    public ClientActor resolve(HttpServletRequest request) {
        ClientContextType contextType = ClientContextType.fromCode(header(request, "X-Gateway-Context-Type"));
        if (!"CUSTOMER".equals(header(request, "X-Gateway-Realm")) || !"CLIENT".equals(header(request, "X-Gateway-Channel"))
            || !"PLATFORM_USER".equals(header(request, "X-Gateway-Actor-Type")) || contextType == null) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        String account = header(request, "X-Gateway-Actor-Subject");
        String session = header(request, "X-Gateway-Session-Id");
        requireCanonicalId(account);
        requireCanonicalId(session);
        return new ClientActor(account, session, contextType, header(request, "X-Gateway-Tenant-Id"),
            header(request, "X-Gateway-Tenant-Member-Id"));
    }

    /** 只解析 ClientActor 参数。 */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == ClientActor.class;
    }

    /** 从当前请求解析可信身份。 */
    @Override
    public ClientActor resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory) {
        return resolve(webRequest.getNativeRequest(HttpServletRequest.class));
    }

    /** 身份 ID 必须是规范正整数且不超出 long 范围。 */
    static void requireCanonicalId(String value) {
        try {
            if (!value.matches(ID)) {
                throw new NumberFormatException();
            }
            Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
    }

    /** 只接受恰好一个值的可信头，否则返回空串。 */
    static String header(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : "";
    }
}
