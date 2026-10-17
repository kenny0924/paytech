package com.paytech.platform.support.api.webhookmanagement.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

/** 投递详情：投递、事件 Envelope 与全部尝试。 */
@Getter
@Setter
public class WebhookDeliveryDetailResponseBody extends WebhookDeliverySummaryResponseBody {

    /** Replay 操作人账号 ID；原始投递为空。 */
    private String requestedBy;

    /** 冻结事件。 */
    private EventItem event;

    /** 按尝试序号升序的全部尝试。 */
    private List<AttemptItem> attempts;

    /** 冻结事件。 */
    @Getter
    @Setter
    public static class EventItem {

        /** 公开 eventId。 */
        private String eventId;

        /** 事件类型。 */
        private String eventType;

        /** Envelope 的 schemaVersion。 */
        private int schemaVersion;

        /** 来源系统服务名。 */
        private String sourceSystem;

        /** 冻结的公开 Envelope，与发给 Client 的内容一致。 */
        private JsonNode envelope;
    }

    /** 一次投递尝试。 */
    @Getter
    @Setter
    public static class AttemptItem {

        /** 尝试序号，从 1 开始。 */
        private int attemptNo;

        /** 本次请求地址。 */
        private String requestUrl;

        /** 主签名密钥是第几把。 */
        private int rotationNo;

        /** 宽限签名密钥是第几把；无宽限时为空。 */
        private Integer graceRotationNo;

        /** 尝试状态：PROCESSING、SUCCEEDED、FAILED。 */
        private String status;

        /** 响应状态码；未收到响应时为空。 */
        private Integer httpStatus;

        /** 失败原因代码；成功时为空。 */
        private String failureCode;

        /** 耗时毫秒数；未完成时为空。 */
        private Integer durationMs;

        /** UTC 开始时间。 */
        private String startedTime;

        /** UTC 结束时间；未完成时为空。 */
        private String finishedTime;
    }
}
