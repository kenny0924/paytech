package com.paytech.platform.support.api.webhookmanagement.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 测试事件的同步发送结果；不落事件或投递记录。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookTestResponseBody {

    /** 结果：SUCCEEDED 或 FAILED。 */
    private String result;

    /** 端点返回的 HTTP 状态码；未收到响应时为空。 */
    private Integer httpStatus;

    /** 失败原因代码；成功时为空。 */
    private String failureCode;

    /** 发送耗时毫秒数；未发出请求时为空。 */
    private Integer durationMs;
}
