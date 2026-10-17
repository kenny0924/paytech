package com.paytech.platform.support.delivery.integration.provider.ses;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;

/**
 * SES 投递事件拉取的环境级配置；仅在通知、SES 与事件拉取同时启用时绑定，变更需重启
 * <li>每个账号的事件队列 URL 保存在 t_notification_provider，由运营后台维护</li>
 */
@Getter
@ConfigurationProperties("notification.ses.events")
public class SesEventProperties {

    /**
     * 两轮拉取之间的间隔
     * <li>默认 60 秒，范围 10 秒—1 小时；由定时任务读取</li>
     */
    private final Duration pollInterval;

    /**
     * 单次 ReceiveMessage 的长轮询等待
     * <li>默认 10 秒，范围 0—20 秒（SQS 上限）</li>
     */
    private final Duration waitTime;

    /**
     * 每轮每个账号最多拉取的批数，每批最多 10 条
     * <li>默认 5，范围 1—20；某批不足 10 条即提前结束</li>
     */
    private final int maxBatches;

    /**
     * 收到的消息在队列中对其他消费者不可见的时长
     * <li>默认 60 秒，范围 30 秒—12 小时；未删除的消息到期后重新可见</li>
     */
    private final Duration visibilityTimeout;

    /**
     * SQS 终端地址覆盖
     * <li>可空；为空时使用队列 URL 所在区域的默认终端。必须为 HTTPS 根地址，或本机 HTTP 地址（仅测试）</li>
     */
    private final URI endpoint;

    /**
     * 连接超时
     * <li>默认 2 秒，范围 1—10 秒</li>
     */
    private final Duration connectTimeout;

    /**
     * 读超时
     * <li>默认 15 秒，范围 1—30 秒；必须大于 wait-time，否则长轮询会被读超时打断</li>
     */
    private final Duration readTimeout;

    /** 构造时校验全部取值；任一失败拒绝启动。 */
    public SesEventProperties(Duration pollInterval, Duration waitTime, Integer maxBatches, Duration visibilityTimeout,
        URI endpoint, Duration connectTimeout, Duration readTimeout) {
        this.pollInterval = within(pollInterval, Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofHours(1),
            "poll-interval");
        this.waitTime = within(waitTime, Duration.ofSeconds(10), Duration.ZERO, Duration.ofSeconds(20), "wait-time");
        this.maxBatches = maxBatches == null ? 5 : maxBatches;
        if (this.maxBatches < 1 || this.maxBatches > 20) {
            throw new IllegalArgumentException("notification.ses.events.max-batches 超出允许范围");
        }
        this.visibilityTimeout = within(visibilityTimeout, Duration.ofSeconds(60), Duration.ofSeconds(30),
            Duration.ofHours(12), "visibility-timeout");
        this.endpoint = SesEmailProperties.validEndpoint(endpoint);
        this.connectTimeout = within(connectTimeout, Duration.ofSeconds(2), Duration.ofSeconds(1),
            Duration.ofSeconds(10), "connect-timeout");
        this.readTimeout = within(readTimeout, Duration.ofSeconds(15), Duration.ofSeconds(1), Duration.ofSeconds(30),
            "read-timeout");
        if (this.readTimeout.compareTo(this.waitTime) <= 0) {
            throw new IllegalArgumentException("notification.ses.events.read-timeout 必须大于 wait-time");
        }
    }

    /** 未配置时取默认值，超出范围拒绝启动。 */
    static Duration within(Duration value, Duration fallback, Duration min, Duration max, String name) {
        Duration actual = value == null ? fallback : value;
        if (actual.compareTo(min) < 0 || actual.compareTo(max) > 0) {
            throw new IllegalArgumentException("notification.ses.events." + name + " 超出允许范围");
        }
        return actual;
    }
}
