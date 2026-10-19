package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.handler.AgreementStatusChangedHandler;
import com.paytech.commercial.service.processor.request.TerminateAgreementRequest;
import com.paytech.commercial.service.processor.response.AgreementResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;

/**
 * 终止合同（POST /agreements/v1/{agreementId}/terminate）：ACTIVE → TERMINATED，锁 {@code agreement:{id}}
 * <li>原请求：已是 TERMINATED 且 Root 版本为期望版本加一时直接返回。</li>
 * <li>新请求：期望版本一致、状态 ACTIVE；effectiveTo 不早于当前时刻（不回溯收窄）、晚于生效起点且不晚于原终点。TERMINATION_NOTICE 文件、
 * 状态与终点、Root CAS 与 AgreementStatusChanged 发布任务同一事务，提交后发布。</li>
 */
public class TerminateAgreementProcessor extends AbstractAgreementWriteProcessor<TerminateAgreementRequest, AgreementResponse> {
    private final IdGenerator idGenerator;
    private final AgreementService agreementService;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param idGenerator 主键生成器
     * @param agreementService 合同写入服务
     * @param agreementRepository 合同仓储
     * @param businessChangeRecordRepository BCR 仓储
     */
    public TerminateAgreementProcessor(Clock clock, IdGenerator idGenerator, AgreementService agreementService,
        AgreementRepository agreementRepository, BusinessChangeRecordRepository businessChangeRecordRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.idGenerator = idGenerator;
        this.agreementService = agreementService;
    }

    /** 请求所指合同。 */
    @Override
    protected long agreementId(TerminateAgreementRequest request) {
        return request.getAgreementId();
    }

    @Override
    protected AgreementResponse processWithinLock(TerminateAgreementRequest request) {
        // 查询合同与原推进
        Agreement agreement = getAgreement(request.getAgreementId());
        BusinessChangeRecord root = getRoot(agreement);
        if (alreadyApplied(root, request.getExpectedChangeVersion(), agreement.getStatus() == AgreementStatus.TERMINATED)) {
            return response(agreement, root);
        }

        // 构建上下文
        AgreementContext context = buildContext(agreement, root, request);

        // 保存上下文
        agreementService.changeLifecycle(context, BusinessChangeReason.AGREEMENT_TERMINATED);

        // 响应处理
        return response(agreement, root);
    }

    /** 校验版本、状态与终点后构造终止上下文、终止通知文件与发布任务。 */
    AgreementContext buildContext(Agreement agreement, BusinessChangeRecord root, TerminateAgreementRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        if (agreement.getStatus() != AgreementStatus.ACTIVE) {
            throw new BizCheckFailException("只有 ACTIVE 合同可以终止: " + agreement.getAgreementId() + ", 状态: "
                + agreement.getStatus().getCode());
        }
        Instant now = now();
        Instant effectiveTo = request.getEffectiveTo();
        if (effectiveTo.isBefore(now) || !effectiveTo.isAfter(agreement.getEffectiveFrom())
            || (agreement.getEffectiveTo() != null && effectiveTo.isAfter(agreement.getEffectiveTo()))) {
            throw new BizCheckFailException("终止终点必须不早于当前时刻、晚于生效起点且不晚于原终点: " + agreement.getAgreementId()
                + ", effectiveTo: " + effectiveTo);
        }
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now,
            request.getExpectedChangeVersion());
        context.moveTo(AgreementStatus.TERMINATED);
        context.setNextEffectiveTo(effectiveTo);
        context.setNewDocuments(List.of(buildNotice(agreement, request, now)));
        context.setStatusChangedEvent(AgreementStatusChangedHandler.buildEvent(agreement.getAgreementId(),
            request.getExpectedChangeVersion() + 1));
        return context;
    }

    /** 终止通知文件引用。 */
    AgreementDocumentRef buildNotice(Agreement agreement, TerminateAgreementRequest request, Instant now) {
        AgreementDocumentRef notice = new AgreementDocumentRef();
        notice.setAgreementDocumentId(idGenerator.nextId());
        notice.setAgreementId(agreement.getAgreementId());
        notice.setDocumentType(AgreementDocumentRefDocumentType.TERMINATION_NOTICE);
        notice.setFileId(request.getNoticeFileId());
        notice.setContentHash(request.getNoticeContentHash());
        notice.setCreateTime(now);
        notice.setUpdateTime(now);
        return notice;
    }
}
