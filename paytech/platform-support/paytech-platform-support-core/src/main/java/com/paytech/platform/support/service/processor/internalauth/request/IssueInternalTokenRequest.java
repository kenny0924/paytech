package com.paytech.platform.support.service.processor.internalauth.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 签发内部调用 Token 的内部请求；调用方身份已由入口从 X-Caller-Service 或已验证 Token 确定。 */
@Getter
@AllArgsConstructor
public class IssueInternalTokenRequest {

    /**
     * 调用方服务名
     * <li>Token {@code sub}；按白名单授权。</li>
     */
    @NotNull(message = "调用方服务名必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "调用方服务名不合法")
    private final String callerService;

    /**
     * 目标服务名
     * <li>Token {@code aud}。</li>
     */
    @NotNull(message = "targetService 必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "targetService 必须是服务发现名")
    private final String targetService;

    /**
     * Purpose Code
     * <li>大写 Code。</li>
     */
    @NotNull(message = "purpose 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "purpose 必须是大写 Code")
    private final String purpose;

    /**
     * 请求 ID
     * <li>写入审计事件；无 HTTP 请求上下文时为 UNKNOWN。</li>
     */
    @NotNull(message = "请求 ID 必填")
    private final String requestId;
}
