package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;

import lombok.Getter;
import lombok.Setter;

/** 一次 HTTP 投递尝试；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class WebhookAttempt {

    /** 尝试主键。 */
    private Long attemptId;

    /** 所属投递。 */
    private Long deliveryId;

    /** 投递内序号，从 1 开始。 */
    private int attemptNo;

    /** 本次请求地址。 */
    private String requestUrl;

    /** 主签名密钥。 */
    private Long signingSecretId;

    /** 宽限签名密钥；无宽限时为空。 */
    private Long graceSecretId;

    /** 尝试状态。 */
    private WebhookAttemptStatus status;

    /** 响应状态码；未收到响应时为空。 */
    private Integer httpStatus;

    /** 失败原因；成功时为空。 */
    private WebhookFailureCode failureCode;

    /** 耗时毫秒；未完成时为空。 */
    private Integer durationMs;

    /** 开始时间。 */
    private Instant startedTime;

    /** 结束时间；PROCESSING 时为空。 */
    private Instant finishedTime;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
