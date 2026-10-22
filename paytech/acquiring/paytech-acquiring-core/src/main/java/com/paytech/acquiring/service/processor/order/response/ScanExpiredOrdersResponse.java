package com.paytech.acquiring.service.processor.order.response;

import java.util.EnumMap;
import java.util.Map;

import lombok.Getter;

/** 一次过期扫描的统计：扫描条数、各收尾结果条数及异常条数。 */
@Getter
public class ScanExpiredOrdersResponse {
    /** 本次读取到的到期订单数。 */
    private int scannedCount;

    /** 收尾处理抛出异常、已记录日志并继续下一单的订单数。 */
    private int failedCount;

    /** 各收尾结果的订单数，未出现的结果不在其中。 */
    private final Map<ExpireOrderResponse, Integer> resultCounts = new EnumMap<>(ExpireOrderResponse.class);

    /** 记录一单的收尾结果。 */
    public void recordResult(ExpireOrderResponse result) {
        scannedCount++;
        resultCounts.merge(result, 1, Integer::sum);
    }

    /** 记录一单的收尾异常。 */
    public void recordFailure() {
        scannedCount++;
        failedCount++;
    }

    /** 指定收尾结果的订单数。 */
    public int count(ExpireOrderResponse result) {
        return resultCounts.getOrDefault(result, 0);
    }
}
