package com.paytech.commercial.service.processor;

import java.time.Clock;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domain.enums.AgreementDocumentRefDocumentType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domainservice.repository.AgreementDocumentRefRepository;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.service.processor.request.AgreementTransitionRequest;
import com.paytech.commercial.service.processor.response.AgreementResponse;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 提交签署（POST /agreements/v1/{agreementId}/submit-for-signature）：DRAFT → PENDING_SIGNATURE，锁 {@code agreement:{id}}
 * <li>原请求：已是 PENDING_SIGNATURE 且 Root 版本为期望版本加一时直接返回。</li>
 * <li>新请求：期望版本一致、状态 DRAFT、恰好一份 SIGNING_DOCUMENT、PARTNER 至少覆盖一个 OE；状态条件更新与 Root CAS 同一事务，
 * 提交签署后参与方与覆盖 OE 冻结。不发布事件。</li>
 */
public class SubmitAgreementForSignatureProcessor
    extends AbstractAgreementWriteProcessor<AgreementTransitionRequest, AgreementResponse> {
    private final AgreementService agreementService;
    private final AgreementDocumentRefRepository agreementDocumentRefRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param agreementService 合同写入服务
     * @param agreementRepository 合同仓储
     * @param businessChangeRecordRepository BCR 仓储
     * @param agreementDocumentRefRepository 文件引用仓储
     * @param agreementOperatingEntityRepository 覆盖 OE 仓储
     */
    public SubmitAgreementForSignatureProcessor(Clock clock, AgreementService agreementService,
        AgreementRepository agreementRepository, BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementDocumentRefRepository agreementDocumentRefRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.agreementService = agreementService;
        this.agreementDocumentRefRepository = agreementDocumentRefRepository;
        this.agreementOperatingEntityRepository = agreementOperatingEntityRepository;
    }

    /** 请求所指合同。 */
    @Override
    protected long agreementId(AgreementTransitionRequest request) {
        return request.getAgreementId();
    }

    @Override
    protected AgreementResponse processWithinLock(AgreementTransitionRequest request) {
        // 查询合同与原推进
        Agreement agreement = getAgreement(request.getAgreementId());
        BusinessChangeRecord root = getRoot(agreement);
        if (alreadyApplied(root, request.getExpectedChangeVersion(),
            agreement.getStatus() == AgreementStatus.PENDING_SIGNATURE)) {
            return response(agreement, root);
        }

        // 构建上下文
        AgreementContext context = buildContext(agreement, root, request);

        // 保存上下文
        agreementService.changeLifecycle(context, BusinessChangeReason.AGREEMENT_SUBMITTED_FOR_SIGNATURE);

        // 响应处理
        return response(agreement, root);
    }

    /** 校验版本、状态、签署文件与覆盖 OE 后构造推进上下文。 */
    AgreementContext buildContext(Agreement agreement, BusinessChangeRecord root, AgreementTransitionRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        if (agreement.getStatus() != AgreementStatus.DRAFT) {
            throw new BizCheckFailException("只有 DRAFT 合同可以提交签署: " + agreement.getAgreementId() + ", 状态: "
                + agreement.getStatus().getCode());
        }
        long signing = agreementDocumentRefRepository.findByAgreementId(agreement.getAgreementId()).stream()
            .filter(document -> document.getDocumentType() == AgreementDocumentRefDocumentType.SIGNING_DOCUMENT).count();
        if (signing != 1) {
            throw new BizCheckFailException("提交签署要求恰好一份 SIGNING_DOCUMENT: " + agreement.getAgreementId() + ", 实际: "
                + signing);
        }
        boolean partner = agreement.getLocalPartnerId() != null;
        if (partner && agreementOperatingEntityRepository.findByAgreementId(agreement.getAgreementId()).isEmpty()) {
            throw new BizCheckFailException("PARTNER 合同提交签署前至少覆盖一个 OE: " + agreement.getAgreementId());
        }
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now(),
            request.getExpectedChangeVersion());
        context.moveTo(AgreementStatus.PENDING_SIGNATURE);
        return context;
    }
}
