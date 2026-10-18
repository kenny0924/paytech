package com.paytech.platform.support.accesscontrol.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operations 用例输入，不承载 HTTP 请求幂等。 */
@Getter
@AllArgsConstructor
public class ListOperationsAuditEventsRequest {
    /** 可信操作者账号 ID。 */
    @NotBlank
    private final String actor;

    /** 当前会话 ID。 */
    @NotBlank
    private final String session;

    /** 页码，从 1 开始。 */
    @Positive
    private final int page;

    /** 每页条数。 */
    @Positive
    private final int size;

    /** 包含的 UTC 起点，可空。 */
    private final Instant occurredFrom;

    /** 不包含的 UTC 终点，可空。 */
    private final Instant occurredTo;

    /** 可选精确匹配条件。 */
    private final String actorId;

    /** 可选精确匹配条件。 */
    private final String actionCode;

    /** 可选精确匹配条件。 */
    private final String result;

    /** 可选精确匹配条件。 */
    private final String requestId;

    /** 可选精确匹配条件。 */
    private final String resourceType;

    /** 可选精确匹配条件。 */
    private final String resourceId;

}
