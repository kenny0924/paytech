package com.paytech.compliance.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一轮退回超期扫描。 */
@Getter
@AllArgsConstructor
public class ExpireReturnedClientReviewsRequest {
    /** 扫描时刻。 */
    @NotNull
    private final Instant now;
    /** 本轮最多处理的案件数。 */
    @Positive
    private final int limit;
}
