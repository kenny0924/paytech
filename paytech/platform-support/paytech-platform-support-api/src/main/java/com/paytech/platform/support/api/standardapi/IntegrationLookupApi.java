package com.paytech.platform.support.api.standardapi;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.standardapi.response.IntegrationExistenceResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 接入方存在性内部查询契约；业务系统（如 channel-connector 激活渠道前）在事务外经私网调用，不经过 Gateway 与 BFF，
 * 不需要运营会话，鉴权方式与 {@link StandardApiCommandApi} 相同（internal-auth 非 OFF 时校验内部 Token）。
 *
 * <p>
 * 只回答"是否存在"，不返回接入方配置、出站地址或凭据；不存在时仍返回 200 与 exists=false。
 */
@HttpExchange(url = "/standard-apis/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface IntegrationLookupApi {

    /**
     * 查询接入方是否存在
     *
     * @param integrationCode
     *            接入方编码（Platform 外部 API 配置中的 integrationCode；支付渠道以 channel_code 作为该编码）
     * @return 接入方编码与是否存在；编码格式不合法返回 400 INVALID_REQUEST（路径参数校验按 Foundation 统一映射）
     */
    @GetExchange("/integrations/{integrationCode}/existence")
    IntegrationExistenceResponseBody integrationExistence(
        @PathVariable("integrationCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String integrationCode);
}
