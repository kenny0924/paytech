package com.paytech.platform.support.dataquery.service.processor.response;

import java.time.Instant;

import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 连接测试结果与检查时间。 */
@Getter
@AllArgsConstructor
public class ConnectionTestResult {

    /** 检查结果。 */
    private final ConnectionCheckResult check;

    /** 检查时间（UTC 微秒）。 */
    private final Instant checkedAt;
}
