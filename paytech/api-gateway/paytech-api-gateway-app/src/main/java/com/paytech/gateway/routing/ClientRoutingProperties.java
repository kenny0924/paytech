package com.paytech.gateway.routing;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** CLIENT 入口默认关闭；启用时要求独立的精确 HTTPS Console Origin。 */
@ConfigurationProperties("gateway.client")
public record ClientRoutingProperties(boolean enabled, String consoleOrigin) {
    public ClientRoutingProperties {
        if (enabled) {
            try {
                URI origin = URI.create(consoleOrigin);
                if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null
                    || !"".equals(origin.getRawPath()) || origin.getRawQuery() != null || origin.getRawFragment() != null
                    || origin.getPort() > 65535) {
                    throw new IllegalArgumentException("无效的 Client Console Origin");
                }
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new IllegalArgumentException("gateway.client.console-origin 必须是精确 HTTPS Origin", exception);
            }
        }
    }

    /** 关闭的 CLIENT 入口不能授予任何浏览器 Origin 信任。 */
    public String trustedOrigin() {
        return enabled ? consoleOrigin : null;
    }
}
