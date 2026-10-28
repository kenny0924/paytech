package com.paytech.commercial.service.processor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.domain.CapabilityPrerequisiteContext;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.service.processor.request.CheckCapabilityPrerequisitesRequest;
import com.paytech.commercial.service.processor.request.RecheckDueCapabilityPrerequisitesRequest;
import com.paytech.commercial.service.processor.response.CapabilityPrerequisiteCheckResponse;
import com.paytech.commercial.service.processor.response.CapabilityPrerequisiteRecheckResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 定时重查一轮（作业 capabilityPrerequisiteRecheck，开通项前置门禁 technique.md §12、S-06）：按合同 ID 游标分页扫描本分片内有
 * 等待前置 / 前置失效暂停（includeEnabled 时另含已开通）开通项的 CLIENT 合同，逐个调用门禁，与运营检查同一路径（同锁、同判定、同事务与事件）
 * <li>单个合同失败只记录并计数，留待下一轮；不影响同轮其他合同。本身不加锁、不开事务。</li>
 */
@Component
@AllArgsConstructor
public class RecheckDueCapabilityPrerequisitesProcessor
    extends AbstractProcessor<RecheckDueCapabilityPrerequisitesRequest, CapabilityPrerequisiteRecheckResponse> {
    /** 每页合同数。 */
    static final int BATCH_SIZE = 200;

    private static final Logger LOG = LoggerFactory.getLogger(RecheckDueCapabilityPrerequisitesProcessor.class);

    private final AgreementRepository agreementRepository;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final CheckCapabilityPrerequisitesProcessor checkCapabilityPrerequisitesProcessor;

    @Override
    protected CapabilityPrerequisiteRecheckResponse processRequest(RecheckDueCapabilityPrerequisitesRequest request) {
        Set<AgreementCapabilityExecutionStatus> statuses = scope(request.getIncludeEnabled());
        RunCounter counter = new RunCounter();
        long cursor = 0L;
        List<Long> page;
        do {
            page = agreementCapabilityRepository.findAgreementIdsByExecutionStatus(statuses, cursor,
                request.getShardIndex(), request.getShardTotal(), BATCH_SIZE);
            page.forEach(agreementId -> check(agreementId, counter));
            cursor = page.isEmpty() ? cursor : page.getLast();
        } while (page.size() == BATCH_SIZE);
        return new CapabilityPrerequisiteRecheckResponse(counter.checked, counter.changed, counter.unknown, counter.failed);
    }

    /** 扫描范围：等待门禁处理的执行状态，复核时另含 ENABLED。 */
    static Set<AgreementCapabilityExecutionStatus> scope(boolean includeEnabled) {
        Set<AgreementCapabilityExecutionStatus> statuses = EnumSet.copyOf(CapabilityPrerequisiteContext.AWAITING_PREREQUISITES);
        if (includeEnabled) {
            statuses.add(AgreementCapabilityExecutionStatus.ENABLED);
        }
        return statuses;
    }

    /**
     * 核对一个 CLIENT 合同（PARTNER 合同的已开通项不进门禁，跳过）；失败只记录
     * <li>捕获运行期异常是为隔离同轮其他合同，失败合同状态不变，原因写入日志。</li>
     */
    void check(long agreementId, RunCounter counter) {
        Agreement agreement = agreementRepository.findById(agreementId);
        if (agreement == null || agreement.getAgreementType() != AgreementAgreementType.CLIENT) {
            return;
        }
        try {
            CapabilityPrerequisiteCheckResponse response = checkCapabilityPrerequisitesProcessor.process(
                new CheckCapabilityPrerequisitesRequest(agreementId));
            counter.checked++;
            counter.changed += response.isChanged() ? 1 : 0;
            counter.unknown += response.isUnknown() ? 1 : 0;
        } catch (RuntimeException failure) {
            counter.failed++;
            LOG.warn("开通项前置定时重查失败 agreementId={} reason={}", agreementId, failure.getMessage());
        }
    }

    /** 一轮的计数。 */
    static final class RunCounter {
        /** 核对成功的合同数 */
        int checked;

        /** 有迁移的合同数 */
        int changed;

        /** 结论含 UNKNOWN 的合同数 */
        int unknown;

        /** 失败的合同数 */
        int failed;
    }
}
