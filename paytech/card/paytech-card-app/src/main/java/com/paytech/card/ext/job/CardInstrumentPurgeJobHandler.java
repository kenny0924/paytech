package com.paytech.card.ext.job;

import org.springframework.stereotype.Component;

import com.paytech.card.configuration.CardInstrumentPurgeProperties;
import com.paytech.card.service.processor.PurgeOrphanCardInstrumentsProcessor;
import com.paytech.card.service.processor.request.PurgeOrphanCardInstrumentsRequest;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import lombok.AllArgsConstructor;

/** XXL-Job 入口：定时回收 Payment 两段创建第二段失败留下的孤立卡交易工具（design/05 §2.2）。 */
@Component
@AllArgsConstructor
public class CardInstrumentPurgeJobHandler {
    /** XXL-Job Handler 名。 */
    public static final String HANDLER_NAME = "cardOrphanInstrumentPurge";

    private final CardInstrumentPurgeProperties properties;
    private final PurgeOrphanCardInstrumentsProcessor processor;

    /** 执行一批回收并记录条数；失败时标记任务失败，不记录卡值。 */
    @XxlJob(HANDLER_NAME)
    public void purge() {
        try {
            XxlJobHelper.log("purged orphan card instruments: " + runOnce());
        } catch (RuntimeException failure) {
            XxlJobHelper.handleFail("card orphan instrument purge failed: " + failure.getClass().getSimpleName());
        }
    }

    /**
     * 按配置回收一批
     *
     * @return 本批删除条数
     */
    public int runOnce() {
        return processor.process(new PurgeOrphanCardInstrumentsRequest(properties.getGrace(), properties.getBatchSize()));
    }
}
