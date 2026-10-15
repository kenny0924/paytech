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

    /** 结果确认来源：SEND_RESPONSE、PROVIDER_QUERY，可空。 */
    private String resolutionSource;

    /** 确认结果码，可空。 */
    private String resolutionCode;

    /** 开始时间，UTC。 */
    private Instant startedTime;

    /** 发送调用结束时间，可空。 */
    private Instant finishedTime;

    /** 结果确认时间，可空。 */
    private Instant resolvedTime;
}
