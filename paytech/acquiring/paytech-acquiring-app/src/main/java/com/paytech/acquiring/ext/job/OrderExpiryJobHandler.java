package com.paytech.acquiring.ext.job;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.acquiring.configuration.OrderExpiryProperties;
import com.paytech.acquiring.service.processor.order.ScanExpiredOrdersProcessor;
import com.paytech.acquiring.service.processor.order.request.ScanExpiredOrdersRequest;
import com.paytech.acquiring.service.processor.order.response.ExpireOrderResponse;
import com.paytech.acquiring.service.processor.order.response.ScanExpiredOrdersResponse;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/**
 * XXL-Job 入口：按分片扫描到期仍为 CREATED/OPEN 的订单并逐单过期收尾（调度每分钟一次，路由策略分片广播）
 * <li>分片取自 XXL 上下文，按 order_id 取模；无分片上下文时按单分片全量扫描。</li>
 * <li>批大小、最多页数与 PENDING 告警阈值每次执行时从配置读取；扫描时刻取进程 UTC Clock。</li>
 * <li>有订单收尾失败或扫描本身异常时标记任务失败；失败订单保持原状态，下一轮重新选中。日志只含条数与异常类型。</li>
 */
@Component
@AllArgsConstructor
public class OrderExpiryJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "orderExpiryScan";

    private final Clock clock;
    private final OrderExpiryProperties properties;
    private final ScanExpiredOrdersProcessor processor;

    /** 执行一轮本分片的过期扫描并记录各结果条数。 */
    @XxlJob(HANDLER_NAME)
    public void scan() {
        try {
            int shardTotal = XxlJobHelper.getShardTotal();
            ScanExpiredOrdersResponse result = shardTotal > 0 ? runOnce(XxlJobHelper.getShardIndex(), shardTotal)
                : runOnce(0, 1);
            String summary = summary(result);
            if (result.getFailedCount() > 0) {
                XxlJobHelper.handleFail(summary);
                return;
            }
            XxlJobHelper.log(summary);
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("order expiry scan failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 按配置与当前 UTC 时间扫描一个分片
     *
     * @param shardIndex 分片序号，从 0 开始且小于分片总数
     * @param shardTotal 分片总数，单实例为 1
     * @return 本轮扫描条数、各收尾结果条数与失败条数
     */
    public ScanExpiredOrdersResponse runOnce(int shardIndex, int shardTotal) {
        return processor.process(new ScanExpiredOrdersRequest(shardIndex, shardTotal, properties.getBatchSize(),
            properties.getMaxBatches(), clock.instant().truncatedTo(ChronoUnit.MICROS),
            properties.getPendingAlertAfter()));
    }

    /** 拼接本轮扫描条数与各结果条数，不含订单或支付标识。 */
    static String summary(ScanExpiredOrdersResponse result) {
        return "order expiry scan: scanned " + result.getScannedCount()
            + ", expired " + result.count(ExpireOrderResponse.EXPIRED)
            + ", pending " + result.count(ExpireOrderResponse.PENDING)
            + ", already paid " + result.count(ExpireOrderResponse.ALREADY_PAID)
            + ", reversal required " + result.count(ExpireOrderResponse.REVERSAL_REQUIRED)
            + ", skipped " + result.count(ExpireOrderResponse.SKIPPED)
            + ", retry " + result.count(ExpireOrderResponse.RETRY)
            + ", failed " + result.getFailedCount();
    }
}
