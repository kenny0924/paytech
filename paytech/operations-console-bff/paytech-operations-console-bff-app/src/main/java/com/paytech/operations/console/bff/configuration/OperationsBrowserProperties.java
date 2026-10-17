package com.paytech.operations.console.bff.configuration;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 浏览器传输模式在启动时确定，不允许根据入站 Header 降级。 @author Codex */
@ConfigurationProperties("operations")
public record OperationsBrowserProperties(String consoleOrigin, boolean testHttpEnabled) {

    public OperationsBrowserProperties {
        URI origin = URI.create(consoleOrigin);
        if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
            || origin.getFragment() != null || !origin.getPath().isEmpty()) {
            throw new IllegalArgumentException("Operations Console Origin 必须是精确 Origin");
        }
        if (testHttpEnabled) {
            if (origin.getPort() < 1 || origin.getPort() > 65535
                || !consoleOrigin.equals("http://localhost:" + origin.getPort())) {
                throw new IllegalArgumentException("Operations HTTP 测试 Origin 必须为 http://localhost:<合法端口>");
            }
        } else if (!"https".equals(origin.getScheme())) {
            throw new IllegalArgumentException("Operations Console Origin 默认必须是 HTTPS");
        }
    }

    /** HTTP 例外只能在单独激活的 test 环境中使用。 */
    public void validateProfiles(String[] activeProfiles) {
        if (testHttpEnabled && (activeProfiles.length != 1 || !"test".equals(activeProfiles[0]))) {
            throw new IllegalArgumentException("Operations HTTP 只允许单独激活 test Profile");
        }
    }
}
