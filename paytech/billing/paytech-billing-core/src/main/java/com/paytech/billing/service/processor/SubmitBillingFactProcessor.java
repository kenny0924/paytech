package com.paytech.billing.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.core.constant.BillingLocks;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.dataobject.BillingFactLine;
import com.paytech.billing.core.dal.dataobject.BillingFactRef;
import com.paytech.billing.core.dal.dataobject.CurrencySpec;
import com.paytech.billing.core.enums.BillingCodes;
import com.paytech.billing.core.enums.FactTargetBucket;
import com.paytech.billing.domain.BillingFactContext;
import com.paytech.billing.domain.BillingFactPayloads;
import com.paytech.billing.domain.view.BillingFactView;
import com.paytech.billing.domainservice.repository.BillingFactRepository;
import com.paytech.billing.domainservice.repository.CurrencySpecRepository;
import com.paytech.billing.domainservice.service.BillingFactService;
import com.paytech.billing.domainservice.validator.fact.BillingFactInvariants;
import com.paytech.billing.service.processor.request.SubmitBillingFactRequest;
import com.paytech.billing.service.processor.response.SubmitBillingFactResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.AllArgsConstructor;

/**
 * Maker 提交人工或成本调整计费事实（originType = MANUAL，DRAFT）
 * <li>业务唯一身份为 sourceSystem + sourceReference：同内容重复提交返回原事实，不同内容拒绝；与同一来源引用的提交共用资源锁并持续等待，
 * 来源唯一键为最终防线。</li>
 * <li>新提交：币种在只读副本中为 ACTIVE 且金额不超过币种精度；目录约束与登记不变量按 {@link BillingFactInvariants}；COST_ADJUSTMENT 的
 * 来源事实是同 OE、同币种的已批准 PAYMENT，Client 取自来源事实（请求给出时必须一致）；被替代事实是同类型、同 Client、同 OE、同币种的
 * 已批准事实。</li>
 * <li>事实头、引用与全部明细行在同一本地事务写入，business_time 暂写提交时间（批准时覆盖）；本入口没有外部调用，也不登记事件
 * （发布任务在批准时登记）。</li>
 */
@Component
@AllArgsConstructor
public class SubmitBillingFactProcessor
    extends AbstractLockProcessor<SubmitBillingFactRequest, SubmitBillingFactResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final BillingFactService billingFactService;
    private final BillingFactRepository billingFactRepository;
    private final CurrencySpecRepository currencySpecRepository;

    /** 事实类型、引用与明细行代码只依赖输入，在加锁前按目录解析；同一引用类型至多一条。 */
    @Override
    protected void validate(SubmitBillingFactRequest request) {
        parseFactType(request.getFactType());
        Set<FactRefType> refTypes = new HashSet<>();
        for (SubmitBillingFactRequest.Ref ref : request.getRefs()) {
            if (!refTypes.add(BillingCodes.parse(FactRefType.class, "引用类型", ref.getRefType()))) {
                throw new InvalidParameterException("同一引用类型至多一条: " + ref.getRefType());
            }
        }
        for (SubmitBillingFactRequest.Line line : request.getLines()) {
            BillingCodes.parse(FactComponentType.class, "组件类型", line.getComponentType());
            BillingCodes.parse(FactPartyRole.class, "付方角色", line.getPayerRole());
            BillingCodes.parse(FactPartyRole.class, "收方角色", line.getPayeeRole());
            BillingCodes.parse(FactLineDirection.class, "影响方向", line.getDirection());
            parseTargetBucket(line.getTargetBucket());
        }
    }

    /** 来源引用资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(SubmitBillingFactRequest request) {
        return LockCommand.lock(BillingLocks.billingFactSource(request.getSourceSystem(), request.getSourceReference()));
    }

    @Override
    protected SubmitBillingFactResponse processWithinLock(SubmitBillingFactRequest request) {
        // 查询原提交
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        BillingFact fact = buildFact(request, now);
        List<BillingFactRef> refs = buildRefs(fact, request, now);
        List<BillingFactLine> lines = buildLines(fact, request, now);
        fact.setSourcePayloadHash(BillingFactPayloads.submissionHash(fact, refs, lines));
        SubmitBillingFactResponse original = getOriginalSubmission(fact);
        if (original != null) {
            return original;
        }

        // 构建上下文
        BillingFactContext context = buildContext(fact, refs, lines);

        // 保存上下文
        billingFactService.saveDraft(context);

        // 响应处理
        return new SubmitBillingFactResponse(BillingFactView.of(context, null, List.of()), true);
    }

    /** 只允许两类人工登记。 */
    BillingFactType parseFactType(String code) {
        BillingFactType factType = BillingCodes.parse(BillingFactType.class, "计费事实类型", code);
        if (factType != BillingFactType.MANUAL_ADJUSTMENT && factType != BillingFactType.COST_ADJUSTMENT) {
            throw new InvalidParameterException("只能登记 MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT: " + code);
        }
        return factType;
    }

    /** 目标余额桶代码；为空时为空。 */
    static FactTargetBucket parseTargetBucket(String code) {
        return code == null ? null : BillingCodes.parse(FactTargetBucket.class, "目标余额桶", code);
    }

    /** 同来源引用已登记时比较提交内容摘要：相同返回原事实，不同拒绝；未登记返回空。 */
    SubmitBillingFactResponse getOriginalSubmission(BillingFact fact) {
        BillingFact original = billingFactRepository.findBySource(fact.getSourceSystem(), fact.getSourceEventKey());
        if (original == null) {
            return null;
        }
        if (!original.getSourcePayloadHash().equals(fact.getSourcePayloadHash())) {
            throw new BizCheckFailException("来源引用已登记且内容不同: " + fact.getSourceSystem() + "/"
                + fact.getSourceEventKey() + ", 原事实 " + original.getId());
        }
        BillingFactContext context = billingFactService.loadContext(original.getId());
        return new SubmitBillingFactResponse(BillingFactView.of(context, null, List.of()), false);
    }

    /** 新提交的业务验证：币种与精度、目录约束与登记不变量、来源事实与被替代事实。 */
    BillingFactContext buildContext(BillingFact fact, List<BillingFactRef> refs, List<BillingFactLine> lines) {
        CurrencySpec spec = getActiveCurrencySpec(fact);
        checkAmountScales(fact, lines, spec.getMinorUnit());
        BillingFactContext context = new BillingFactContext(fact, refs, lines, null, spec.getMinorUnit());
        BillingFactInvariants.checkManualRegistration(context.toPayload());
        if (fact.getParentFactId() != null) {
            checkParentFact(fact);
        }
        if (fact.getSupersedesFactId() != null) {
            checkSupersededFact(fact);
        }
        return context;
    }

    /** 事实币种在只读副本中为 ACTIVE。 */
    CurrencySpec getActiveCurrencySpec(BillingFact fact) {
        CurrencySpec spec = currencySpecRepository.findByCurrency(fact.getCurrency());
        if (spec == null || spec.getStatus() != CurrencySpecStatus.ACTIVE) {
            throw new BizCheckFailException("计费事实币种未配置或未启用: " + fact.getCurrency() + ", 来源 "
                + fact.getSourceEventKey());
        }
        return spec;
    }

    /** 明细金额的小数位不超过币种精度，不隐式舍入。 */
    void checkAmountScales(BillingFact fact, List<BillingFactLine> lines, int minorUnit) {
        for (BillingFactLine line : lines) {
            if (line.getPostedAmount().stripTrailingZeros().scale() > minorUnit) {
                throw new BizCheckFailException("计费事实明细金额超过币种精度 " + minorUnit + " 位: 行 " + line.getLineNo()
                    + ", 金额 " + line.getPostedAmount().toPlainString() + ", 币种 " + fact.getCurrency());
            }
        }
    }

    /** 成本调整的来源事实是同 OE、同币种的已批准 PAYMENT；Client 取自来源事实，请求给出时必须一致。 */
    void checkParentFact(BillingFact fact) {
        BillingFact parent = billingFactRepository.findById(fact.getParentFactId());
        boolean valid = parent != null && parent.getFactType() == BillingFactType.PAYMENT
            && parent.getStatus() == BillingFactStatus.APPROVED && parent.getEntityNo().equals(fact.getEntityNo())
            && parent.getCurrency().equals(fact.getCurrency())
            && (fact.getClientId() == null || fact.getClientId().equals(parent.getClientId()));
        if (!valid) {
            throw new BizCheckFailException("来源事实不存在、不是已批准的同主体同币种支付事实: " + fact.getParentFactId()
                + ", 来源 " + fact.getSourceEventKey());
        }
        fact.setClientId(parent.getClientId());
    }

    /** 被替代事实是同类型、同 Client、OE、币种的已批准事实。 */
    void checkSupersededFact(BillingFact fact) {
        BillingFact superseded = billingFactRepository.findById(fact.getSupersedesFactId());
        boolean valid = superseded != null && superseded.getFactType() == fact.getFactType()
            && superseded.getStatus() == BillingFactStatus.APPROVED
            && Objects.equals(superseded.getClientId(), fact.getClientId())
            && superseded.getEntityNo().equals(fact.getEntityNo()) && superseded.getCurrency().equals(fact.getCurrency());
        if (!valid) {
            throw new BizCheckFailException("被替代事实不存在、不是已批准的同类型同主体同币种事实: " + fact.getSupersedesFactId()
                + ", 来源 " + fact.getSourceEventKey());
        }
    }

    /** DRAFT 事实头：人工登记、来源版本 1、版本在批准时定稿、业务时间暂为提交时间，总金额为明细金额之和。 */
    BillingFact buildFact(SubmitBillingFactRequest request, Instant now) {
        BillingFact fact = new BillingFact();
        fact.setId(idGenerator.nextId());
        fact.setFactType(parseFactType(request.getFactType()));
        fact.setOriginType(FactOriginType.MANUAL);
        fact.setSourceSystem(request.getSourceSystem());
        fact.setSourceEventKey(request.getSourceReference());
        fact.setSourceVersion(1L);
        fact.setParentFactId(request.getParentFactId());
        fact.setSupersedesFactId(request.getSupersedesFactId());
        fact.setClientId(request.getClientId());
        fact.setEntityNo(request.getEntityNo());
        fact.setLocalPartnerId(request.getLocalPartnerId());
        fact.setFinanceEntityId(request.getFinanceEntityId());
        fact.setCurrency(request.getCurrency());
        fact.setGrossAmount(request.getLines().stream().map(SubmitBillingFactRequest.Line::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        fact.setBusinessTime(now);
        fact.setStatus(BillingFactStatus.DRAFT);
        fact.setFactVersion(0L);
        fact.setMakerId(request.getMakerId());
        fact.setCreateTime(now);
        fact.setUpdateTime(now);
        return fact;
    }

    /** 外部引用按引用类型升序。 */
    List<BillingFactRef> buildRefs(BillingFact fact, SubmitBillingFactRequest request, Instant now) {
        List<BillingFactRef> refs = new ArrayList<>();
        for (SubmitBillingFactRequest.Ref source : request.getRefs()) {
            BillingFactRef ref = new BillingFactRef();
            ref.setId(idGenerator.nextId());
            ref.setFactId(fact.getId());
            ref.setRefType(BillingCodes.parse(FactRefType.class, "引用类型", source.getRefType()));
            ref.setRefId(source.getRefId());
            ref.setCreateTime(now);
            refs.add(ref);
        }
        refs.sort(Comparator.comparing(ref -> ref.getRefType().getCode()));
        return refs;
    }

    /** 明细行按提交顺序编号；基数、原始金额与记账金额相同，费率与来源规则字段为空。 */
    List<BillingFactLine> buildLines(BillingFact fact, SubmitBillingFactRequest request, Instant now) {
        List<BillingFactLine> lines = new ArrayList<>();
        for (SubmitBillingFactRequest.Line source : request.getLines()) {
            BillingFactLine line = new BillingFactLine();
            line.setId(idGenerator.nextId());
            line.setFactId(fact.getId());
            line.setLineNo(lines.size() + 1);
            line.setComponentType(BillingCodes.parse(FactComponentType.class, "组件类型", source.getComponentType()));
            line.setPayerRole(BillingCodes.parse(FactPartyRole.class, "付方角色", source.getPayerRole()));
            line.setPayerId(source.getPayerId());
            line.setPayeeRole(BillingCodes.parse(FactPartyRole.class, "收方角色", source.getPayeeRole()));
            line.setPayeeId(source.getPayeeId());
            line.setBasisAmount(source.getAmount());
            line.setRawAmount(source.getAmount());
            line.setPostedAmount(source.getAmount());
            line.setEffectDirection(BillingCodes.parse(FactLineDirection.class, "影响方向", source.getDirection()));
            line.setTargetBucket(parseTargetBucket(source.getTargetBucket()));
            line.setCreateTime(now);
            lines.add(line);
        }
        return lines;
    }
}
