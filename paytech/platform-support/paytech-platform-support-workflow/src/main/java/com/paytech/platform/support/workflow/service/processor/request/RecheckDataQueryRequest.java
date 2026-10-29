package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 定时重查一个分片的内部请求。 */
@Getter
@AllArgsConstructor
public class RecheckDataQueryRequest {

    /** 分片序号，从 0 开始。 */
    @Min(0)
    private final int shardIndex;

    /** 分片总数。 */
    @Positive
    private final int shardTotal;
}
