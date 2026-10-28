package com.paytech.commercial.service.processor;

import java.time.Clock;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.processor.request.ResumeAgreementCapabilityRequest;
import com.paytech.commercial.service.processor.response.AgreementCapabilityResponse;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 运营恢复手动暂停的开通项（POST /agreements/v1/{agreementId}/capabilities/{id}/resumption），锁 {@code agreement:{id}}
 * <li>开通项前置门禁 technique.md §9.2：SUSPENDED_MANUALLY → PENDING_PREREQUISITES，同一事务登记前置检查，提交后由门禁核对；恢复不直接开通。</li>
 * <li>存量迁移的 PARTNER 合同开通项（原 SUSPENDED）不进门禁，恢复直接 ENABLED。</li>
 * <li>没有原请求重放：不是手动暂停返回 400。期望版本一致、合同未结束；Root CAS、执行状态条件迁移、开通项 UPDATE 快照、前置检查与
 * AgreementCapabilityStatusChanged 发布任务同一事务写入。</li>
 */
public class ResumeAgreementCapabilityProcessor
    extends AbstractAgreementWriteProcessor<ResumeAgreementCapabilityRequest, AgreementCapabilityResponse> {
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
    public ResumeAgreementCapabilityProcessor(Clock clock, AgreementService agreementService,
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
    protected long agreementId(ResumeAgreementCapabilityRequest request) {
        return request.getAgreementId();
    }

    @Override
    protected AgreementCapabilityResponse processWithinLock(ResumeAgreementCapabilityRequest request) {
        // 查询合同与开通项
        Agreement agreement = getAgreement(request.getAgreementId());
        BusinessChangeRecord root = getRoot(agreement);
        AgreementCapability capability = SuspendAgreementCapabilityProcessor.getOwnedCapability(
            agreementCapabilityRepository, agreement, request.getAgreementCapabilityId());

        // 构建上下文
        AgreementContext context = buildContext(agreement, root, capability, request);

        // 保存上下文
        agreementService.resumeCapability(context);

        // 响应处理
        return AddAgreementCapabilityProcessor.capabilityResponse(context);
    }

    /** 校验版本、合同与当前执行状态后构造恢复上下文、前置检查与发布任务。 */
    AgreementContext buildContext(Agreement agreement, BusinessChangeRecord root, AgreementCapability capability,
        ResumeAgreementCapabilityRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        checkOpen(agreement, "恢复开通项");
        if (capability.getExecutionStatus() != AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY) {
            throw new BizCheckFailException("只有手动暂停的开通项可以恢复: " + capability.getAgreementCapabilityId());
        }
        AgreementCapabilityExecutionStatus target = AddAgreementCapabilityProcessor.initialExecutionStatus(agreement);
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now(),
            request.getExpectedChangeVersion());
        context.setCapability(capability);
        context.setCapabilityLimits(agreementCapabilityLimitRepository.findByAgreementCapabilityId(
            capability.getAgreementCapabilityId()));
        context.setCapabilityChange(SuspendAgreementCapabilityProcessor.executionChange(target));
        AddAgreementCapabilityProcessor.registerExecutionEvents(context,
            target == AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES);
        return context;
    }
}
