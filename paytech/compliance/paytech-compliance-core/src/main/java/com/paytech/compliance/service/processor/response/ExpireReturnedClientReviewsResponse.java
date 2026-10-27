package com.paytech.compliance.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一轮退回超期扫描的条数。 */
@Getter
@AllArgsConstructor
public class ExpireReturnedClientReviewsResponse {
    /** 本轮选中的案件数。 */
    private final int scannedCount;
    /** 成功过期的案件数。 */
    private final int expiredCount;
    /** 失败（下一轮重试）的案件数。 */
    private final int failedCount;
}
