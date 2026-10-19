package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.util.Set;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.service.processor.response.AgreementResponse;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 已存在合同的写入 Processor 公共部分：锁 {@code agreement:{agreementId}}（LOCK，租约 -1），读取合同与其 BCR Root
 * <li>签署、状态推进、订阅与开通项共用同一资源锁，C 线 Plan 创建也锁同一 Key。</li>
 *
 * @param <Q> 内部请求
 * @param <R> 内部响应
 */
public abstract class AbstractAgreementWriteProcessor<Q, R> extends AbstractCommercialWriteProcessor<Q, R> {
    /** 已结束的合同不再接受订阅与开通项变更。 */
    static final Set<AgreementStatus> CLOSED = Set.of(AgreementStatus.EXPIRED, AgreementStatus.TERMINATED,
        AgreementStatus.CANCELLED);

    protected final AgreementRepository agreementRepository;

    /**
     * 注入公共依赖
     *
     * @param clock 业务时钟
     * @param agreementRepository 合同仓储
     * @param businessChangeRecordRepository BCR 仓储
     */
    protected AbstractAgreementWriteProcessor(Clock clock, AgreementRepository agreementRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, businessChangeRecordRepository);
        this.agreementRepository = agreementRepository;
    }

    /**
     * 请求所指合同
     *
     * @param request 内部请求
     * @return 合同 ID
     */
    protected abstract long agreementId(Q request);

    /** 锁合同聚合，持续等待。 */
    @Override
    protected LockCommand lockCommand(Q request) {
        return LockCommand.lock(CommercialLocks.agreement(agreementId(request)));
    }

    /** 合同必须存在。 */
    Agreement getAgreement(long agreementId) {
        Agreement agreement = agreementRepository.findById(agreementId);
        if (agreement == null) {
            throw new BizCheckFailException("合同不存在: " + agreementId);
        }
        return agreement;
    }

    /** 合同 BCR Root。 */
    BusinessChangeRecord getRoot(Agreement agreement) {
        return getRoot(BusinessChangeRecordRecordType.AGREEMENT, agreement.getAgreementId());
    }

    /** 已结束合同拒绝订阅与开通项变更。 */
    static void checkOpen(Agreement agreement, String operation) {
        if (CLOSED.contains(agreement.getStatus())) {
            throw new BizCheckFailException("合同已结束，不能" + operation + ": " + agreement.getAgreementId() + ", 状态: "
                + agreement.getStatus().getCode());
        }
    }

    /** 从合同与 Root 构造写入结果。 */
    static AgreementResponse response(Agreement agreement, BusinessChangeRecord root) {
        AgreementResponse response = new AgreementResponse();
        response.setAgreementId(agreement.getAgreementId());
        response.setAgreementNo(agreement.getAgreementNo());
        response.setStatus(agreement.getStatus());
        response.setChangeVersion(root.getCurrentVersion());
        return response;
    }
}
