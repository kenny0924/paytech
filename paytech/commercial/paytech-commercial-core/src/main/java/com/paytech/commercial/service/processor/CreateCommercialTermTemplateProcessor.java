package com.paytech.commercial.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.commercial.api.enums.CommercialTermOwnerType;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.domain.CommercialTermVersionContext;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.service.CommercialTermService;
import com.paytech.commercial.service.processor.request.CreateCommercialTermTemplateRequest;
import com.paytech.commercial.service.processor.response.CreateCommercialTermPlanResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 创建平台标准价目模板方案（POST /commercial-terms/v1/templates）：owner_type=PLATFORM_TEMPLATE，DRAFT
 * <li>业务身份 (termType, planCode)：同号且同为模板、同 OE 同名称重放返回原方案，其他 409。</li>
 * <li>只允许 Client 条款类型（在计费事件目录登记且不是 Partner 条款）；模板不属于合同，不参与运行时取价。</li>
 * <li>锁 {@code term-plan-code:{termType}:{planCode}} 持续等待；方案、BCR Root（版本 1）与 CREATE 快照同事务插入。</li>
 */
@Component
@AllArgsConstructor
public class CreateCommercialTermTemplateProcessor
    extends AbstractLockProcessor<CreateCommercialTermTemplateRequest, CreateCommercialTermPlanResponse> {
    private final Clock clock;
    private final CommercialTermLoader loader;
    private final CommercialTermService commercialTermService;
    private final ProductFeeEventRepository productFeeEventRepository;
    private final CommercialTermPlanRepository commercialTermPlanRepository;

    /** 方案编号资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CreateCommercialTermTemplateRequest request) {
        return LockCommand.lock(CommercialLocks.termPlanCode(request.getTermType(), request.getPlanCode()));
    }

    @Override
    protected CreateCommercialTermPlanResponse processWithinLock(CreateCommercialTermTemplateRequest request) {
        // 原方案：同号同内容直接返回
        CommercialTermPlan existing =
            commercialTermPlanRepository.findByTypeAndCode(request.getTermType(), request.getPlanCode());
        if (existing != null) {
            return replay(existing, request);
        }

        // 新模板：条款类型须为已登记的 Client 条款类型
        if (CreateCommercialTermPlanProcessor.PARTNER_TERM_TYPES.contains(request.getTermType())
            || productFeeEventRepository.findByTermType(request.getTermType()).isEmpty()) {
            throw new BizCheckFailException("标准价目模板只支持已登记的 Client 条款类型: " + request.getTermType());
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CommercialTermVersionContext context = CommercialTermVersionContext.builder().now(now)
            .operatorAccountId(request.getOperatorAccountId()).plan(newTemplate(request, now)).build();
        try {
            return new CreateCommercialTermPlanResponse(commercialTermService.createPlan(context), true);
        } catch (DuplicateKeyException concurrent) {
            throw new ConcurrentRequestException("方案编号已被并发占用: " + request.getPlanCode(), concurrent);
        }
    }

    /** 同号重放：须同为模板且 OE、名称一致，否则 409。 */
    CreateCommercialTermPlanResponse replay(CommercialTermPlan existing, CreateCommercialTermTemplateRequest request) {
        boolean same = existing.getOwnerType() == CommercialTermOwnerType.PLATFORM_TEMPLATE
            && Objects.equals(existing.getEntityNo(), request.getEntityNo())
            && Objects.equals(existing.getTemplateName(), request.getTemplateName());
        if (!same) {
            throw new ConcurrentRequestException("方案编号已被不同内容的方案使用: " + request.getTermType() + "/"
                + request.getPlanCode());
        }
        long changeVersion = loader.planRoot(existing).getCurrentVersion();
        return new CreateCommercialTermPlanResponse(new CommercialTermVersionView(existing, null, changeVersion), false);
    }

    /** 新模板方案行：只填 OE 与模板名，合同与 Owner 为空。 */
    CommercialTermPlan newTemplate(CreateCommercialTermTemplateRequest request, Instant now) {
        CommercialTermPlan plan = new CommercialTermPlan();
        plan.setTermPlanId(loader.nextId());
        plan.setPlanCode(request.getPlanCode());
        plan.setTermType(request.getTermType());
        plan.setOwnerType(CommercialTermOwnerType.PLATFORM_TEMPLATE);
        plan.setTemplateName(request.getTemplateName());
        plan.setEntityNo(request.getEntityNo());
        plan.setStatus(CommercialTermPlanStatus.DRAFT);
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        return plan;
    }
}
