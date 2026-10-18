package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一次发送尝试；不含供应商请求号与消息标识。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttemptItem {

    /** 尝试 ID。 */
    private String attemptId;

    /** 实际使用的账号编码。 */
    private String providerCode;

    /** 尝试状态：PROCESSING、ACCEPTED、FAILED、UNKNOWN。 */
    private String status;

    /** 发送响应码，可空。 */
    private String responseCode;

    /** 结果确认来源：SEND_RESPONSE、PROVIDER_QUERY、PROVIDER_EVENT，可空。 */
    private String resolutionSource;

    /** 确认结果码，可空。 */
    private String resolutionCode;

    /** 开始时间，UTC。 */
    private Instant startedTime;

    /** 发送调用结束时间，可空。 */
    private Instant finishedTime;

    /** 结果确认时间，可空。 */
    private Instant resolvedTime;

    /**
     * 供应商投递事件的结果
     * <li>可空；取值 DELIVERED、SOFT_BOUNCE、HARD_BOUNCE、REJECTED。</li>
     */
    private String eventOutcome;

    /**
     * 投递事件的分类码
     * <li>退信为 bounceType/bounceSubType，如 Permanent/General；拒发为 Reject/原因；送达为 null。</li>
     */
    private String eventOutcomeCode;

    /** 投递事件结果发生时间，UTC；可空。 */
    private Instant eventOutcomeTime;

    /** 收件人投诉的分类码，如 abuse；未投诉为 null。 */
    private String complaintCode;

    /** 收件人投诉发生时间，UTC；可空。 */
    private Instant complaintTime;
}
