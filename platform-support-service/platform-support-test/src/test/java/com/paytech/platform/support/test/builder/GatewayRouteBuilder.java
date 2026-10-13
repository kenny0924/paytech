package com.paytech.platform.support.test.builder;

import com.paytech.platform.support.test.dal.ConfigurationTestMapper.Route;

/** 网关路由夹具；默认是绑定权限的 Operations POST 路由。 */
public final class GatewayRouteBuilder {

    private long id = OperatorBuilder.nextId();
    private String code;
    private String channel = "OPERATIONS";
    private String method = "POST";
    private String externalPath;
    private String downstreamPath;
    private long targetId;
    private String securityProfile = "OPERATIONS_JWT";
    private String authorizationMode = "HUMAN_PERMISSION";
    private Long permissionId;
    private String scopeCode;
    private String requestCookieMode = "DROP";
    private String responseCookieMode = "NONE";
    private long bodyLimit = 16384;
    private String status = "ACTIVE";

    private GatewayRouteBuilder() {
    }

    /** Operations 渠道路由，外部路径为 /api/operations 前缀加下游路径。 */
    public static GatewayRouteBuilder operations(String code, long targetId, String downstreamPath) {
        GatewayRouteBuilder builder = new GatewayRouteBuilder();
        builder.code = code;
        builder.targetId = targetId;
        builder.downstreamPath = downstreamPath;
        builder.externalPath = "/api/operations" + downstreamPath;
        return builder;
    }

    /** 仅认证、不校验权限的路由。 */
    public GatewayRouteBuilder authenticated() {
        this.authorizationMode = "HUMAN_AUTHENTICATED";
        this.permissionId = null;
        return this;
    }

    public GatewayRouteBuilder permission(long permissionId) {
        this.permissionId = permissionId;
        return this;
    }

    public GatewayRouteBuilder channel(String channel, String securityProfile, String authorizationMode) {
        this.channel = channel;
        this.securityProfile = securityProfile;
        this.authorizationMode = authorizationMode;
        return this;
    }

    public GatewayRouteBuilder scope(String scopeCode) {
        this.scopeCode = scopeCode;
        return this;
    }

    public GatewayRouteBuilder method(String method) {
        this.method = method;
        return this;
    }

    public GatewayRouteBuilder externalPath(String externalPath) {
        this.externalPath = externalPath;
        return this;
    }

    public GatewayRouteBuilder cookies(String request, String response) {
        this.requestCookieMode = request;
        this.responseCookieMode = response;
        return this;
    }

    public GatewayRouteBuilder bodyLimit(long bodyLimit) {
        this.bodyLimit = bodyLimit;
        return this;
    }

    public GatewayRouteBuilder status(String status) {
        this.status = status;
        return this;
    }

    public Route build() {
        return new Route(id, code, channel, method, externalPath, downstreamPath, targetId, securityProfile,
            authorizationMode, permissionId, scopeCode, requestCookieMode, responseCookieMode, bodyLimit, status);
    }
}
