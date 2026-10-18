package com.paytech.platform.support.api.internalauth.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 内部调用 Token 签发请求；调用方身份取自 X-Caller-Service，不在 Body 中声明。 */
@Getter
@Setter
public class InternalTokenIssueRequestBody {

    /**
     * 目标服务名
     * <li>服务发现名（{@code spring.application.name}），即 Token {@code aud}；格式 [a-z][a-z0-9-]{0,63}。</li>
     */
    @NotNull(message = "targetService 必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "targetService 必须是服务发现名")
    private String targetService;

    /**
     * Purpose Code
     * <li>大写 Code，格式 [A-Z][A-Z0-9_]{0,63}；普通业务调用使用 {@code GENERAL}。</li>
     */
    @NotNull(message = "purpose 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "purpose 必须是大写 Code")
    private String purpose;
}
