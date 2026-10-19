package com.paytech.commercial.service.processor;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.service.processor.request.CommercialTermVersionCommandRequest;
import com.paytech.commercial.service.processor.response.CommercialTermActivationRunResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 定时激活一轮（作业 commercialTermVersionActivation）：扫描生效起点已到的 SCHEDULED 版本，按生效起点顺序逐个以系统命令调用激活用例
 * {@link ActivateCommercialTermVersionProcessor}，与运营手工激活完全同一路径（同锁、同校验、同事务与事件）
 * <li>单个版本失败（校验不通过、并发冲突等）只记录并计数，版本保持 SCHEDULED，下一轮重试；不影响同轮其他版本。</li>
 * <li>本身不加锁、不开事务；每个版本的锁与事务由激活用例负责。</li>
 */
@Component
@AllArgsConstructor
public class ActivateDueCommercialTermVersionsProcessor extends AbstractProcessor<Instant, CommercialTermActivationRunResponse> {
    /** 每轮最多处理的版本数。 */
    static final int BATCH_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(ActivateDueCommercialTermVersionsProcessor.class);

    private final CommercialTermVersionRepository commercialTermVersionRepository;
    private final ActivateCommercialTermVersionProcessor activateCommercialTermVersionProcessor;

    @Override
    protected CommercialTermActivationRunResponse processRequest(Instant now) {
        List<CommercialTermVersion> due = commercialTermVersionRepository.findScheduledDue(now, BATCH_SIZE);
        int activated = 0;
        for (CommercialTermVersion version : due) {
            if (activate(version)) {
                activated++;
            }
        }
        return new CommercialTermActivationRunResponse(due.size(), activated, due.size() - activated);
    }

    /**
     * 以系统命令激活一个版本；失败只记录，留待下一轮
     * <li>捕获运行期异常是为隔离同轮其他版本，失败版本状态不变，原因写入日志。</li>
     *
     * @param version 到期版本
     * @return 是否激活成功
     */
    boolean activate(CommercialTermVersion version) {
        try {
            activateCommercialTermVersionProcessor.process(CommercialTermVersionCommandRequest.system(version.getTermVersionId()));
            return true;
        } catch (RuntimeException failure) {
            LOG.warn("商业条款版本定时激活失败 versionId={} planId={} reason={}", version.getTermVersionId(),
                version.getTermPlanId(), failure.getMessage());
            return false;
        }
    }
}
