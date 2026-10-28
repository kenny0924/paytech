package com.paytech.commercial.service.processor;

import java.time.Clock;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.service.processor.request.SuspendAgreementCapabilityRequest;
import com.paytech.commercial.service.processor.response.AgreementCapabilityResponse;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 运营手动暂停开通项（POST /agreements/v1/{agreementId}/capabilities/{id}/suspension），锁 {@code agreement:{id}}
 * <li>开通项前置门禁 technique.md §9.2：PENDING_PREREQUISITES / ENABLED / SUSPENDED_BY_PREREQUISITE → SUSPENDED_MANUALLY，门禁不再触碰。</li>
 * <li>没有原请求重放：已是手动暂停返回 400。期望版本一致、合同未结束、CLIENT 合同、开通项成员资格 ACTIVE；Root CAS、执行状态条件迁移、
 * 开通项 UPDATE 快照（附暂停原因）与 AgreementCapabilityStatusChanged 发布任务同一事务写入。</li>
 */
public class SuspendAgreementCapabilityProcessor
    extends AbstractAgreementWriteProcessor<SuspendAgreementCapabilityRequest, AgreementCapabilityResponse> {
    private final AgreementService agreementService;
    private final AgreementCapabilityRepository agreementCapabilityRepository;
    private final AgreementCapabilityLimitRepository agreementCapabilityLimitRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param agreementService 合同写入服务
     * @param agreementRepository 合同仓储
     * @param agreementCapabilityRepository 开通项仓储
     * @param businessChangeRecordRepository BCR 仓储
     * @param agreementCapabilityLimitRepository 条件仓储
     */
    public SuspendAgreementCapabilityProcessor(Clock clock, AgreementService agreementService,
        AgreementRepository agreementRepository, AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementCapabilityLimitRepository agreementCapabilityLimitRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.agreementService = agreementService;
        this.agreementCapabilityRepository = agreementCapabilityRepository;
        this.agreementCapabilityLimitRepository = agreementCapabilityLimitRepository;
    }

    /** 请求所指合同。 */
    @Override
    protected long agreementId(SuspendAgreementCapabilityRequest request) {
        return request.getAgreementId();
    }

    @Override
    protected AgreementCapabilityResponse processWithinLock(SuspendAgreementCapabilityRequest request) {
        // 查询合同与开通项
        Agreement agreement = getAgreement(request.getAgreementId());
        BusinessChangeRecord root = getRoot(agreement);
        AgreementCapability capability = getOwnedCapability(agreementCapabilityRepository, agreement,
            request.getAgreementCapabilityId());

        // 构建上下文
        AgreementContext context = buildContext(agreement, root, capability, request);

        // 保存上下文
        agreementService.suspendCapability(context);

        // 响应处理
        return AddAgreementCapabilityProcessor.capabilityResponse(context);
    }

    /** 校验版本、合同、成员资格与当前执行状态后构造暂停上下文与发布任务。 */
    AgreementContext buildContext(Agreement agreement, BusinessChangeRecord root, AgreementCapability capability,
        SuspendAgreementCapabilityRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        checkOpen(agreement, "暂停开通项");
        if (agreement.getAgreementType() != AgreementAgreementType.CLIENT) {
            throw new BizCheckFailException("PARTNER 合同的开通项不能暂停: " + capability.getAgreementCapabilityId());
        }
        if (capability.getStatus() != AgreementCapabilityStatus.ACTIVE) {
            throw new BizCheckFailException("开通项已关闭，不能暂停: " + capability.getAgreementCapabilityId());
        }
        if (capability.getExecutionStatus() == AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY) {
            throw new BizCheckFailException("开通项已是手动暂停: " + capability.getAgreementCapabilityId());
        }
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now(),
            request.getExpectedChangeVersion());
        context.setCapability(capability);
        context.setCapabilityLimits(agreementCapabilityLimitRepository.findByAgreementCapabilityId(
            capability.getAgreementCapabilityId()));
        context.setCapabilityChange(executionChange(AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY));
        context.setSuspensionReason(request.getReason());
        context.setCapabilityStatusChangedEvent(AgreementCapabilityStatusChangedHandler.buildEvent(
            agreement.getAgreementId(), request.getExpectedChangeVersion() + 1));
        return context;
    }

    /**
     * 只带目标执行状态的变更载体；暂停与恢复共用
     *
     * @param executionStatus 目标执行状态
     * @return 变更载体
     */
    static AgreementCapability executionChange(AgreementCapabilityExecutionStatus executionStatus) {
        AgreementCapability change = new AgreementCapability();
        change.setExecutionStatus(executionStatus);
        return change;
    }

    /**
     * 开通项必须属于请求合同；暂停与恢复共用
     *
     * @param repository 开通项仓储
     * @param agreement 合同
     * @param agreementCapabilityId 开通项 ID
     * @return 开通项
     */
    static AgreementCapability getOwnedCapability(AgreementCapabilityRepository repository, Agreement agreement,
        long agreementCapabilityId) {
        AgreementCapability capability = repository.findById(agreementCapabilityId);
        if (capability == null || !capability.getAgreementId().equals(agreement.getAgreementId())) {
            throw new BizCheckFailException("开通项不存在或不属于该合同: " + agreementCapabilityId + ", 合同: "
                + agreement.getAgreementId());
        }
        return capability;
    }
}
