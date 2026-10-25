package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.AgreementContext;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.domainservice.validator.agreement.PartnerEntityPartyValidator;
import com.paytech.commercial.service.handler.AgreementStatusChangedHandler;
import com.paytech.commercial.service.processor.request.AgreementTransitionRequest;
import com.paytech.commercial.service.processor.response.AgreementResponse;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 激活合同（POST /agreements/v1/{agreementId}/activate）：SIGNED → ACTIVE，锁 {@code agreement:{id}}
 * <li>原请求：已是 ACTIVE 且 Root 版本为期望版本加一时直接返回。</li>
 * <li>新请求：期望版本一致、状态 SIGNED、生效起点已到且终点未到（effective_from ≤ activated_time）、不与同 Owner 的其他 ACTIVE 合同
 * 区间冲突（CLIENT 按 Client + OE + 业务模式，PARTNER 按 Local Partner + 共同覆盖 OE）；Partner 有平台服务费方案时合同 OE Party
 * 必须等于 Local Partner Party（AC-10）。状态、Root CAS 与 AgreementStatusChanged
 * 发布任务同一事务，提交后由补偿 Handler 发布。</li>
 */
public class ActivateAgreementProcessor extends AbstractAgreementWriteProcessor<AgreementTransitionRequest, AgreementResponse> {
    private final AgreementService agreementService;
    private final AgreementPartyRepository agreementPartyRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;
    private final AgreementOperatingEntityRepository agreementOperatingEntityRepository;

    /**
     * 注入依赖
     *
     * @param clock 业务时钟
     * @param agreementService 合同写入服务
     * @param agreementRepository 合同仓储
     * @param businessChangeRecordRepository BCR 仓储
     * @param agreementPartyRepository 合同参与方仓储
     * @param commercialTermPlanRepository 条款方案仓储
     * @param agreementOperatingEntityRepository 覆盖 OE 仓储
     */
    public ActivateAgreementProcessor(Clock clock, AgreementService agreementService,
        AgreementRepository agreementRepository, BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementPartyRepository agreementPartyRepository, CommercialTermPlanRepository commercialTermPlanRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository) {
        super(clock, agreementRepository, businessChangeRecordRepository);
        this.agreementService = agreementService;
        this.agreementPartyRepository = agreementPartyRepository;
        this.commercialTermPlanRepository = commercialTermPlanRepository;
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
        if (alreadyApplied(root, request.getExpectedChangeVersion(), agreement.getStatus() == AgreementStatus.ACTIVE)) {
            return response(agreement, root);
        }

        // 构建上下文
        AgreementContext context = buildContext(agreement, root, request);

        // 保存上下文
        agreementService.changeLifecycle(context, BusinessChangeReason.AGREEMENT_ACTIVATED);

        // 响应处理
        return response(agreement, root);
    }

    /** 校验版本、状态、生效区间与区间冲突后构造激活上下文与发布任务。 */
    AgreementContext buildContext(Agreement agreement, BusinessChangeRecord root, AgreementTransitionRequest request) {
        checkVersion(root, request.getExpectedChangeVersion());
        if (agreement.getStatus() != AgreementStatus.SIGNED) {
            throw new BizCheckFailException("只有 SIGNED 合同可以激活: " + agreement.getAgreementId() + ", 状态: "
                + agreement.getStatus().getCode());
        }
        Instant now = now();
        if (agreement.getEffectiveFrom().isAfter(now)) {
            throw new BizCheckFailException("合同生效起点未到，不能激活: " + agreement.getAgreementId() + ", effectiveFrom: "
                + agreement.getEffectiveFrom());
        }
        if (agreement.getEffectiveTo() != null && !agreement.getEffectiveTo().isAfter(now)) {
            throw new BizCheckFailException("合同生效终点已过，不能激活: " + agreement.getAgreementId());
        }
        checkNoOverlap(agreement);
        checkServiceChargeParties(agreement);
        AgreementContext context = new AgreementContext(agreement, root, request.getActorId(), now,
            request.getExpectedChangeVersion());
        context.moveTo(AgreementStatus.ACTIVE);
        context.setNextActivatedTime(now);
        context.setStatusChangedEvent(AgreementStatusChangedHandler.buildEvent(agreement.getAgreementId(),
            request.getExpectedChangeVersion() + 1));
        return context;
    }

    /** 同 Owner 的其他 ACTIVE 合同在相同适用范围内区间不得重叠。 */
    void checkNoOverlap(Agreement agreement) {
        boolean client = agreement.getClientId() != null;
        List<Agreement> siblings = client ? agreementRepository.findByClientId(agreement.getClientId())
            : agreementRepository.findByLocalPartnerId(agreement.getLocalPartnerId());
        List<Agreement> active = siblings.stream()
            .filter(other -> other.getStatus() == AgreementStatus.ACTIVE
                && !other.getAgreementId().equals(agreement.getAgreementId()) && overlaps(agreement, other))
            .toList();
        if (active.isEmpty()) {
            return;
        }
        Set<String> scope = client ? Set.of() : entities(List.of(agreement.getAgreementId()));
        for (Agreement other : active) {
            boolean sameScope = client ? agreement.getEntityNo().equals(other.getEntityNo())
                && agreement.getBusinessMode() == other.getBusinessMode()
                : entities(List.of(other.getAgreementId())).stream().anyMatch(scope::contains);
            if (sameScope) {
                throw new BizCheckFailException("与已生效合同区间冲突: " + agreement.getAgreementId() + ", 冲突合同: "
                    + other.getAgreementId());
            }
        }
    }

    /** Partner 合同的 Local Partner 有平台服务费方案时，合同 OE Party 必须等于 Local Partner Party。 */
    void checkServiceChargeParties(Agreement agreement) {
        if (agreement.getAgreementType() != AgreementAgreementType.PARTNER || commercialTermPlanRepository
            .findByPartnerAndType(agreement.getLocalPartnerId(), CommercialCodes.TermType.PLATFORM_SERVICE_CHARGE) == null) {
            return;
        }
        PartnerEntityPartyValidator.check(agreement.getAgreementId(),
            agreementPartyRepository.findByAgreementId(agreement.getAgreementId()));
    }

    /** 合同覆盖的 OE。 */
    Set<String> entities(List<Long> agreementIds) {
        return agreementOperatingEntityRepository.findByAgreementIds(agreementIds).stream()
            .map(AgreementOperatingEntity::getEntityNo).collect(Collectors.toSet());
    }

    /** 半开区间 [from, to) 是否重叠，空终点表示无穷。 */
    static boolean overlaps(Agreement left, Agreement right) {
        boolean leftEndsAfterRightStarts = left.getEffectiveTo() == null
            || left.getEffectiveTo().isAfter(right.getEffectiveFrom());
        boolean rightEndsAfterLeftStarts = right.getEffectiveTo() == null
            || right.getEffectiveTo().isAfter(left.getEffectiveFrom());
        return leftEndsAfterRightStarts && rightEndsAfterLeftStarts;
    }
}
