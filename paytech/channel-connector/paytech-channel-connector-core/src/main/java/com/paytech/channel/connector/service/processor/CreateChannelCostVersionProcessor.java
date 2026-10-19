package com.paytech.channel.connector.service.processor;

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

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostComponent;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostScope;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.enums.AmountRoundingMode;
import com.paytech.channel.connector.core.enums.ChannelCostCalculationBase;
import com.paytech.channel.connector.core.enums.ChannelCostCodes;
import com.paytech.channel.connector.core.enums.ChannelCostComponentType;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.channel.connector.domain.ChannelCostVersionContext;
import com.paytech.channel.connector.domain.ChannelCostVersionView;
import com.paytech.channel.connector.domainservice.repository.ChannelCostPlanVerRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.service.ChannelCostPlanService;
import com.paytech.channel.connector.service.processor.request.CreateChannelCostVersionRequest;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 以完整适用范围与成本组成创建 DRAFT 渠道成本版本，版本号为当前最大值 + 1，并冻结内容摘要
 * <li>适用范围非空且每个范围的组成非空；范围序号为正且不重复，六个匹配维度组合在版本内不重复；范围内组成代码不重复。</li>
 * <li>组成：类型、计算基础、舍入方式必须属于 V1 目录（否则参数错误）；费率与固定费至少一项，费率在 [0,1]；金额非负；
 * 固定费与其币种成对出现且币种等于范围币种（本期不换汇）；上下限同时存在时下限不大于上限。</li>
 * <li>每个范围的 MID 必须是本库 t_channel_mid 中存在的记录，其 Channel 等于方案 Channel、OE 等于范围 entityNo。</li>
 * <li>币种只由入口校验格式，金额小数位不超过 8 位由入口格式保证；本库无币种精度表，按币种精度归整由 billing 计算时完成。</li>
 * <li>同一方案已有 DRAFT 或 PENDING_APPROVAL 版本时拒绝；与同一方案的其他写入口共用资源锁并持续等待，事务内锁方案后复验，
 * 版本号由唯一键保护。</li>
 */
@Component
@AllArgsConstructor
public class CreateChannelCostVersionProcessor
    extends AbstractLockProcessor<CreateChannelCostVersionRequest, ChannelCostVersionView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidRepository channelMidRepository;
    private final ChannelCostPlanService channelCostPlanService;
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
        return LockCommand.lock(ChannelConnectorLocks.channelCostPlan(request.getPlanCode()));
    }

    @Override
    protected ChannelCostVersionView processWithinLock(CreateChannelCostVersionRequest request) {
        // 业务验证
        ChannelCostPlan plan = channelCostPlanService.loadPlan(request.getPlanCode());
        checkNoOpenVersion(plan);
        checkScopeMids(plan, request);

        // 构建上下文并保存
        ChannelCostVersionContext context = buildContext(plan, request);
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

    /** 每个范围的 MID 在本库存在、归属方案 Channel，且其 OE 等于范围 entityNo；同一 MID 只读取一次。 */
    void checkScopeMids(ChannelCostPlan plan, CreateChannelCostVersionRequest request) {
        Map<Long, ChannelMid> mids = new HashMap<>();
        for (CreateChannelCostVersionRequest.Scope scope : request.getScopes()) {
            ChannelMid mid = mids.computeIfAbsent(scope.getMidId(), channelMidRepository::findById);
            String label = "方案 " + plan.getPlanCode() + ", 范围 " + scope.getScopeNo() + ", MID " + scope.getMidId();
            if (mid == null) {
                throw new BizCheckFailException("适用范围 MID 不存在: " + label);
            }
            if (!mid.getChannelId().equals(plan.getChannelId())) {
                throw new BizCheckFailException("适用范围 MID 不属于方案 Channel: " + label + ", MID Channel "
                    + mid.getChannelId() + ", 方案 Channel " + plan.getChannelId());
            }
            if (!mid.getEntityNo().equals(scope.getEntityNo())) {
                throw new BizCheckFailException("适用范围 MID 的 Operating Entity 与范围不一致: " + label + ", MID 主体 "
                    + mid.getEntityNo() + ", 范围主体 " + scope.getEntityNo());
            }
        }
    }

    /** 构建新版本、全部适用范围与成本组成，并冻结内容摘要。 */
    ChannelCostVersionContext buildContext(ChannelCostPlan plan, CreateChannelCostVersionRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelCostPlanVer ver = buildVersion(plan, request, now);
        List<ChannelCostScope> scopes = new ArrayList<>();
        List<ChannelCostComponent> components = new ArrayList<>();
        for (CreateChannelCostVersionRequest.Scope source : request.getScopes()) {
            ChannelCostScope scope = buildScope(ver, source, now);
            scopes.add(scope);
            source.getComponents().forEach(component -> components.add(buildComponent(scope, component, now)));
        }
        ChannelCostVersionContext context = new ChannelCostVersionContext(plan, ver, scopes, components);
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

    /** 构建成本组成，金额按请求原值保存。 */
    ChannelCostComponent buildComponent(ChannelCostScope scope, CreateChannelCostVersionRequest.Component source,
        Instant now) {
        ChannelCostComponent component = new ChannelCostComponent();
        component.setId(idGenerator.nextId());
        component.setScopeId(scope.getId());
        component.setComponentCode(source.getComponentCode());
        component.setComponentType(parse(ChannelCostComponentType.class, "成本组成类型", source.getComponentType()));
        component.setCalculationBase(parse(ChannelCostCalculationBase.class, "成本计算基础", source.getCalculationBase()));
        component.setRateValue(source.getRateValue());
        component.setFixedAmount(source.getFixedAmount());
        component.setCurrency(source.getCurrency());
        component.setMinAmount(source.getMinAmount());
        component.setMaxAmount(source.getMaxAmount());
        component.setRoundingMode(parse(AmountRoundingMode.class, "舍入方式", source.getRoundingMode()));
        component.setCreateTime(now);
        return component;
    }

    /** 组成中已提供的固定费与上下限。 */
    static Stream<BigDecimal> amounts(CreateChannelCostVersionRequest.Component component) {
        return Stream.of(component.getFixedAmount(), component.getMinAmount(), component.getMaxAmount())
            .filter(Objects::nonNull);
    }

    /** 错误消息中的范围与组成定位。 */
    static String label(CreateChannelCostVersionRequest.Scope scope,
        CreateChannelCostVersionRequest.Component component) {
        return "范围 " + scope.getScopeNo() + ", 组成 " + component.getComponentCode();
    }

    /** 按稳定 Code 解析 V1 目录枚举，未知代码以参数错误拒绝。 */
    static <E extends Enum<E> & CodeEnum> E parse(Class<E> type, String field, String code) {
        return ChannelCostCodes.parse(type, field, code);
    }
}
