package com.paytech.platform.support.referencedata.client.autoconfigure;

import java.net.URI;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.paytech.platform.support.referencedata.client.ReferenceDataKind;

/**
 * 参考数据客户端配置；不合法时启动失败。
 * <li>宽松 JSON、响应上限 1 MiB、不自动重试、不跟随重定向是固定行为，不开放配置。</li>
 *
 * @param baseUrl 可选：Platform 直连基址（含 Context Path），如 {@code http://127.0.0.1:18106/platform-support}，用于自动测试与本机；
 *            配置后不使用服务发现。未配置时经 LoadBalancerClient 按 serviceId + contextPath 访问
 * @param serviceId 未配置 baseUrl 时的服务发现名，默认 platform-support-service
 * @param contextPath 未配置 baseUrl 时 Platform 的 Context Path，默认 /platform-support；以 / 开头、不以 / 结尾，或为空
 * @param callerService 请求头 X-Caller-Service 的值（Platform internal-auth DUAL 据此识别调用服务）；未配置时取
 *            spring.application.name，两者都没有时启动失败
 * @param kinds 必填，至少一项：要加载的种类 CURRENCY / COUNTRY / SUBDIVISION / INDUSTRY / MCC；未开启的种类读取时报"未配置"
 * @param refreshInterval 首次全部加载成功后的全量刷新间隔，默认 PT1H，不小于 1 分钟；Platform 的变更最迟一个间隔后生效
 * @param initialRetryInterval 首次全部加载成功前，未成功种类的重试间隔，默认 PT10S，不小于 1 秒
 * @param schedulingEnabled 是否启动自带调度，默认 true；自动测试设为 false，经 ReferenceDataRefresher.refreshNow() 加载
 * @param connectTimeout 连接超时，默认 2 秒，大于零
 * @param readTimeout 读取超时，默认 5 秒，大于零
 */
@ConfigurationProperties("platform-support.reference-data.client")
public record ReferenceDataClientProperties(String baseUrl, @DefaultValue("platform-support-service") String serviceId,
    @DefaultValue("/platform-support") String contextPath, String callerService, Set<ReferenceDataKind> kinds,
    @DefaultValue("PT1H") Duration refreshInterval, @DefaultValue("PT10S") Duration initialRetryInterval,
    @DefaultValue("true") boolean schedulingEnabled, @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("5s") Duration readTimeout) {

    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(1);

    private static final Duration MIN_INITIAL_RETRY_INTERVAL = Duration.ofSeconds(1);

    /** 服务名格式（服务发现名与调用服务名）。 */
    private static final Pattern SERVICE_NAME = Pattern.compile("[a-z][a-z0-9-]{0,127}");

    /** Context Path 格式：为空，或以 / 开头的路径段且不以 / 结尾。 */
    private static final Pattern CONTEXT_PATH = Pattern.compile("(/[A-Za-z0-9._~-]+)*");

    /** 校验必填项与下限，并冻结种类集合。 */
    public ReferenceDataClientProperties {
        if (baseUrl != null) {
            validateBaseUrl(baseUrl);
        }
        requireServiceName("service-id", serviceId);
        if (contextPath == null || !CONTEXT_PATH.matcher(contextPath).matches()) {
            throw new IllegalArgumentException("platform-support.reference-data.client.context-path 必须为空或以 / 开头且不以 / 结尾");
        }
        if (callerService != null) {
            requireServiceName("caller-service", callerService);
        }
        if (kinds == null || kinds.isEmpty()) {
            throw new IllegalArgumentException("platform-support.reference-data.client.kinds 必须至少开启一个种类");
        }
        kinds = Set.copyOf(EnumSet.copyOf(kinds));
        requireAtLeast("refresh-interval", refreshInterval, MIN_REFRESH_INTERVAL);
        requireAtLeast("initial-retry-interval", initialRetryInterval, MIN_INITIAL_RETRY_INTERVAL);
        requirePositive("connect-timeout", connectTimeout);
        requirePositive("read-timeout", readTimeout);
    }

    /**
     * 未配置 base-url 时经服务发现访问的根地址（服务名作主机，由 LoadBalancerInterceptor 解析为实例）
     *
     * @return {@code http://<serviceId><contextPath>}
     */
    public String discoveryBaseUrl() {
        return "http://" + serviceId + contextPath;
    }

    /** 服务名必填且为小写服务名格式。 */
    static void requireServiceName(String name, String value) {
        if (value == null || !SERVICE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("platform-support.reference-data.client." + name + " 必须是小写服务名: " + value);
        }
    }

    /** base-url 必须是带主机的 http / https 地址，不含用户信息、查询或片段。 */
    private static void validateBaseUrl(String baseUrl) {
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("platform-support.reference-data.client.base-url 配置时不能为空白");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("platform-support.reference-data.client.base-url 格式非法", invalid);
        }
        boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        if (!http || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
            || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("platform-support.reference-data.client.base-url 必须是不带用户信息、查询和片段的 "
                + "http(s) 地址");
        }
    }

    /** 时长必填且不小于下限。 */
    private static void requireAtLeast(String name, Duration value, Duration minimum) {
        if (value == null || value.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("platform-support.reference-data.client." + name + " 不能小于 " + minimum);
        }
    }

    /** 时长必填且大于零。 */
    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("platform-support.reference-data.client." + name + " 必须大于零");
        }
    }
}
