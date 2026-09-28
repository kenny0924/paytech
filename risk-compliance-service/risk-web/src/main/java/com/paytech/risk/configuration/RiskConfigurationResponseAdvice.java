package com.paytech.risk.configuration;

import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.RiskVariableModels;
import com.paytech.risk.api.decision.response.FlowDetailResponseBody;
import com.paytech.risk.api.decision.response.RuleSetDetailResponseBody;
import com.paytech.risk.api.decision.response.RuleSetReferenceResponseBody;
import lombok.AllArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** 配置查询按实际 UTF-8 响应实施容量合同，不能截断历史结构或变量当前值。 */
@RestControllerAdvice
@AllArgsConstructor
public class RiskConfigurationResponseAdvice implements ResponseBodyAdvice<Object> {
    private static final Set<Class<?>> PAGED = Set.of(RiskVariableModels.VariableListResponseBody.class,
        RiskVariableModels.VariableMembersResponseBody.class);
    private static final Set<Class<?>> DETAILS = Set.of(RiskVariableModels.VariableDetailResponseBody.class,
        RiskVariableModels.VariableReferencesResponseBody.class,
        com.paytech.risk.api.decision.response.RuleSetDetailResponseBody.class,
        FlowDetailResponseBody.class, RuleSetReferenceResponseBody.class);
    private final JsonMapper mapper;

    @Override
    public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
        return PAGED.contains(method.getParameterType()) || DETAILS.contains(method.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
        Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        int limit = PAGED.contains(method.getParameterType()) ? 512 * 1024 : 1024 * 1024;
        if (body != null && mapper.writeValueAsBytes(body).length > limit) {
            throw new InvalidParameterException("配置查询响应超过容量上限，请缩小分页或批取数量");
        }
        return body;
    }
}
