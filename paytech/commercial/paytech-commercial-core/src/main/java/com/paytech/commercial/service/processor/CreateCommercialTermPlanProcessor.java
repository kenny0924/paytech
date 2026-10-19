package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.constant.CommercialLocks;
import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeHistory;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.service.processor.request.CreateCommercialTermPlanRequest;
import com.paytech.commercial.service.processor.response.CreateCommercialTermPlanResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 创建商业条款方案（DRAFT）：业务身份为 (termType, planCode)，同号同归属重放返回原方案，同号不同归属 409
 * <li>Client 条款（CLIENT_PRICING 等）引用同 Client 的 CLIENT 合同，方案冻结合同、OE 与 Client；Partner 条款（PARTNER_SHARE、
 * PLATFORM_SERVICE_CHARGE、PARTNER_BUY_RATE）只冻结 Local Partner，合同由版本冻结。</li>
 * <li>与合同的其他写入口共用 agreement:{agreementId} 锁并持续等待；方案、BCR 根（版本 1）与 CREATE 快照同事务插入。</li>
 */
@Component
@AllArgsConstructor
public class CreateCommercialTermPlanProcessor
    extends AbstractLockProcessor<CreateCommercialTermPlanRequest, CreateCommercialTermPlanResponse> {
    /** Owner 为 Local Partner 的条款类型。 */
    static final Set<String> PARTNER_TERM_TYPES = Set.of(CommercialCodes.TermType.PARTNER_SHARE,
        CommercialCodes.TermType.PLATFORM_SERVICE_CHARGE, CommercialCodes.TermType.PARTNER_BUY_RATE);

    private final Clock clock;
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;
    private final ProductFeeEventRepository productFeeEventRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;

    /** Owner 恰好一个，且与条款类型的 Owner 种类一致。 */
    @Override
    protected void validate(CreateCommercialTermPlanRequest request) {
        boolean partnerTerm = PARTNER_TERM_TYPES.contains(request.getTermType());
        if ((request.getClientId() == null) == (request.getLocalPartnerId() == null)) {
            throw new InvalidParameterException("clientId 与 localPartnerId 必须恰好一个");
        }
        if (partnerTerm != (request.getLocalPartnerId() != null)) {
            throw new InvalidParameterException("条款类型 " + request.getTermType() + " 的 Owner 种类不符");
        }
    }

    /** 合同资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CreateCommercialTermPlanRequest request) {
        return LockCommand.lock(CommercialLocks.agreement(request.getAgreementId()));
    }

    @Override
    protected CreateCommercialTermPlanResponse processWithinLock(CreateCommercialTermPlanRequest request) {
        // 原方案：同号同归属直接返回
        CommercialTermPlan existing =
            commercialTermPlanRepository.findByTypeAndCode(request.getTermType(), request.getPlanCode());
        if (existing != null) {
            return replay(existing, request);
        }

        // 新方案：合同与 Owner、条款类型目录、同合同 / 同 Partner 唯一
        CommercialTermVersionContext context = buildContext(request);
        try {
            commercialTermService.createPlan(context);
        } catch (DuplicateKeyException concurrent) {
            throw new ConcurrentRequestException("方案编号或合同条款类型已被并发占用: " + request.getPlanCode(), concurrent);
        }
        return new CreateCommercialTermPlanResponse(context.toVersionView(), true);
    }

    /** 同号重放：归属一致返回原方案，否则 409。 */
    CreateCommercialTermPlanResponse replay(CommercialTermPlan existing, CreateCommercialTermPlanRequest request) {
        boolean sameAgreement = existing.getAgreementId() == null || existing.getAgreementId() == request.getAgreementId();
        boolean same = sameAgreement && Objects.equals(existing.getClientId(), request.getClientId())
            && Objects.equals(existing.getLocalPartnerId(), request.getLocalPartnerId());
        if (!same) {
            throw new ConcurrentRequestException("方案编号已被不同归属的方案使用: " + request.getTermType() + "/"
                + request.getPlanCode());
        }
        long changeVersion = loader.planRoot(existing).getCurrentVersion();
        return new CreateCommercialTermPlanResponse(new CommercialTermVersionView(existing, null, changeVersion), false);
    }

    /** 校验合同、Owner 与唯一性后构造新方案与 BCR 根。 */
    CommercialTermVersionContext buildContext(CreateCommercialTermPlanRequest request) {
        Agreement agreement = loader.openAgreement(request.getAgreementId());
        checkOwner(agreement, request);
        if (productFeeEventRepository.findByTermType(request.getTermType()).isEmpty()) {
            throw new BizCheckFailException("条款类型未在计费事件目录登记: " + request.getTermType());
        }
        checkUnique(request);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CommercialTermPlan plan = newPlan(agreement, request, now);
        return CommercialTermVersionContext.builder().now(now).triggerId(loader.nextId())
            .operatorAccountId(request.getOperatorAccountId()).plan(plan).planHistory(newRoot(plan, now))
            .agreement(agreement).build();
    }

    /** Client 条款须为同 Client 的 CLIENT 合同，Partner 条款须为同 Local Partner 的 PARTNER 合同。 */
    void checkOwner(Agreement agreement, CreateCommercialTermPlanRequest request) {
        boolean matches = request.getClientId() != null
            ? agreement.getAgreementType() == AgreementAgreementType.CLIENT
                && request.getClientId().equals(agreement.getClientId())
            : agreement.getAgreementType() == AgreementAgreementType.PARTNER
                && request.getLocalPartnerId().equals(agreement.getLocalPartnerId());
        if (!matches) {
            throw new BizCheckFailException("合同与方案 Owner 不符: 合同 " + agreement.getAgreementId() + ", 条款类型 "
                + request.getTermType());
        }
    }

    /** 同合同（Client 条款）或同 Local Partner（Partner 条款）的同条款类型方案唯一。 */
    void checkUnique(CreateCommercialTermPlanRequest request) {
        CommercialTermPlan duplicate = request.getClientId() != null
            ? commercialTermPlanRepository.findByAgreementAndType(request.getAgreementId(), request.getTermType())
            : commercialTermPlanRepository.findByPartnerAndType(request.getLocalPartnerId(), request.getTermType());
        if (duplicate != null) {
            throw new BizCheckFailException("该 Owner 已有同条款类型方案: " + duplicate.getPlanCode());
        }
    }

    /** 新方案行。 */
    CommercialTermPlan newPlan(Agreement agreement, CreateCommercialTermPlanRequest request, Instant now) {
        boolean clientTerm = request.getClientId() != null;
        CommercialTermPlan plan = new CommercialTermPlan();
        plan.setTermPlanId(loader.nextId());
        plan.setPlanCode(request.getPlanCode());
        plan.setTermType(request.getTermType());
        plan.setAgreementId(clientTerm ? agreement.getAgreementId() : null);
        plan.setEntityNo(clientTerm ? agreement.getEntityNo() : null);
        plan.setClientId(request.getClientId());
        plan.setLocalPartnerId(request.getLocalPartnerId());
        plan.setStatus(CommercialTermPlanStatus.DRAFT);
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        return plan;
    }

    /** 方案的 BCR 根（版本 1）。 */
    BusinessChangeHistory newRoot(CommercialTermPlan plan, Instant now) {
        BusinessChangeHistory root = new BusinessChangeHistory();
        root.setHistoryId(loader.nextId());
        root.setRecordType(BusinessChangeRecordRecordType.COMMERCIAL_TERM_PLAN);
        root.setRecordId(Long.toString(plan.getTermPlanId()));
        root.setAggregateRootType(BusinessChangeRecordAggregateRootType.COMMERCIAL_TERM_PLAN);
        root.setAggregateRootId(Long.toString(plan.getTermPlanId()));
        root.setParentHistoryId(null);
        root.setCurrentVersion(1L);
        root.setCreateTime(now);
        root.setUpdateTime(now);
        return root;
    }
}
