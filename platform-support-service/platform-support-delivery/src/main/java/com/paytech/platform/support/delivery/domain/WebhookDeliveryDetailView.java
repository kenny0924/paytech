package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 投递、所属事件与全部尝试的组合查询结果。 */
@Getter
@AllArgsConstructor
public class WebhookDeliveryDetailView {

    /** 投递 ID。 */
    private final long deliveryId;

    /** 目标端点。 */
    private final long endpointId;

    /** 投递状态。 */
    private final WebhookDeliveryStatus status;

    /** 已创建尝试数量。 */
    private final int attemptCount;

    /** 最近尝试时间。 */
    private final Instant lastAttemptTime;

    /** Replay 来源投递。 */
    private final Long replayOfDeliveryId;

    /** Replay 操作人。 */
    private final Long requestedBy;

    /** 创建时间。 */
    private final Instant createTime;

    /** 公开 eventId。 */
    private final String eventId;

    /** 事件类型。 */
    private final String eventType;

    /** Envelope 的 schemaVersion。 */
    private final int schemaVersion;

    /** 来源系统。 */
    private final String sourceSystem;

    /** 冻结的 Envelope JSON。 */
    private final String envelope;

    /** 按序号升序的尝试。 */
    private final List<Attempt> attempts;

    /** 一次尝试；密钥只返回序号。 */
    @Getter
    @AllArgsConstructor
    public static class Attempt {

        /** 尝试序号。 */
        private final int attemptNo;

        /** 请求地址。 */
        private final String requestUrl;

        /** 主签名密钥序号。 */
        private final int rotationNo;

        /** 宽限签名密钥序号；无宽限时为空。 */
        private final Integer graceRotationNo;

        /** 尝试状态。 */
        private final WebhookAttemptStatus status;

        /** 响应状态码。 */
        private final Integer httpStatus;

        /** 失败原因。 */
        private final WebhookFailureCode failureCode;

        /** 耗时毫秒。 */
        private final Integer durationMs;

        /** 开始时间。 */
        private final Instant startedTime;

        /** 结束时间。 */
        private final Instant finishedTime;
    }
}
