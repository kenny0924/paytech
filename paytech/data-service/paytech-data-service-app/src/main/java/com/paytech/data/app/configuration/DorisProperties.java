package com.paytech.data.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Doris 连接配置（MySQL 协议，经 Doris FE 查询端口）。
 *
 * @author Claude
 */
@ConfigurationProperties("data-service.doris")
public class DorisProperties {

    /**
     * 是否创建 Doris DataSource 与健康检查。
     * <li>默认 {@code true}；{@code mock} Profile 为 {@code false}，关闭时不创建 DataSource Bean，也不注册健康指示器</li>
     * <li>启动配置，修改后需重启</li>
     */
    private final boolean enabled;

    /**
     * JDBC URL，例如 {@code jdbc:mysql://127.0.0.1:19030/mor_data_service}。
     * <li>开启时必填</li>
     */
    private final String url;

    /**
     * 只读账号，本地为 {@code data_service_app}。
     * <li>开启时必填</li>
     */
    private final String username;

    /**
     * 只读账号密码，本地取 {@code MOR_DATA_SERVICE_DORIS_PASSWORD}。
     * <li>开启时必填；不进入日志</li>
     */
    private final String password;

    /**
     * 获取连接与健康检查的超时。
     * <li>开启时必填，例如 {@code 2s}</li>
     */
    private final Duration connectTimeout;

    /**
     * 绑定配置。
     *
     * @param enabled 是否开启
     * @param url JDBC URL
     * @param username 账号
     * @param password 密码
     * @param connectTimeout 超时
     */
    public DorisProperties(boolean enabled, String url, String username, String password, Duration connectTimeout) {
        this.enabled = enabled;
        this.url = url;
        this.username = username;
        this.password = password;
        this.connectTimeout = connectTimeout;
    }

    /**
     * 返回是否开启。
     *
     * @return 是否开启
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 返回 JDBC URL。
     *
     * @return URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * 返回账号。
     *
     * @return 账号
     */
    public String getUsername() {
        return username;
    }

    /**
     * 返回密码。
     *
     * @return 密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * 返回超时。
     *
     * @return 超时
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }
}
