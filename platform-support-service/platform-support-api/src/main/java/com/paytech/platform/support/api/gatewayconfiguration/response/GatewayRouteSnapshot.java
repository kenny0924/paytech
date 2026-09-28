package com.paytech.platform.support.api.gatewayconfiguration.response;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前有效路由及其完整请求策略。 */
public final class GatewayRouteSnapshot {

    /**
     * 稳定路由编码。
     */
    private final String routeCode;

    /**
     * 路由所属公共入口 Channel 编码。
     * <li>必须与所属 Target 的 Channel 相同。</li>
     */
    private final String channel;

    /**
     * 精确匹配的 HTTP 方法。
     * <li>HEAD 不自动复用 GET；OPTIONS 由 Gateway 按 CORS 协议处理。</li>
     */
    private final String httpMethod;

    /**
     * 外部路径模板。
     * <li>仅支持固定路径段和单段变量，变量集合与下游模板一致。</li>
     */
    private final String externalPathTemplate;

    /**
     * 下游业务路径模板。
     * <li>路径来自配置，不承载目标地址或服务发现名称。</li>
     */
    private final String downstreamPathTemplate;

    /**
     * 同一快照内引用的目标编码。
     */
    private final String targetServiceCode;

    /**
     * 预编译安全档案编码。
     * <li>只能选择 Gateway 已支持的安全能力。</li>
     */
    private final String securityProfileCode;

    /**
     * 授权模式编码。
     */
    private final String authorizationMode;

    /**
     * 路由绑定的稳定权限编码。
     * <li>仅 HUMAN_PERMISSION 模式非空；权限当前是否有效由业务授权入口判断。</li>
     */
    private final String permissionCode;

    /**
     * 路由要求的精确 API Scope 编码。
     * <li>仅 API_SCOPE 模式非空，不支持前缀或通配匹配。</li>
     */
    private final String requiredScopeCode;

    /**
     * 请求 Cookie 转发模式编码。
     */
    private final String requestCookieMode;

    /**
     * 响应 Cookie 处理模式编码。
     */
    private final String responseCookieMode;

    /**
     * 允许 CORS 请求使用的 Header 名称。
     * <li>按规范化名称稳定排序；集合非空引用，允许没有元素。</li>
     */
    private final List<String> corsRequestHeaderNames;

    /**
     * 允许透传至下游的 Header 名称。
     * <li>按规范化名称稳定排序；不包含 Gateway 保留或逐跳 Header。</li>
     */
    private final List<String> forwardedRequestHeaderNames;

    /**
     * 允许的 Query 参数名称。
     * <li>按名称稳定排序；未声明的参数由 Gateway 拒绝。</li>
     */
    private final List<String> queryParameterNames;

    /**
     * 请求体最大字节数。
     * <li>单位为字节，允许 0，不能超过 Gateway 绝对上限。</li>
     */
    private final long bodyLimitBytes;

    /**
     * 接收请求体的超时。
     * <li>单位为毫秒，必须为正数且不超过 Gateway 绝对上限。</li>
     */
    private final int bodyReceiveTimeoutMillis;

    /**
     * 等待下游响应的超时。
     * <li>单位为毫秒，必须为正数且不超过 Gateway 绝对上限。</li>
     */
    private final int responseTimeoutMillis;

    /**
     * 当前路由的完整 Guard 规则。
     * <li>按规则类型和维度稳定排序，包含安全档案要求的最小规则集合。</li>
     */
    private final List<GatewayGuardRuleSnapshot> guardRules;

    /** 绑定快照字段，集合使用不可变副本，保留完整快照的值语义。 */
    @JsonCreator
    public GatewayRouteSnapshot(@JsonProperty("routeCode") String routeCode,
        @JsonProperty("channel") String channel,
        @JsonProperty("httpMethod") String httpMethod,
        @JsonProperty("externalPathTemplate") String externalPathTemplate,
        @JsonProperty("downstreamPathTemplate") String downstreamPathTemplate,
        @JsonProperty("targetServiceCode") String targetServiceCode,
        @JsonProperty("securityProfileCode") String securityProfileCode,
        @JsonProperty("authorizationMode") String authorizationMode,
        @JsonProperty("permissionCode") String permissionCode,
        @JsonProperty("requiredScopeCode") String requiredScopeCode,
        @JsonProperty("requestCookieMode") String requestCookieMode,
        @JsonProperty("responseCookieMode") String responseCookieMode,
        @JsonProperty("corsRequestHeaderNames") List<String> corsRequestHeaderNames,
        @JsonProperty("forwardedRequestHeaderNames") List<String> forwardedRequestHeaderNames,
        @JsonProperty("queryParameterNames") List<String> queryParameterNames,
        @JsonProperty("bodyLimitBytes") long bodyLimitBytes,
        @JsonProperty("bodyReceiveTimeoutMillis") int bodyReceiveTimeoutMillis,
        @JsonProperty("responseTimeoutMillis") int responseTimeoutMillis,
        @JsonProperty("guardRules") List<GatewayGuardRuleSnapshot> guardRules) {
        this.routeCode = routeCode;
        this.channel = channel;
        this.httpMethod = httpMethod;
        this.externalPathTemplate = externalPathTemplate;
        this.downstreamPathTemplate = downstreamPathTemplate;
        this.targetServiceCode = targetServiceCode;
        this.securityProfileCode = securityProfileCode;
        this.authorizationMode = authorizationMode;
        this.permissionCode = permissionCode;
        this.requiredScopeCode = requiredScopeCode;
        this.requestCookieMode = requestCookieMode;
        this.responseCookieMode = responseCookieMode;
        this.corsRequestHeaderNames = List.copyOf(corsRequestHeaderNames);
        this.forwardedRequestHeaderNames = List.copyOf(forwardedRequestHeaderNames);
        this.queryParameterNames = List.copyOf(queryParameterNames);
        this.bodyLimitBytes = bodyLimitBytes;
        this.bodyReceiveTimeoutMillis = bodyReceiveTimeoutMillis;
        this.responseTimeoutMillis = responseTimeoutMillis;
        this.guardRules = List.copyOf(guardRules);
    }

    /** 返回稳定路由编码。 */
    public String getRouteCode() {
        return routeCode;
    }

    /** 返回路由所属公共入口 Channel 编码。 */
    public String getChannel() {
        return channel;
    }

    /** 返回精确匹配的 HTTP 方法。 */
    public String getHttpMethod() {
        return httpMethod;
    }

    /** 返回外部路径模板。 */
    public String getExternalPathTemplate() {
        return externalPathTemplate;
    }

    /** 返回下游业务路径模板。 */
    public String getDownstreamPathTemplate() {
        return downstreamPathTemplate;
    }

    /** 返回同一快照内引用的目标编码。 */
    public String getTargetServiceCode() {
        return targetServiceCode;
    }

    /** 返回预编译安全档案编码。 */
    public String getSecurityProfileCode() {
        return securityProfileCode;
    }

    /** 返回授权模式编码。 */
    public String getAuthorizationMode() {
        return authorizationMode;
    }

    /** 返回路由绑定的稳定权限编码。 */
    public String getPermissionCode() {
        return permissionCode;
    }

    /** 返回路由要求的精确 API Scope 编码。 */
    public String getRequiredScopeCode() {
        return requiredScopeCode;
    }

    /** 返回请求 Cookie 转发模式编码。 */
    public String getRequestCookieMode() {
        return requestCookieMode;
    }

    /** 返回响应 Cookie 处理模式编码。 */
    public String getResponseCookieMode() {
        return responseCookieMode;
    }

    /** 返回允许 CORS 请求使用的 Header 名称。 */
    public List<String> getCorsRequestHeaderNames() {
        return corsRequestHeaderNames;
    }

    /** 返回允许透传至下游的 Header 名称。 */
    public List<String> getForwardedRequestHeaderNames() {
        return forwardedRequestHeaderNames;
    }

    /** 返回允许的 Query 参数名称。 */
    public List<String> getQueryParameterNames() {
        return queryParameterNames;
    }

    /** 返回请求体最大字节数。 */
    public long getBodyLimitBytes() {
        return bodyLimitBytes;
    }

    /** 返回接收请求体的超时。 */
    public int getBodyReceiveTimeoutMillis() {
        return bodyReceiveTimeoutMillis;
    }

    /** 返回等待下游响应的超时。 */
    public int getResponseTimeoutMillis() {
        return responseTimeoutMillis;
    }

    /** 返回当前路由的完整 Guard 规则。 */
    public List<GatewayGuardRuleSnapshot> getGuardRules() {
        return guardRules;
    }

    /** 比较完整快照字段，保持原契约的值相等语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GatewayRouteSnapshot value)) {
            return false;
        }
        return Objects.equals(routeCode, value.routeCode)
            && Objects.equals(channel, value.channel)
            && Objects.equals(httpMethod, value.httpMethod)
            && Objects.equals(externalPathTemplate, value.externalPathTemplate)
            && Objects.equals(downstreamPathTemplate, value.downstreamPathTemplate)
            && Objects.equals(targetServiceCode, value.targetServiceCode)
            && Objects.equals(securityProfileCode, value.securityProfileCode)
            && Objects.equals(authorizationMode, value.authorizationMode)
            && Objects.equals(permissionCode, value.permissionCode)
            && Objects.equals(requiredScopeCode, value.requiredScopeCode)
            && Objects.equals(requestCookieMode, value.requestCookieMode)
            && Objects.equals(responseCookieMode, value.responseCookieMode)
            && Objects.equals(corsRequestHeaderNames, value.corsRequestHeaderNames)
            && Objects.equals(forwardedRequestHeaderNames, value.forwardedRequestHeaderNames)
            && Objects.equals(queryParameterNames, value.queryParameterNames)
            && bodyLimitBytes == value.bodyLimitBytes
            && bodyReceiveTimeoutMillis == value.bodyReceiveTimeoutMillis
            && responseTimeoutMillis == value.responseTimeoutMillis
            && Objects.equals(guardRules, value.guardRules);
    }

    /** 根据完整快照字段生成与值相等语义一致的哈希值。 */
    @Override
    public int hashCode() {
        return Objects.hash(routeCode, channel, httpMethod, externalPathTemplate, downstreamPathTemplate,
            targetServiceCode, securityProfileCode, authorizationMode, permissionCode, requiredScopeCode,
            requestCookieMode, responseCookieMode, corsRequestHeaderNames, forwardedRequestHeaderNames,
            queryParameterNames, bodyLimitBytes, bodyReceiveTimeoutMillis, responseTimeoutMillis, guardRules);
    }
}
