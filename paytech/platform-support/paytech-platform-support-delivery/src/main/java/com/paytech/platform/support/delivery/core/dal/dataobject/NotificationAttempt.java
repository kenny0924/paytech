package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import com.paytech.platform.support.delivery.core.enums.NotificationAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationEventOutcome;
import com.paytech.platform.support.delivery.core.enums.NotificationResolutionSource;

/** 通知领域持久化事实；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class NotificationAttempt {

    /** 一次发送尝试标识。 */
    private Long attemptId;

    /** 所属投递标识。 */
    private Long deliveryId;

    /** 原发送账号身份，查询时不改用当前路由。 */
    private Long providerId;

    /** 外发前持久化的供应商请求号。 */
    private String providerRequestNo;

    /** 供应商返回的消息标识。 */
    private String providerMessageId;

    /** 发送提交结果。 */
    private NotificationAttemptStatus status;

    /** 初次发送的安全结果码。 */
    private String responseCode;

    /** 最近确认来源。 */
    private NotificationResolutionSource resolutionSource;

    /** 后续查询的安全结果码。 */
    private String resolutionCode;

    /** 发送尝试开始时间。 */
    private Instant startedTime;

    /** 初次调用结束时间。 */
    private Instant finishedTime;

    /** 未知提交获得明确结果的时间。 */
    private Instant resolvedTime;

    /**
     * 供应商投递事件的结果
     * <li>可空；优先级只升不降，见 NotificationEventOutcome</li>
     */
    private NotificationEventOutcome eventOutcome;

    /**
     * 投递事件的安全分类码
     * <li>如 Permanent/General；送达为空</li>
     */
    private String eventOutcomeCode;

    /** 投递事件结果发生时间，取自供应商事件，可空。 */
    private Instant eventOutcomeTime;

    /**
     * 收件人投诉的安全分类码
     * <li>如 abuse；首个投诉为准，未投诉为空</li>
     */
    private String complaintCode;

    /** 收件人投诉发生时间，取自供应商事件，可空。 */
    private Instant complaintTime;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
