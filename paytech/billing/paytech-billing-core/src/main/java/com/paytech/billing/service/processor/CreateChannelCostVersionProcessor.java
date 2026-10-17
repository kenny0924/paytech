package com.paytech.billing.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.billing.core.constant.BillingLocks;
import com.paytech.billing.core.dal.dataobject.ChannelCostComponent;
import com.paytech.billing.core.dal.dataobject.ChannelCostPlan;
import com.paytech.billing.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.billing.core.dal.dataobject.ChannelCostScope;
import com.paytech.billing.core.dal.dataobject.CurrencySpec;
import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.core.enums.ChannelCostCalculationBase;
import com.paytech.billing.core.enums.ChannelCostComponentType;
import com.paytech.billing.core.enums.ChannelCostVersionStatus;
import com.paytech.billing.domain.ChannelCostVersionContext;
import com.paytech.billing.domain.view.ChannelCostVersionView;
import com.paytech.billing.domainservice.repository.ChannelCostPlanVerRepository;
import com.paytech.billing.domainservice.repository.CurrencySpecRepository;
import com.paytech.billing.domainservice.service.ChannelCostPlanService;
import com.paytech.billing.service.processor.request.CreateChannelCostVersionRequest;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.AllArgsConstructor;

/**
 * 以完整适用范围与成本组成创建 DRAFT 渠道成本版本，版本号为当前最大值 + 1，并冻结内容摘要
 * <li>适用范围非空且每个范围的组成非空；范围序号为正且不重复，六个匹配维度组合在版本内不重复；范围内组成代码不重复。</li>
 * <li>组成：类型、计算基础、舍入方式必须属于 V1 目录（否则参数错误）；费率与固定费至少一项，费率在 [0,1]；金额非负；
 * 固定费与其币种成对出现且币种等于范围币种（本期不换汇）；上下限同时存在时下限不大于上限。</li>
 * <li>范围币种在 t_currency_spec 中为 ACTIVE，固定费与上下限的小数位不超过该币种精度。</li>
 * <li>同一方案已有 DRAFT 或 PENDING_APPROVAL 版本时拒绝；与同一方案的其他写入口共用资源锁并持续等待，事务内锁方案后复验，
 * 版本号由唯一键保护。</li>
 */
@Component
@AllArgsConstructor
public class CreateChannelCostVersionProcessor
    extends AbstractLockProcessor<CreateChannelCostVersionRequest, ChannelCostVersionView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelCostPlanService channelCostPlanService;
    private final CurrencySpecRepository currencySpecRepository;
    private final ChannelCostPlanVerRepository channelCostPlanVerRepository;

    /** 适用范围与组成非空，序号、匹配维度与组成代码不重复，组成参数自洽。 */
    @Override
    protected void validate(CreateChannelCostVersionRequest request) {
        if (request.getScopes().isEmpty()) {
            throw new BizCheckFailException("渠道成本适用范围不能为空: 方案 " + request.getPlanCode());
        }
        Set<Integer> scopeNos = new HashSet<>();
        Set<String> matchKeys = new HashSet<>();
        for (CreateChannelCostVersionRequest.Scope scope : request.getScopes()) {
            checkScopeIdentity(scope, scopeNos, matchKeys);
            checkComponents(scope);
        }
    }

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CreateChannelCostVersionRequest request) {
        return LockCommand.lock(BillingLocks.channelCostPlan(request.getPlanCode()));
    }

    @Override
    protected ChannelCostVersionView processWithinLock(CreateChannelCostVersionRequest request) {
        ChannelCostPlan plan = channelCostPlanService.loadPlan(request.getPlanCode());
        checkNoOpenVersion(plan);
        Map<String, Integer> minorUnits = loadActiveMinorUnits(request);
        checkAmountScales(request, minorUnits);
        ChannelCostVersionContext context = buildContext(plan, request, minorUnits);
        channelCostPlanService.createVersion(context);
        return context.toView();
    }

    /** 范围序号为正且不重复，六个匹配维度组合在版本内不重复。 */
    void checkScopeIdentity(CreateChannelCostVersionRequest.Scope scope, Set<Integer> scopeNos, Set<String> matchKeys) {
        if (scope.getScopeNo() <= 0) {
            throw new BizCheckFailException("适用范围序号必须为正整数: 范围 " + scope.getScopeNo());
        }
        if (!scopeNos.add(scope.getScopeNo())) {
            throw new BizCheckFailException("适用范围序号重复: 范围 " + scope.getScopeNo());
        }
        if (!matchKeys.add(scope.matchKey())) {
            throw new BizCheckFailException("适用范围匹配维度重复: 范围 " + scope.getScopeNo() + ", MID " + scope.getMidId()
                + ", 主体 " + scope.getEntityNo() + ", 国家 " + scope.getCountryCode() + ", 币种 " + scope.getCurrency()
                + ", 支付方式 " + scope.getPaymentMethodCode() + ", 动作 " + scope.getActionType());
        }
    }

    /** 范围内组成非空、组成代码不重复且每个组成参数自洽。 */
    void checkComponents(CreateChannelCostVersionRequest.Scope scope) {
        if (scope.getComponents().isEmpty()) {
            throw new BizCheckFailException("适用范围成本组成不能为空: 范围 " + scope.getScopeNo());
        }
        Set<String> componentCodes = new HashSet<>();
        for (CreateChannelCostVersionRequest.Component component : scope.getComponents()) {
            if (!componentCodes.add(component.getComponentCode())) {
                throw new BizCheckFailException("同一适用范围内成本组成代码重复: 范围 " + scope.getScopeNo() + ", 组成 "
                    + component.getComponentCode());
            }
            parseCodes(component);
            checkPricing(scope, component);
            checkBounds(scope, component);
        }
    }

    /** 类型、计算基础与舍入方式必须属于 V1 目录。 */
    void parseCodes(CreateChannelCostVersionRequest.Component component) {
        parse(ChannelCostComponentType.class, "成本组成类型", component.getComponentType());
        parse(ChannelCostCalculationBase.class, "成本计算基础", component.getCalculationBase());
        parse(AmountRoundingMode.class, "舍入方式", component.getRoundingMode());
    }

    /** 费率与固定费至少一项，费率在 [0,1]，固定费与币种成对且币种等于范围币种。 */
    void checkPricing(CreateChannelCostVersionRequest.Scope scope,
        CreateChannelCostVersionRequest.Component component) {
        String label = label(scope, component);
        if (component.getRateValue() == null && component.getFixedAmount() == null) {
            throw new BizCheckFailException("成本组成费率与固定费至少提供一项: " + label);
        }
        BigDecimal rate = component.getRateValue();
        if (rate != null && (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)) {
            throw new BizCheckFailException("成本组成费率必须在 0 到 1 之间: " + label + ", 费率 " + rate.toPlainString());
        }
        if ((component.getFixedAmount() == null) != (component.getCurrency() == null)) {
            throw new BizCheckFailException("固定费与固定费币种必须同时提供或同时为空: " + label);
        }
        if (component.getCurrency() != null && !component.getCurrency().equals(scope.getCurrency())) {
            throw new BizCheckFailException("固定费币种必须等于适用范围币种: " + label + ", 固定费币种 " + component.getCurrency()
                + ", 范围币种 " + scope.getCurrency());
        }
    }

    /** 固定费与上下限非负，上下限同时存在时下限不大于上限。 */
    void checkBounds(CreateChannelCostVersionRequest.Scope scope, CreateChannelCostVersionRequest.Component component) {
        String label = label(scope, component);
        if (amounts(component).anyMatch(amount -> amount.signum() < 0)) {
            throw new BizCheckFailException("成本组成固定费与上下限不能为负: " + label);
        }
        BigDecimal min = component.getMinAmount();
        BigDecimal max = component.getMaxAmount();
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BizCheckFailException("成本组成下限不能大于上限: " + label + ", 下限 " + min.toPlainString() + ", 上限 "
                + max.toPlainString());
        }
    }

    /** 同一方案不能同时存在 DRAFT 或 PENDING_APPROVAL 版本。 */
    void checkNoOpenVersion(ChannelCostPlan plan) {
        if (channelCostPlanVerRepository.existsOpenVersion(plan.getId())) {
            throw new BizCheckFailException("渠道成本方案已有草稿或待复核版本: " + plan.getPlanCode());
        }
    }

    /** 读取全部范围币种的精度，币种必须在币种规则中为 ACTIVE。 */
    Map<String, Integer> loadActiveMinorUnits(CreateChannelCostVersionRequest request) {
        Map<String, Integer> minorUnits = new HashMap<>();
        for (CreateChannelCostVersionRequest.Scope scope : request.getScopes()) {
            if (minorUnits.containsKey(scope.getCurrency())) {
                continue;
            }
            CurrencySpec spec = currencySpecRepository.findByCurrency(scope.getCurrency());
            if (spec == null || spec.getStatus() != CurrencySpecStatus.ACTIVE) {
                throw new BizCheckFailException("适用范围币种未配置或未启用: 方案 " + request.getPlanCode() + ", 范围 "
                    + scope.getScopeNo() + ", 币种 " + scope.getCurrency());
            }
            minorUnits.put(scope.getCurrency(), spec.getMinorUnit());
        }
        return minorUnits;
    }

    /** 固定费与上下限的小数位不超过范围币种精度。 */
    void checkAmountScales(CreateChannelCostVersionRequest request, Map<String, Integer> minorUnits) {
        for (CreateChannelCostVersionRequest.Scope scope : request.getScopes()) {
            int minorUnit = minorUnits.get(scope.getCurrency());
            for (CreateChannelCostVersionRequest.Component component : scope.getComponents()) {
                if (amounts(component).anyMatch(amount -> amount.stripTrailingZeros().scale() > minorUnit)) {
                    throw new BizCheckFailException("成本组成金额超过币种精度 " + minorUnit + " 位: " + label(scope, component)
                        + ", 币种 " + scope.getCurrency());
                }
            }
        }
    }

    /** 构建新版本、全部适用范围与成本组成，并冻结内容摘要。 */
    ChannelCostVersionContext buildContext(ChannelCostPlan plan, CreateChannelCostVersionRequest request,
        Map<String, Integer> minorUnits) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelCostPlanVer ver = buildVersion(plan, request, now);
        List<ChannelCostScope> scopes = new ArrayList<>();
        List<ChannelCostComponent> components = new ArrayList<>();
        for (CreateChannelCostVersionRequest.Scope source : request.getScopes()) {
            ChannelCostScope scope = buildScope(ver, source, now);
            scopes.add(scope);
            int minorUnit = minorUnits.get(source.getCurrency());
            source.getComponents()
                .forEach(component -> components.add(buildComponent(scope, component, minorUnit, now)));
        }
        ChannelCostVersionContext context = new ChannelCostVersionContext(plan, ver, scopes, components, minorUnits);
        ver.setContentHash(context.computeContentHash());
        return context;
    }

    /** DRAFT 版本，版本号为当前最大值 + 1。 */
    ChannelCostPlanVer buildVersion(ChannelCostPlan plan, CreateChannelCostVersionRequest request, Instant now) {
        ChannelCostPlanVer ver = new ChannelCostPlanVer();
        ver.setId(idGenerator.nextId());
        ver.setPlanId(plan.getId());
        ver.setCostVer(channelCostPlanVerRepository.nextCostVer(plan.getId()));
        ver.setEffectiveFrom(request.getEffectiveFrom().truncatedTo(ChronoUnit.MICROS));
        ver.setStatus(ChannelCostVersionStatus.DRAFT);
        ver.setMakerId(request.getMakerId());
        ver.setCreateTime(now);
        ver.setUpdateTime(now);
        return ver;
    }

    /** 构建适用范围。 */
    ChannelCostScope buildScope(ChannelCostPlanVer ver, CreateChannelCostVersionRequest.Scope source, Instant now) {
        ChannelCostScope scope = new ChannelCostScope();
        scope.setId(idGenerator.nextId());
        scope.setCostVerId(ver.getId());
        scope.setScopeNo(source.getScopeNo());
        scope.setMidId(source.getMidId());
        scope.setEntityNo(source.getEntityNo());
        scope.setChannelAgreementRef(source.getChannelAgreementRef());
        scope.setCountryCode(source.getCountryCode());
        scope.setCurrency(source.getCurrency());
        scope.setPaymentMethodCode(source.getPaymentMethodCode());
        scope.setActionType(source.getActionType());
        scope.setCreateTime(now);
        return scope;
    }

    /** 构建成本组成，金额按范围币种精度补齐。 */
    ChannelCostComponent buildComponent(ChannelCostScope scope, CreateChannelCostVersionRequest.Component source,
        int minorUnit, Instant now) {
        ChannelCostComponent component = new ChannelCostComponent();
        component.setId(idGenerator.nextId());
        component.setScopeId(scope.getId());
        component.setComponentCode(source.getComponentCode());
        component.setComponentType(parse(ChannelCostComponentType.class, "成本组成类型", source.getComponentType()));
        component.setCalculationBase(parse(ChannelCostCalculationBase.class, "成本计算基础", source.getCalculationBase()));
        component.setRateValue(source.getRateValue());
        component.setFixedAmount(scale(source.getFixedAmount(), minorUnit));
        component.setCurrency(source.getCurrency());
        component.setMinAmount(scale(source.getMinAmount(), minorUnit));
        component.setMaxAmount(scale(source.getMaxAmount(), minorUnit));
        component.setRoundingMode(parse(AmountRoundingMode.class, "舍入方式", source.getRoundingMode()));
        component.setCreateTime(now);
        return component;
    }

    /** 组成中已提供的固定费与上下限。 */
    static Stream<BigDecimal> amounts(CreateChannelCostVersionRequest.Component component) {
        return Stream.of(component.getFixedAmount(), component.getMinAmount(), component.getMaxAmount())
            .filter(Objects::nonNull);
    }

    /** 金额按币种精度补齐；调用方已保证没有超精度，为空时保持为空。 */
    static BigDecimal scale(BigDecimal value, int minorUnit) {
        return value == null ? null : value.setScale(minorUnit);
    }

    /** 错误消息中的范围与组成定位。 */
    static String label(CreateChannelCostVersionRequest.Scope scope,
        CreateChannelCostVersionRequest.Component component) {
        return "范围 " + scope.getScopeNo() + ", 组成 " + component.getComponentCode();
    }

    /** 按稳定 Code 解析 V1 目录枚举，未知代码以参数错误拒绝。 */
    static <E extends Enum<E> & CodeEnum> E parse(Class<E> type, String field, String code) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(code)) {
                return constant;
            }
        }
        throw new InvalidParameterException(field + "代码不在目录内: " + code);
    }
}
