package com.paytech.platform.support.api.clientwebhook.response;

import java.util.List;

import tools.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户可见的投递详情；不含运营账号、内部系统名与内部事件主键。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientWebhookDeliveryDetailResponseBody {

    /** 投递 ID。 */
    private String deliveryId;

    /** 端点 ID。 */
    private String endpointId;

    /** PENDING、DELIVERING、SUCCEEDED、DEAD、CANCELLED 等投递状态。 */
    private String status;

    /** 已尝试次数。 */
    private int attemptCount;

    /** 最近一次尝试时间，UTC ISO-8601；未尝试为空。 */
    private String lastAttemptTime;

    /** 重发来源投递 ID；非重发为空。 */
    private String replayOfDeliveryId;

    /** 创建时间，UTC ISO-8601。 */
    private String createTime;

    /** 事件。 */
    private EventItem event;

    /** 按次序的尝试记录。 */
    private List<AttemptItem> attempts;

    /** 事件公开信息。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventItem {

        /** 公开事件 ID。 */
        private String eventId;

        /** 事件类型。 */
        private String eventType;

        /** 事件结构版本。 */
        private int schemaVersion;

        /** 发给客户的事件内容。 */
        private JsonNode envelope;
    }

    /** 单次尝试。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptItem {

        /** 尝试序号，从 1 开始。 */
        private int attemptNo;

        /** 请求地址（客户自己的端点地址）。 */
        private String requestUrl;

        /** 签名使用的密钥轮次。 */
        private int rotationNo;

        /** 尝试状态。 */
        private String status;

        /** 端点返回的 HTTP 状态；未收到响应为空。 */
        private Integer httpStatus;

        /** 粗粒度失败原因；成功为空。 */
        private String failureCode;

        /** 耗时毫秒。 */
        private Integer durationMs;

        /** 开始时间，UTC ISO-8601。 */
        private String startedTime;

        /** 结束时间，UTC ISO-8601。 */
        private String finishedTime;
    }
}
