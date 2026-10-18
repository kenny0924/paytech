package com.paytech.platform.support.api.standardapi;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.api.standardapi.response.ExecuteStandardApiResponseBody;

import jakarta.validation.Valid;

/**
 * 标准 API 内部执行契约；业务系统在事务外经私网调用，不经过 Gateway 与 BFF，本期不做调用方授权。
 *
 * <p>
 * 无 Idempotency-Key，调用方不得自动重试。
 */
@HttpExchange(url = "/standard-apis/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface StandardApiCommandApi {

    /**
     * 执行标准 API
     *
     * @param standardApiCode
     *            标准 API 编码，例如 PAY
     * @param requestBody
     *            接入方编码与标准 API 输入；input 不满足标准 API 请求字段返回 422 及 errors[]
     * @return 执行状态与标准 API 输出
     *         <li>只要返回 200，就可能已经发出渠道请求；ERROR 表示结果无法确认。</li>
     *         <li>404 STANDARD_API_NOT_FOUND / INTEGRATION_IMPLEMENTATION_NOT_FOUND、500 INTEGRATION_CONFIG_INVALID、 503
     *         INTEGRATION_INITIALIZATION_FAILED / INTEGRATION_BUDGET_EXHAUSTED 保证没有发出渠道请求。</li>
     */
    @PostExchange("/{standardApiCode}/execute")
    ExecuteStandardApiResponseBody executeStandardApi(@PathVariable("standardApiCode") String standardApiCode,
        @Valid @RequestBody ExecuteStandardApiRequestBody requestBody);
}
