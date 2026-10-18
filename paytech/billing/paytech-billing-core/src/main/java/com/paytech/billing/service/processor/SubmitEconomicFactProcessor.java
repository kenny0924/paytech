package com.paytech.billing.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.core.constant.BillingLocks;
import com.paytech.billing.core.dal.dataobject.CurrencySpec;
import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.core.dal.dataobject.EconomicFactLine;
import com.paytech.billing.core.enums.BillingCodes;
import com.paytech.billing.domain.EconomicFactContext;
import com.paytech.billing.domain.EconomicFactPayloads;
import com.paytech.billing.domainservice.repository.CurrencySpecRepository;
import com.paytech.billing.domainservice.repository.EconomicFactRepository;
import com.paytech.billing.domainservice.service.EconomicFactService;
import com.paytech.billing.domainservice.validator.fact.EconomicFactInvariants;
import com.paytech.billing.service.processor.request.SubmitEconomicFactRequest;
import com.paytech.billing.service.processor.response.SubmitEconomicFactResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.AllArgsConstructor;

/**
 * Maker 提交人工或成本调整经济事实（DRAFT，design/04 §2）
 * <li>业务唯一身份为 sourceSystem + sourceReference：同内容重复提交返回原事实，不同内容拒绝；与同一来源引用的提交共用资源锁并持续等待，
 * 来源唯一键为最终防线。</li>
 * <li>新提交：币种在只读副本中为 ACTIVE 且金额不超过币种精度；COST_ADJUSTMENT 的来源事实是同 Client / OE / 币种的已批准 PAYMENT；
 * 被替代事实是同类型、同主体、同币种的已批准事实；合作方三元组、平台服务费主体与付方 / 收方主体一致性按
 * {@link EconomicFactInvariants#checkAdjustmentConsistency} 校验。</li>
 * <li>事实头与全部明细行在同一本地事务写入；本入口没有外部调用，也不登记事件（发布任务在批准时登记）。</li>
 */
@Component
@AllArgsConstructor
public class SubmitEconomicFactProcessor
    extends AbstractLockProcessor<SubmitEconomicFactRequest, SubmitEconomicFactResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final EconomicFactService economicFactService;
    private final CurrencySpecRepository currencySpecRepository;
    private final EconomicFactRepository economicFactRepository;

    /** 类型锚点与明细行代码、金额、付方 / 收方只依赖输入，在加锁前校验。 */
    @Override
    protected void validate(SubmitEconomicFactRequest request) {
        EconomicsType factType = parseFactType(request.getFactType());
        checkTypeAnchors(factType, request);
        for (SubmitEconomicFactRequest.Line line : request.getLines()) {
            checkLine(factType, line);
        }
    }

    /** 来源引用资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(SubmitEconomicFactRequest request) {
        return LockCommand.lock(BillingLocks.economicFactSource(request.getSourceSystem(), request.getSourceReference()));
    }

    @Override
    protected SubmitEconomicFactResponse processWithinLock(SubmitEconomicFactRequest request) {
        // 查询原提交
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        EconomicFact fact = buildFact(request, now);
        List<EconomicFactLine> lines = buildLines(fact, request, now);
        fact.setSourcePayloadHash(EconomicFactPayloads.submissionHash(fact, lines));
        SubmitEconomicFactResponse original = getOriginalSubmission(fact);
        if (original != null) {
            return original;
        }

        // 构建上下文
        EconomicFactContext context = buildContext(fact, lines);

        // 保存上下文
        economicFactService.saveDraft(context);

        // 响应处理
        return new SubmitEconomicFactResponse(context.toView(), true);
    }

    /** 只允许阶段 0 的两类人工登记。 */
    EconomicsType parseFactType(String code) {
        EconomicsType factType = BillingCodes.parse(EconomicsType.class, "经济事实类型", code);
        if (factType != EconomicsType.MANUAL_ADJUSTMENT && factType != EconomicsType.COST_ADJUSTMENT) {
            throw new InvalidParameterException("只能登记 MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT: " + code);
        }
        return factType;
    }

    /** COST_ADJUSTMENT 必须给出来源支付事实与渠道清算账户；MANUAL_ADJUSTMENT 不得给出来源事实。 */
    void checkTypeAnchors(EconomicsType factType, SubmitEconomicFactRequest request) {
        if (factType == EconomicsType.COST_ADJUSTMENT
            && (request.getParentFactId() == null || request.getChannelAccountId() == null)) {
            throw new BizCheckFailException("成本调整必须指定来源支付事实与渠道清算账户: 来源 " + request.getSourceReference());
        }
        if (factType == EconomicsType.MANUAL_ADJUSTMENT && request.getParentFactId() != null) {
            throw new BizCheckFailException("人工调整不得指定来源事实: 来源 " + request.getSourceReference());
        }
    }

    /** 费用类型、角色与方向属于目录，金额为正，付方与收方不同；成本调整只登记渠道成本。 */
    void checkLine(EconomicsType factType, SubmitEconomicFactRequest.Line line) {
        AmountSource feeType = BillingCodes.parse(AmountSource.class, "费用类型", line.getFeeTypeCode());
        BillingCodes.parse(FactPartyRole.class, "付方角色", line.getPayerRole());
        BillingCodes.parse(FactPartyRole.class, "收方角色", line.getPayeeRole());
        BillingCodes.parse(FactLineDirection.class, "影响方向", line.getDirection());
        if (feeType == AmountSource.GROSS_AMOUNT) {
            throw new InvalidParameterException("GROSS_AMOUNT 不是明细行费用类型");
        }
        if (factType == EconomicsType.COST_ADJUSTMENT && feeType != AmountSource.CHANNEL_COST) {
            throw new BizCheckFailException("成本调整只能登记 CHANNEL_COST 明细: " + feeType.getCode());
        }
        if (line.getAmount().signum() <= 0) {
            throw new BizCheckFailException("经济事实明细金额必须为正: " + line.getFeeTypeCode() + ", 金额 "
                + line.getAmount().toPlainString());
        }
        if (line.getPayerRole().equals(line.getPayeeRole()) && line.getPayerId().equals(line.getPayeeId())) {
            throw new BizCheckFailException("经济事实明细付方与收方不能相同: " + line.getPayerRole() + "/" + line.getPayerId());
        }
    }

    /** 同来源引用已登记时比较提交内容摘要：相同返回原事实，不同拒绝；未登记返回空。 */
    SubmitEconomicFactResponse getOriginalSubmission(EconomicFact fact) {
        EconomicFact original = economicFactRepository.findBySource(fact.getSourceSystem(), fact.getSourceEventKey());
        if (original == null) {
            return null;
        }
        if (!original.getSourcePayloadHash().equals(fact.getSourcePayloadHash())) {
            throw new BizCheckFailException("来源引用已登记且内容不同: " + fact.getSourceSystem() + "/"
                + fact.getSourceEventKey() + ", 原事实 " + original.getId());
        }
        return new SubmitEconomicFactResponse(economicFactService.loadContext(original.getId()).toView(), false);
    }

    /** 新提交的业务验证：币种与精度、来源事实、被替代事实与登记不变量。 */
    EconomicFactContext buildContext(EconomicFact fact, List<EconomicFactLine> lines) {
        CurrencySpec spec = getActiveCurrencySpec(fact);
        checkAmountScales(fact, lines, spec.getMinorUnit());
        lines.forEach(line -> line.setRoundingMode(spec.getRoundingMode()));
        if (fact.getParentEconomicsId() != null) {
            checkParentFact(fact);
        }
        if (fact.getSupersedesFactId() != null) {
            checkSupersededFact(fact);
        }
        EconomicFactContext context = new EconomicFactContext(fact, lines, null, spec.getMinorUnit());
        EconomicFactInvariants.checkAdjustmentConsistency(context.toPayload());
        return context;
    }

    /** 事实币种在只读副本中为 ACTIVE。 */
    CurrencySpec getActiveCurrencySpec(EconomicFact fact) {
        CurrencySpec spec = currencySpecRepository.findByCurrency(fact.getCurrency());
        if (spec == null || spec.getStatus() != CurrencySpecStatus.ACTIVE) {
            throw new BizCheckFailException("经济事实币种未配置或未启用: " + fact.getCurrency() + ", 来源 "
                + fact.getSourceEventKey());
        }
        return spec;
    }

    /** 明细金额的小数位不超过币种精度，不隐式舍入。 */
    void checkAmountScales(EconomicFact fact, List<EconomicFactLine> lines, int minorUnit) {
        for (EconomicFactLine line : lines) {
            if (line.getPostedAmount().stripTrailingZeros().scale() > minorUnit) {
                throw new BizCheckFailException("经济事实明细金额超过币种精度 " + minorUnit + " 位: 行 " + line.getComponentNo()
                    + ", 金额 " + line.getPostedAmount().toPlainString() + ", 币种 " + fact.getCurrency());
            }
        }
    }

    /** 成本调整的来源事实是同 Client、OE、币种的已批准 PAYMENT。 */
    void checkParentFact(EconomicFact fact) {
        EconomicFact parent = economicFactRepository.findById(fact.getParentEconomicsId());
        boolean valid = parent != null && parent.getFactType() == EconomicsType.PAYMENT
            && parent.getStatus() == EconomicFactStatus.APPROVED && sameScope(parent, fact);
        if (!valid) {
            throw new BizCheckFailException("来源事实不存在、不是已批准的同主体同币种支付事实: " + fact.getParentEconomicsId()
                + ", 来源 " + fact.getSourceEventKey());
        }
    }

    /** 被替代事实是同类型、同 Client、OE、币种的已批准事实。 */
    void checkSupersededFact(EconomicFact fact) {
        EconomicFact superseded = economicFactRepository.findById(fact.getSupersedesFactId());
        boolean valid = superseded != null && superseded.getFactType() == fact.getFactType()
            && superseded.getStatus() == EconomicFactStatus.APPROVED && sameScope(superseded, fact);
        if (!valid) {
            throw new BizCheckFailException("被替代事实不存在、不是已批准的同类型同主体同币种事实: " + fact.getSupersedesFactId()
                + ", 来源 " + fact.getSourceEventKey());
        }
    }

    /** 两条事实属于同一 Client、OE 与币种。 */
    static boolean sameScope(EconomicFact left, EconomicFact right) {
        return Objects.equals(left.getClientId(), right.getClientId()) && left.getEntityNo().equals(right.getEntityNo())
            && left.getCurrency().equals(right.getCurrency());
    }

    /** DRAFT 事实头：来源版本固定为 1，事实版本在批准时定稿，总金额为明细金额之和；交易锚点为空。 */
    EconomicFact buildFact(SubmitEconomicFactRequest request, Instant now) {
        EconomicFact fact = new EconomicFact();
        fact.setId(idGenerator.nextId());
        fact.setFactType(parseFactType(request.getFactType()));
        fact.setSourceSystem(request.getSourceSystem());
        fact.setSourceEventKey(request.getSourceReference());
        fact.setSourceVersion(1L);
        fact.setParentEconomicsId(request.getParentFactId());
        fact.setClientId(request.getClientId());
        fact.setEntityNo(request.getEntityNo());
        fact.setLocalPartnerId(request.getLocalPartnerId());
        fact.setPartnerAgreementOeId(request.getPartnerAgreementOeId());
        fact.setFinanceEntityId(request.getFinanceEntityId());
        fact.setCurrency(request.getCurrency());
        fact.setGrossAmount(request.getLines().stream().map(SubmitEconomicFactRequest.Line::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        fact.setSupersedesFactId(request.getSupersedesFactId());
        fact.setStatus(EconomicFactStatus.DRAFT);
        fact.setFactVersion(0L);
        fact.setChannelAccountId(request.getChannelAccountId());
        fact.setMakerId(request.getMakerId());
        fact.setCreateTime(now);
        fact.setUpdateTime(now);
        return fact;
    }

    /** 明细行按提交顺序编号；归属方等于收方，基数、原始金额与记账金额相同，来源规则字段为空。 */
    List<EconomicFactLine> buildLines(EconomicFact fact, SubmitEconomicFactRequest request, Instant now) {
        List<EconomicFactLine> lines = new ArrayList<>();
        for (SubmitEconomicFactRequest.Line source : request.getLines()) {
            EconomicFactLine line = new EconomicFactLine();
            line.setId(idGenerator.nextId());
            line.setTransactionEconomicsId(fact.getId());
            line.setComponentNo(lines.size() + 1);
            line.setComponentType(source.getFeeTypeCode());
            line.setPayerRole(BillingCodes.parse(FactPartyRole.class, "付方角色", source.getPayerRole()));
            line.setPayerId(source.getPayerId());
            line.setPayeeRole(BillingCodes.parse(FactPartyRole.class, "收方角色", source.getPayeeRole()));
            line.setPayeeId(source.getPayeeId());
            line.setPartyType(line.getPayeeRole());
            line.setPartyId(line.getPayeeId());
            line.setBasisAmount(source.getAmount());
            line.setRawAmount(source.getAmount());
            line.setPostedAmount(source.getAmount());
            line.setEffectDirection(BillingCodes.parse(FactLineDirection.class, "影响方向", source.getDirection()));
            line.setCreateTime(now);
            lines.add(line);
        }
        return lines;
    }
}
