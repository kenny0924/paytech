package com.paytech.compliance.ext.job;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.compliance.service.processor.ExpireReturnedClientReviewsProcessor;
import com.paytech.compliance.service.processor.request.ExpireReturnedClientReviewsRequest;
import com.paytech.compliance.service.processor.response.ExpireReturnedClientReviewsResponse;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * XXL-Job 入口：退回超期扫描（建议每 10 分钟一次，单实例路由）
 * <li>每轮最多处理 {@value #BATCH_SIZE} 个案件；有案件失败时标记任务失败，失败案件保持原状态，下一轮重新选中。日志只含条数。</li>
 * <li>仅在 onboarding.review.enabled=true 时注册，与审核入口一致。</li>
 */
@Component
@ConditionalOnProperty(name = "onboarding.review.enabled", havingValue = "true")
public class ClientReviewReturnExpiryJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "clientReviewReturnExpiryScan";

    /** 每轮最多处理的案件数。 */
    static final int BATCH_SIZE = 200;

    private final Clock clock;
    private final ExpireReturnedClientReviewsProcessor processor;

    /** 注入时间源与扫描 Processor。 */
    public ClientReviewReturnExpiryJobHandler(Clock clock, ExpireReturnedClientReviewsProcessor processor) {
        this.clock = clock;
        this.processor = processor;
    }

    /** 执行一轮扫描并记录条数。 */
    @XxlJob(HANDLER_NAME)
    public void scan() {
        try {
            ExpireReturnedClientReviewsResponse result = runOnce();
            String summary = "client review return expiry scan: scanned " + result.getScannedCount() + ", expired "
                + result.getExpiredCount() + ", failed " + result.getFailedCount();
            if (result.getFailedCount() > 0) {
                XxlJobHelper.handleFail(summary);
                return;
            }
            XxlJobHelper.log(summary);
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("client review return expiry scan failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 按当前 UTC 时间扫描一轮
     *
     * @return 本轮条数
     */
    public ExpireReturnedClientReviewsResponse runOnce() {
        return processor.process(new ExpireReturnedClientReviewsRequest(clock.instant().truncatedTo(ChronoUnit.MICROS), BATCH_SIZE));
    }
}
