package com.paytech.platform.support.api.permissionmanagement.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前路由配置引用，不代表 Gateway 已加载。 */
public final class RouteReference implements PermissionReference {
    /** 稳定路由编码。 */
    private final String routeCode;

    /** 资源或路由所属 Channel。 */
    private final String channel;

    /** HTTP 请求方法。 */
    private final String httpMethod;

    /** 外部路径模板。 */
    private final String externalPathTemplate;

    /** 目标服务编码。 */
    private final String targetServiceCode;

    /** 当前配置状态：ACTIVE 或 DISABLED。 */
    private final String status;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public RouteReference(@JsonProperty(value = "routeCode") String routeCode,
        @JsonProperty(value = "channel") String channel,
        @JsonProperty(value = "httpMethod") String httpMethod,
        @JsonProperty(value = "externalPathTemplate") String externalPathTemplate,
        @JsonProperty(value = "targetServiceCode") String targetServiceCode,
        @JsonProperty(value = "status") String status) {
        this.routeCode = routeCode;
        this.channel = channel;
        this.httpMethod = httpMethod;
        this.externalPathTemplate = externalPathTemplate;
        this.targetServiceCode = targetServiceCode;
        this.status = status;
    }

    /** 稳定路由编码。 */
    public String getRouteCode() { return routeCode; }

    /** 资源或路由所属 Channel。 */
    public String getChannel() { return channel; }

    /** HTTP 请求方法。 */
    public String getHttpMethod() { return httpMethod; }

    /** 外部路径模板。 */
    public String getExternalPathTemplate() { return externalPathTemplate; }

    /** 目标服务编码。 */
    public String getTargetServiceCode() { return targetServiceCode; }

    /** 当前配置状态：ACTIVE 或 DISABLED。 */
    public String getStatus() { return status; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RouteReference value)) return false;
        return Objects.equals(routeCode, value.routeCode)
            && Objects.equals(channel, value.channel)
            && Objects.equals(httpMethod, value.httpMethod)
            && Objects.equals(externalPathTemplate, value.externalPathTemplate)
            && Objects.equals(targetServiceCode, value.targetServiceCode)
            && Objects.equals(status, value.status);
    }

    @Override
    public int hashCode() { return Objects.hash(routeCode, channel, httpMethod, externalPathTemplate, targetServiceCode, status); }
}
